package com.embabel.insurance.xiaoming.health;

import com.embabel.insurance.xiaoming.config.XiaomingHealthProperties;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class HealthExtractionService {

    private static final String PRODUCT_ID = "36743";
    private static final String EXTRACTION_VERSION = "health-extraction-v1";
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;

    private final KnowledgeSnapshotStore snapshotStore;
    private final XiaomingHealthProperties properties;
    private final HealthFactExtractor factExtractor;
    private final OcrAdapter ocrAdapter;
    private final Path temporaryRoot;

    @Autowired
    public HealthExtractionService(
            KnowledgeSnapshotStore snapshotStore,
            XiaomingHealthProperties properties,
            HealthFactExtractor factExtractor,
            OcrAdapter ocrAdapter
    ) {
        this(snapshotStore, properties, factExtractor, ocrAdapter,
                Path.of(System.getProperty("java.io.tmpdir")));
    }

    HealthExtractionService(
            KnowledgeSnapshotStore snapshotStore,
            XiaomingHealthProperties properties,
            HealthFactExtractor factExtractor,
            OcrAdapter ocrAdapter,
            Path temporaryRoot
    ) {
        this.snapshotStore = snapshotStore;
        this.properties = properties;
        this.factExtractor = factExtractor;
        this.ocrAdapter = ocrAdapter;
        this.temporaryRoot = temporaryRoot;
    }

    public HealthExtractionResult extract(HealthExtractCommand command) {
        String currentVersion = currentVersion();
        validateCommand(command, currentVersion);
        List<HealthFactDraft> facts = new ArrayList<>();
        List<HealthFileResult> fileResults = new ArrayList<>();

        if (command.text() != null && !command.text().isBlank()) {
            List<HealthFactDraft> textFacts = factExtractor.extract(List.of(
                    new HealthFactExtractor.SourceText("manual-text", "手工文字", null,
                            command.text().trim(), 0.95)
            ));
            facts.addAll(textFacts);
            fileResults.add(new HealthFileResult(
                    "manual-text", "手工文字", HealthFileStatus.SUCCESS,
                    "TEXT_EXTRACTED", "手工文字已处理，请逐项核对事实。", textFacts.size()
            ));
        }

        if (!command.files().isEmpty()) {
            Path requestDirectory = createRequestDirectory(currentVersion);
            try {
                for (int index = 0; index < command.files().size(); index++) {
                    MultipartFile file = command.files().get(index);
                    String fileId = "health-file-" + UUID.randomUUID();
                    String label = "文件 " + (index + 1);
                    try {
                        List<HealthFactDraft> fileFacts = processFileWithTimeout(
                                requestDirectory, file, fileId, label, currentVersion
                        );
                        facts.addAll(fileFacts);
                        fileResults.add(new HealthFileResult(
                                fileId, label, HealthFileStatus.SUCCESS,
                                "FILE_EXTRACTED", "文件已处理，请逐项核对事实。", fileFacts.size()
                        ));
                    } catch (HealthServiceException exception) {
                        fileResults.add(new HealthFileResult(
                                fileId, label, HealthFileStatus.FAILED,
                                exception.reason().name(), exception.getMessage(), 0
                        ));
                    }
                }
            } finally {
                deleteRecursively(requestDirectory);
            }
        }

        return new HealthExtractionResult(
                EXTRACTION_VERSION + "@" + currentVersion,
                currentVersion,
                facts,
                fileResults,
                "事实草稿不会自动匹配。请逐项修改、删除或确认；原始文件和中间内容已清理。"
        );
    }

    private List<HealthFactDraft> processFileWithTimeout(
            Path requestDirectory,
            MultipartFile multipart,
            String sourceId,
            String label,
            String currentVersion
    ) {
        FutureTask<List<HealthFactDraft>> task = new FutureTask<>(
                () -> processFile(requestDirectory, multipart, sourceId, label, currentVersion)
        );
        Thread worker = Thread.ofVirtual().name("xiaoming-health-file-").start(task);
        try {
            return task.get(properties.getProcessingTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            worker.interrupt();
            throw new HealthServiceException(
                    HealthServiceException.Reason.PROCESSING_TIMEOUT,
                    "文件处理超过 30 秒，已停止；请压缩文件或改用手工文字输入。",
                    currentVersion,
                    exception
            );
        } catch (InterruptedException exception) {
            task.cancel(true);
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new HealthServiceException(
                    HealthServiceException.Reason.PROCESSING_TIMEOUT,
                    "文件处理已中断，请改用手工文字输入或稍后重试。",
                    currentVersion,
                    exception
            );
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof HealthServiceException healthException) {
                throw healthException;
            }
            throw new HealthServiceException(
                    HealthServiceException.Reason.FILE_UNSUPPORTED,
                    "文件处理失败，请改用手工文字输入。",
                    currentVersion,
                    exception.getCause()
            );
        }
    }

    private List<HealthFactDraft> processFile(
            Path requestDirectory,
            MultipartFile multipart,
            String sourceId,
            String label,
            String currentVersion
    ) {
        if (multipart == null || multipart.isEmpty() || multipart.getSize() <= 0) {
            throw unsupported("文件为空，请重新选择。", currentVersion);
        }
        if (multipart.getSize() > properties.getMaxFileBytes()) {
            throw unsupported("单文件不能超过 10 MB。", currentVersion);
        }
        String declaredType = multipart.getContentType() == null
                ? ""
                : multipart.getContentType().toLowerCase(Locale.ROOT);
        String extension = extensionFor(declaredType);
        Path stored = requestDirectory.resolve(UUID.randomUUID() + extension).normalize();
        if (!stored.startsWith(requestDirectory)) {
            throw unsupported("文件路径无效。", currentVersion);
        }
        try {
            Files.copy(multipart.getInputStream(), stored, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(stored) > properties.getMaxFileBytes()) {
                throw unsupported("单文件不能超过 10 MB。", currentVersion);
            }
            FileKind kind = validateMagic(stored, declaredType, currentVersion);
            List<HealthFactExtractor.SourceText> sources = switch (kind) {
                case TEXT -> List.of(new HealthFactExtractor.SourceText(
                        sourceId, label, null, decodeUtf8(stored, currentVersion), 0.95
                ));
                case PDF -> extractPdf(stored, sourceId, label, currentVersion);
                case JPEG, PNG -> List.of(new HealthFactExtractor.SourceText(
                        sourceId, label, null, extractImage(stored, declaredType, currentVersion), 0.75
                ));
            };
            return factExtractor.extract(sources);
        } catch (IOException exception) {
            throw new HealthServiceException(
                    HealthServiceException.Reason.FILE_UNSUPPORTED,
                    "文件读取失败，请重新选择。",
                    currentVersion,
                    exception
            );
        } finally {
            try {
                Files.deleteIfExists(stored);
            } catch (IOException ignored) {
                stored.toFile().deleteOnExit();
            }
        }
    }

    private List<HealthFactExtractor.SourceText> extractPdf(
            Path file,
            String sourceId,
            String label,
            String currentVersion
    ) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            if (document.isEncrypted()) throw unsupported("不支持加密 PDF。", currentVersion);
            if (document.getNumberOfPages() > properties.getMaxPdfPages()) {
                throw unsupported("PDF 不能超过 30 页。", currentVersion);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<HealthFactExtractor.SourceText> sources = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (!text.isBlank()) {
                    sources.add(new HealthFactExtractor.SourceText(sourceId, label, page, text, 0.9));
                }
            }
            if (sources.isEmpty()) throw unsupported("PDF 没有可解析文字，请改用手工文字输入。", currentVersion);
            return sources;
        } catch (HealthServiceException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new HealthServiceException(
                    HealthServiceException.Reason.FILE_UNSUPPORTED,
                    "PDF 已损坏或无法解析。",
                    currentVersion,
                    exception
            );
        }
    }

    private String extractImage(Path file, String mediaType, String currentVersion) {
        validateImageDimensions(file, currentVersion);
        try {
            String text = ocrAdapter.extract(file, mediaType);
            if (text == null || text.isBlank()) {
                throw unsupported("OCR 未识别到可核对文字，请改用手工输入。", currentVersion);
            }
            return text;
        } catch (HealthServiceException exception) {
            throw new HealthServiceException(exception.reason(), exception.getMessage(), currentVersion, exception);
        }
    }

    private void validateImageDimensions(Path file, String currentVersion) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) throw unsupported("图片已损坏或无法读取。", currentVersion);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw unsupported("图片已损坏或无法读取。", currentVersion);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = Math.multiplyExact((long) reader.getWidth(0), (long) reader.getHeight(0));
                if (pixels > MAX_IMAGE_PIXELS) throw unsupported("图片像素过大，请压缩后重试。", currentVersion);
            } finally {
                reader.dispose();
            }
        } catch (ArithmeticException | IOException exception) {
            throw new HealthServiceException(
                    HealthServiceException.Reason.FILE_UNSUPPORTED,
                    "图片已损坏、尺寸异常或无法读取。",
                    currentVersion,
                    exception
            );
        }
    }

    private FileKind validateMagic(Path file, String declaredType, String currentVersion) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0) throw unsupported("文件为空，请重新选择。", currentVersion);
        FileKind actual;
        if (startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'})) actual = FileKind.PDF;
        else if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) actual = FileKind.PNG;
        else if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) actual = FileKind.JPEG;
        else if (!containsNull(bytes)) actual = FileKind.TEXT;
        else throw unsupported("仅支持文字、可解析 PDF、JPG 或 PNG。", currentVersion);

        boolean matches = switch (actual) {
            case PDF -> "application/pdf".equals(declaredType);
            case PNG -> "image/png".equals(declaredType);
            case JPEG -> "image/jpeg".equals(declaredType) || "image/jpg".equals(declaredType);
            case TEXT -> "text/plain".equals(declaredType);
        };
        if (!matches) throw unsupported("文件声明类型与实际内容不一致。", currentVersion);
        return actual;
    }

    private String decodeUtf8(Path file, String currentVersion) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > properties.getMaxTextCharacters() * 4L) {
            throw unsupported("文字内容过长，请拆分后重试。", currentVersion);
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (value.length() > properties.getMaxTextCharacters()) {
                throw unsupported("文字内容不能超过 20000 个字符。", currentVersion);
            }
            return value;
        } catch (CharacterCodingException exception) {
            throw unsupported("文字文件必须使用 UTF-8 编码。", currentVersion);
        }
    }

    private String currentVersion() {
        return snapshotStore.current()
                .map(snapshot -> snapshot.knowledgeVersion().versionId())
                .orElseThrow(() -> new HealthServiceException(
                        HealthServiceException.Reason.SNAPSHOT_UNAVAILABLE,
                        "当前没有可用的已发布健康告知版本。",
                        ""
                ));
    }

    private void validateCommand(HealthExtractCommand command, String currentVersion) {
        if (!PRODUCT_ID.equals(command.productId())) {
            throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                    "当前仅支持产品 36743。", currentVersion);
        }
        if (!command.authorized()) {
            throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                    "未确认授权，不会处理健康文字或文件。", currentVersion);
        }
        if (!currentVersion.equals(command.knowledgeVersion())) {
            throw new HealthServiceException(HealthServiceException.Reason.VERSION_CONFLICT,
                    "健康告知资料版本已更新，请按最新版本重新核对。", currentVersion);
        }
        if ((command.text() == null || command.text().isBlank()) && command.files().isEmpty()) {
            throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                    "请提供手工文字或文件。", currentVersion);
        }
        if (command.text() != null && command.text().length() > properties.getMaxTextCharacters()) {
            throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                    "文字内容不能超过 20000 个字符。", currentVersion);
        }
        if (command.files().size() > properties.getMaxFiles()) {
            throw new HealthServiceException(HealthServiceException.Reason.VALIDATION,
                    "一次最多处理 5 个文件。", currentVersion);
        }
    }

    private Path createRequestDirectory(String currentVersion) {
        try {
            Files.createDirectories(temporaryRoot);
            return Files.createTempDirectory(temporaryRoot, "xiaoming-health-").toRealPath();
        } catch (IOException exception) {
            throw new HealthServiceException(
                    HealthServiceException.Reason.FILE_UNSUPPORTED,
                    "无法创建隔离处理目录，请稍后重试。",
                    currentVersion,
                    exception
            );
        }
    }

    private void deleteRecursively(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    path.toFile().deleteOnExit();
                }
            });
        } catch (IOException ignored) {
            directory.toFile().deleteOnExit();
        }
    }

    private String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "text/plain" -> ".txt";
            default -> ".bin";
        };
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private boolean containsNull(byte[] value) {
        int limit = Math.min(value.length, 4096);
        for (int index = 0; index < limit; index++) {
            if (value[index] == 0) return true;
        }
        return false;
    }

    private HealthServiceException unsupported(String message, String currentVersion) {
        return new HealthServiceException(HealthServiceException.Reason.FILE_UNSUPPORTED, message, currentVersion);
    }

    private enum FileKind { TEXT, PDF, JPEG, PNG }
}
