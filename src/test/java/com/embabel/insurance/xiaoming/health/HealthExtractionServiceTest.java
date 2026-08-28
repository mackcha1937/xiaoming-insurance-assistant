package com.embabel.insurance.xiaoming.health;

import com.embabel.insurance.xiaoming.config.XiaomingHealthProperties;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.importer.ImportedKnowledgeBundle;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthExtractionServiceTest {

    @TempDir
    Path tempRoot;

    private XiaomingHealthProperties properties;
    private KnowledgeSnapshotStore store;

    @BeforeEach
    void setUp() {
        properties = new XiaomingHealthProperties();
        store = new KnowledgeSnapshotStore();
        publish();
    }

    @Test
    void extractsManualTextWithoutCreatingPersistentFiles() throws Exception {
        HealthExtractionResult result = service((path, type) -> "").extract(command(
                "去年因高血压住院，当前服药。", List.of()
        ));

        assertTrue(result.facts().stream().anyMatch(fact -> fact.code().equals("HYPERTENSION")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.code().equals("HOSPITALIZATION")));
        assertTrue(result.facts().stream().noneMatch(HealthFactDraft::confirmed));
        assertDirectoryEmpty();
    }

    @Test
    void parsesTextPdfAndUsesServerSideSourceLabels() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile(
                "files", "../../sensitive-name.pdf", "application/pdf", pdf("BMI 31")
        );
        HealthExtractionResult result = service((path, type) -> "").extract(command(null, List.of(pdf)));

        assertEquals(HealthFileStatus.SUCCESS, result.files().getFirst().status());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.code().equals("BMI")));
        assertTrue(result.facts().stream().allMatch(fact -> fact.sourceLocation().sourceLabel().equals("文件 1")));
        assertFalse(result.toString().contains("sensitive-name"));
        assertDirectoryEmpty();
    }

    @Test
    void acceptsPngThroughReplaceableOcrAdapter() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "files", "scan.png", "image/png", png()
        );
        HealthExtractionResult result = service((path, type) -> "否认高血压，无住院史。")
                .extract(command(null, List.of(image)));

        assertEquals(HealthFileStatus.SUCCESS, result.files().getFirst().status());
        assertTrue(result.facts().stream().allMatch(fact -> fact.polarity() == HealthFactPolarity.ABSENT));
        assertDirectoryEmpty();
    }

    @Test
    void ocrFailureDoesNotGuessAndAlwaysCleansTemporaryFiles() throws Exception {
        MockMultipartFile image = new MockMultipartFile("files", "scan.png", "image/png", png());
        HealthExtractionResult result = service((path, type) -> {
            throw new HealthServiceException(HealthServiceException.Reason.FILE_UNSUPPORTED,
                    "OCR 失败", "");
        }).extract(command(null, List.of(image)));

        assertTrue(result.facts().isEmpty());
        assertEquals(HealthFileStatus.FAILED, result.files().getFirst().status());
        assertTrue(result.files().getFirst().message().contains("OCR"));
        assertDirectoryEmpty();
    }

    @Test
    void rejectsMimeMagicMismatchAndContinuesOtherFiles() throws Exception {
        MockMultipartFile bad = new MockMultipartFile(
                "files", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile good = new MockMultipartFile(
                "files", "facts.txt", "text/plain", "否认糖尿病".getBytes(StandardCharsets.UTF_8)
        );
        HealthExtractionResult result = service((path, type) -> "").extract(command(null, List.of(bad, good)));

        assertEquals(HealthFileStatus.FAILED, result.files().get(0).status());
        assertEquals(HealthFileStatus.SUCCESS, result.files().get(1).status());
        assertTrue(result.facts().stream().anyMatch(fact -> fact.code().equals("DIABETES")));
        assertDirectoryEmpty();
    }

    @Test
    void rejectsMissingAuthorizationStaleVersionAndTooManyFiles() {
        HealthExtractCommand unauthorized = new HealthExtractCommand(
                "36743", "conversation-health-0001", "knowledge-health-v1", false, "高血压", List.of()
        );
        HealthExtractCommand stale = new HealthExtractCommand(
                "36743", "conversation-health-0001", "old", true, "高血压", List.of()
        );
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", new byte[]{'a'});
        HealthExtractCommand tooMany = new HealthExtractCommand(
                "36743", "conversation-health-0001", "knowledge-health-v1", true, null,
                List.of(file, file, file, file, file, file)
        );

        assertThrows(HealthServiceException.class, () -> service((path, type) -> "").extract(unauthorized));
        assertEquals(HealthServiceException.Reason.VERSION_CONFLICT,
                assertThrows(HealthServiceException.class, () -> service((path, type) -> "").extract(stale)).reason());
        assertThrows(HealthServiceException.class, () -> service((path, type) -> "").extract(tooMany));
    }

    private HealthExtractionService service(OcrAdapter adapter) {
        return new HealthExtractionService(
                store, properties, new HealthFactExtractor(), adapter, tempRoot
        );
    }

    private HealthExtractCommand command(String text, List<? extends MultipartFile> files) {
        return new HealthExtractCommand(
                "36743", "conversation-health-0001", "knowledge-health-v1", true,
                text, new ArrayList<>(files)
        );
    }

    private byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) image.setRGB(x, y, Color.WHITE.getRGB());
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private void assertDirectoryEmpty() throws Exception {
        try (var entries = Files.list(tempRoot)) {
            assertEquals(0, entries.count());
        }
    }

    private void publish() {
        KnowledgeVersion version = new KnowledgeVersion(
                "knowledge-health-v1", "36743", KnowledgeVersionStatus.PUBLISHED,
                "rules-v1", "occupation-v1", Instant.parse("2026-08-28T00:00:00Z"), List.of()
        );
        store.replace(version, new ImportedKnowledgeBundle(version, List.of(), List.of(), List.of(), List.of()));
    }
}
