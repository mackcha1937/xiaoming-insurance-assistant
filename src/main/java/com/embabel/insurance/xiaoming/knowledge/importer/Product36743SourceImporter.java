package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.DocumentSection;
import com.embabel.insurance.xiaoming.knowledge.DocumentStatus;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.PageMapping;
import com.embabel.insurance.xiaoming.knowledge.SemanticChunker;
import com.embabel.insurance.xiaoming.knowledge.SourceDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Product36743SourceImporter {

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final int OCCUPATIONS_PER_SECTION = 25;

    private final SemanticChunker chunker;
    private final SpreadsheetSourceParser spreadsheetParser;
    private final Product36743RuleFactory ruleFactory;
    private final ObjectMapper objectMapper;

    public Product36743SourceImporter(SemanticChunker chunker) {
        this(chunker, new SpreadsheetSourceParser(), new Product36743RuleFactory(), new ObjectMapper());
    }

    Product36743SourceImporter(
            SemanticChunker chunker,
            SpreadsheetSourceParser spreadsheetParser,
            Product36743RuleFactory ruleFactory,
            ObjectMapper objectMapper
    ) {
        this.chunker = chunker;
        this.spreadsheetParser = spreadsheetParser;
        this.ruleFactory = ruleFactory;
        this.objectMapper = objectMapper;
    }

    public ImportedKnowledgeBundle importFrom(Path sourceDirectory, SpreadsheetScanMode spreadsheetScanMode) {
        Path safeRoot = validateRoot(sourceDirectory);
        List<SourceDocument> documents = new ArrayList<>();
        List<ClauseChunk> chunks = new ArrayList<>();
        List<OccupationEntry> occupations = new ArrayList<>();
        List<SpreadsheetSummary> spreadsheets = new ArrayList<>();

        for (SourceMaterialSpec spec : Product36743SourceCatalog.sources()) {
            Path source = resolveSource(safeRoot, spec);
            verifyHash(source, spec);
            ParsedMaterial material = parse(source, spec, spreadsheetScanMode);
            SourceDocument document = new SourceDocument(
                    spec.documentId(),
                    Product36743SourceCatalog.PRODUCT_ID,
                    Product36743SourceCatalog.KNOWLEDGE_VERSION,
                    spec.fileName(),
                    spec.documentType(),
                    DocumentStatus.DRAFT,
                    spec.sha256(),
                    spec.effectiveAt(),
                    spec.priority(),
                    material.pageMappings()
            );
            documents.add(document);
            chunks.addAll(chunker.chunk(document, material.sections()));
            occupations.addAll(material.occupations());
            if (material.spreadsheet() != null) {
                spreadsheets.add(material.spreadsheet());
            }
        }

        validateOccupationDataset(occupations);
        KnowledgeVersion version = new KnowledgeVersion(
                Product36743SourceCatalog.KNOWLEDGE_VERSION,
                Product36743SourceCatalog.PRODUCT_ID,
                KnowledgeVersionStatus.DRAFT,
                Product36743SourceCatalog.RULES_VERSION,
                Product36743SourceCatalog.OCCUPATION_VERSION,
                null,
                documents
        );
        return new ImportedKnowledgeBundle(
                version,
                chunks,
                ruleFactory.create(chunks),
                occupations,
                spreadsheets
        );
    }

    private ParsedMaterial parse(
            Path source,
            SourceMaterialSpec spec,
            SpreadsheetScanMode spreadsheetScanMode
    ) {
        String lowerName = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            return parsePdf(source);
        }
        if (lowerName.endsWith(".txt")) {
            return parseText(source);
        }
        if (lowerName.endsWith(".json")) {
            return parseOccupations(source);
        }
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            SpreadsheetSummary summary = spreadsheetScanMode == SpreadsheetScanMode.FULL_CELL_SCAN
                    ? spreadsheetParser.parse(source)
                    : spreadsheetParser.inspectStructure(source);
            return parseSpreadsheetSummary(summary);
        }
        throw new SourceImportException("unsupported source format: " + spec.fileName());
    }

    private ParsedMaterial parsePdf(Path source) {
        try (PDDocument document = Loader.loadPDF(source.toFile())) {
            if (document.isEncrypted()) {
                throw new SourceImportException("encrypted PDF is not allowed: " + source.getFileName());
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<DocumentSection> sections = new ArrayList<>();
            List<PageMapping> pageMappings = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = clean(stripper.getText(document));
                if (text.isBlank()) {
                    throw new SourceImportException(
                            "PDF page has no extractable text: " + source.getFileName() + " page " + page
                    );
                }
                pageMappings.add(new PageMapping(page, null));
                sections.add(new DocumentSection(
                        detectSection(text, page),
                        page,
                        null,
                        text,
                        tagsFor(text)
                ));
            }
            return new ParsedMaterial(sections, pageMappings, List.of(), null);
        } catch (IOException exception) {
            throw new SourceImportException("failed to parse PDF: " + source.getFileName(), exception);
        }
    }

    private ParsedMaterial parseText(Path source) {
        try {
            String text = clean(Files.readString(source, StandardCharsets.UTF_8));
            if (text.isBlank()) {
                throw new SourceImportException("text source is empty: " + source.getFileName());
            }
            List<DocumentSection> sections = new ArrayList<>();
            String[] paragraphs = text.split("\\n\\s*\\n");
            int index = 1;
            for (String paragraph : paragraphs) {
                String cleaned = clean(paragraph);
                if (!cleaned.isBlank()) {
                    sections.add(new DocumentSection(
                            detectTextSection(cleaned, index++), 1, null, cleaned, tagsFor(cleaned)
                    ));
                }
            }
            return new ParsedMaterial(sections, List.of(new PageMapping(1, null)), List.of(), null);
        } catch (IOException exception) {
            throw new SourceImportException("failed to parse text source: " + source.getFileName(), exception);
        }
    }

    private ParsedMaterial parseOccupations(Path source) {
        try {
            JsonNode root = objectMapper.readTree(source.toFile());
            if (!Product36743SourceCatalog.PRODUCT_ID.equals(root.path("productId").asText())) {
                throw new SourceImportException("occupation productId does not match 36743");
            }
            if (!"1963059331068178434".equals(root.path("templateId").asText())) {
                throw new SourceImportException("occupation templateId does not match the confirmed version");
            }
            JsonNode items = root.path("items");
            if (!items.isArray() || items.size() != root.path("total").asInt()) {
                throw new SourceImportException("occupation row count does not match source total");
            }
            List<OccupationEntry> entries = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                entries.add(new OccupationEntry(
                        requiredText(item, "occupationId"),
                        requiredText(item, "occupationTemplateId"),
                        requiredText(item, "bigCategory"),
                        requiredText(item, "secondCategory"),
                        requiredText(item, "minorCategory"),
                        nullableText(item, "fourthCategory"),
                        item.path("classCode").asInt(-1),
                        item.path("ifDeclined").asInt(1) != 0
                ));
            }
            List<DocumentSection> sections = occupationSections(entries);
            return new ParsedMaterial(
                    sections, List.of(new PageMapping(1, null)), entries, null
            );
        } catch (IOException exception) {
            throw new SourceImportException("failed to parse occupation source", exception);
        }
    }

    private ParsedMaterial parseSpreadsheetSummary(SpreadsheetSummary summary) {
        if (summary.sheets().isEmpty()) {
            throw new SourceImportException("spreadsheet contains no worksheets: " + summary.fileName());
        }
        List<DocumentSection> sections = summary.sheets().stream()
                .map(sheet -> new DocumentSection(
                        "工作表 " + sheet.name(),
                        1,
                        null,
                        spreadsheetText(sheet),
                        Set.of("表格", "工作表")
                ))
                .toList();
        return new ParsedMaterial(
                sections, List.of(new PageMapping(1, null)), List.of(), summary
        );
    }

    private String spreadsheetText(SpreadsheetSummary.SheetSummary sheet) {
        StringBuilder text = new StringBuilder("工作表：").append(sheet.name());
        if (sheet.nonEmptyRows() >= 0) {
            text.append("\n非空行数：").append(sheet.nonEmptyRows())
                    .append("\n非空单元格数：").append(sheet.nonEmptyCells());
        } else {
            text.append("\n结构检查已通过；单元格统计待完整扫描。");
        }
        if (!sheet.sampleRows().isEmpty()) {
            text.append("\n抽样行：\n").append(String.join("\n", sheet.sampleRows()));
        }
        return text.toString();
    }

    private List<DocumentSection> occupationSections(List<OccupationEntry> entries) {
        List<DocumentSection> sections = new ArrayList<>();
        for (int start = 0; start < entries.size(); start += OCCUPATIONS_PER_SECTION) {
            int end = Math.min(entries.size(), start + OCCUPATIONS_PER_SECTION);
            StringBuilder text = new StringBuilder();
            Set<String> tags = new LinkedHashSet<>();
            for (OccupationEntry entry : entries.subList(start, end)) {
                text.append(entry.occupationId()).append(" | ")
                        .append(entry.bigCategory()).append(" | ")
                        .append(entry.secondCategory()).append(" | ")
                        .append(entry.minorCategory());
                if (entry.fourthCategory() != null) {
                    text.append(" | ").append(entry.fourthCategory());
                }
                text.append(" | 职业类别").append(entry.classCode()).append('\n');
                tags.add("职业" + entry.classCode() + "类");
            }
            sections.add(new DocumentSection(
                    "职业分类记录 " + (start + 1) + "-" + end,
                    1,
                    null,
                    text.toString().strip(),
                    tags
            ));
        }
        return sections;
    }

    private void validateOccupationDataset(List<OccupationEntry> occupations) {
        if (occupations.size() != 1_546) {
            throw new SourceImportException("expected 1546 occupation rows but found " + occupations.size());
        }
        Set<String> ids = new HashSet<>();
        Map<Integer, Integer> classCounts = new LinkedHashMap<>();
        for (OccupationEntry entry : occupations) {
            if (!ids.add(entry.occupationId())) {
                throw new SourceImportException("duplicate occupationId: " + entry.occupationId());
            }
            if (entry.classCode() < 1 || entry.classCode() > 4 || entry.declined()) {
                throw new SourceImportException("occupation classification is outside confirmed 1-4 range");
            }
            classCounts.merge(entry.classCode(), 1, Integer::sum);
        }
        Map<Integer, Integer> expected = Map.of(1, 358, 2, 423, 3, 398, 4, 367);
        if (!expected.equals(classCounts)) {
            throw new SourceImportException("occupation class counts do not match confirmed source");
        }
    }

    private Path validateRoot(Path sourceDirectory) {
        if (sourceDirectory == null) {
            throw new SourceImportException("source directory is required");
        }
        try {
            Path root = sourceDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw new SourceImportException("source path must be a real directory");
            }
            return root;
        } catch (IOException exception) {
            throw new SourceImportException("source directory is unavailable", exception);
        }
    }

    private Path resolveSource(Path safeRoot, SourceMaterialSpec spec) {
        try {
            Path candidate = safeRoot.resolve(spec.fileName()).normalize();
            if (!candidate.startsWith(safeRoot) || Files.isSymbolicLink(candidate)) {
                throw new SourceImportException("source file must not escape the confirmed directory");
            }
            Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(safeRoot) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
                throw new SourceImportException("source file is not a regular file: " + spec.fileName());
            }
            return real;
        } catch (IOException exception) {
            throw new SourceImportException("required source file is unavailable: " + spec.fileName(), exception);
        }
    }

    private void verifyHash(Path source, SourceMaterialSpec spec) {
        String actual = sha256(source);
        if (!spec.sha256().equals(actual)) {
            throw new SourceImportException("source hash mismatch: " + spec.fileName());
        }
    }

    private String sha256(Path source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(source)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new SourceImportException("failed to hash source: " + source.getFileName(), exception);
        }
    }

    private String detectSection(String text, int page) {
        for (String line : text.lines().map(String::strip).filter(value -> !value.isBlank()).limit(12).toList()) {
            if (line.matches("^(第[一二三四五六七八九十百]+条|[一二三四五六七八九十]+、|\\d+(\\.\\d+)*\\s+|【.+】|附表.+).*$")) {
                return limitSection(line);
            }
        }
        return "PDF第" + page + "页";
    }

    private String detectTextSection(String text, int index) {
        String firstLine = text.lines().map(String::strip).filter(value -> !value.isBlank()).findFirst().orElse("");
        return firstLine.isBlank() ? "文本段落" + index : limitSection(firstLine);
    }

    private String limitSection(String value) {
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private Set<String> tagsFor(String text) {
        Map<String, String> dictionary = Map.ofEntries(
                Map.entry("保险责任", "保障责任"),
                Map.entry("责任免除", "免责"),
                Map.entry("投保", "投保规则"),
                Map.entry("职业", "职业"),
                Map.entry("健康告知", "健康告知"),
                Map.entry("理赔", "理赔"),
                Map.entry("索赔", "理赔"),
                Map.entry("犹豫期", "犹豫期"),
                Map.entry("等待期", "等待期"),
                Map.entry("保全", "保全")
        );
        Set<String> tags = new LinkedHashSet<>();
        dictionary.forEach((keyword, tag) -> {
            if (text.contains(keyword)) {
                tags.add(tag);
            }
        });
        return tags;
    }

    private String clean(String value) {
        String withoutControls = CONTROL_CHARACTERS.matcher(value == null ? "" : value).replaceAll("");
        String normalizedLines = withoutControls.replace("\r\n", "\n").replace('\r', '\n');
        return MULTIPLE_BLANK_LINES.matcher(normalizedLines).replaceAll("\n\n").strip();
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null || value.isBlank()) {
            throw new SourceImportException("occupation field is missing: " + field);
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private record ParsedMaterial(
            List<DocumentSection> sections,
            List<PageMapping> pageMappings,
            List<OccupationEntry> occupations,
            SpreadsheetSummary spreadsheet
    ) {
    }
}
