package com.embabel.insurance.xiaoming.knowledge.importer;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.util.XMLHelper;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class SpreadsheetSourceParser {

    private static final int MAX_SAMPLE_ROWS = 12;
    private static final int MAX_SAMPLE_CELLS = 24;
    private static final int MAX_SAMPLE_CHARACTERS = 500;
    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public SpreadsheetSummary parse(Path path) {
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (lowerName.endsWith(".xlsx")) {
                return parseXlsx(path);
            }
            if (lowerName.endsWith(".xls")) {
                return parseXls(path);
            }
            throw new SourceImportException("unsupported spreadsheet format: " + path.getFileName());
        } catch (SourceImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SourceImportException("failed to parse spreadsheet: " + path.getFileName(), exception);
        }
    }

    public SpreadsheetSummary inspectStructure(Path path) {
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (lowerName.endsWith(".xlsx")) {
                List<SpreadsheetSummary.SheetSummary> sheets = new ArrayList<>();
                try (OPCPackage container = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
                    XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) new XSSFReader(container).getSheetsData();
                    while (iterator.hasNext()) {
                        try (InputStream ignored = iterator.next()) {
                            sheets.add(new SpreadsheetSummary.SheetSummary(
                                    iterator.getSheetName(), -1, -1, List.of()
                            ));
                        }
                    }
                }
                return new SpreadsheetSummary(path.getFileName().toString(), sheets);
            }
            if (lowerName.endsWith(".xls")) {
                try (InputStream input = Files.newInputStream(path);
                     HSSFWorkbook workbook = new HSSFWorkbook(input)) {
                    List<SpreadsheetSummary.SheetSummary> sheets = new ArrayList<>();
                    for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                        sheets.add(new SpreadsheetSummary.SheetSummary(
                                workbook.getSheetName(index), -1, -1, List.of()
                        ));
                    }
                    return new SpreadsheetSummary(path.getFileName().toString(), sheets);
                }
            }
            throw new SourceImportException("unsupported spreadsheet format: " + path.getFileName());
        } catch (SourceImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SourceImportException("failed to inspect spreadsheet: " + path.getFileName(), exception);
        }
    }

    private SpreadsheetSummary parseXlsx(Path path) throws Exception {
        List<SpreadsheetSummary.SheetSummary> sheets = new ArrayList<>();
        try (OPCPackage container = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(container);
            StylesTable styles = reader.getStylesTable();
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (iterator.hasNext()) {
                try (InputStream stream = iterator.next()) {
                    StreamingSheetHandler handler = new StreamingSheetHandler();
                    XMLReader xmlReader = XMLHelper.newXMLReader();
                    xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                            styles,
                            null,
                            sharedStrings,
                            handler,
                            formatter,
                            false
                    ));
                    xmlReader.parse(new InputSource(stream));
                    sheets.add(handler.summary(iterator.getSheetName()));
                }
            }
        }
        return new SpreadsheetSummary(path.getFileName().toString(), sheets);
    }

    private SpreadsheetSummary parseXls(Path path) throws IOException {
        List<SpreadsheetSummary.SheetSummary> sheets = new ArrayList<>();
        try (InputStream input = Files.newInputStream(path);
             HSSFWorkbook workbook = new HSSFWorkbook(input)) {
            for (Sheet sheet : workbook) {
                long nonEmptyRows = 0;
                long nonEmptyCells = 0;
                List<String> samples = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).strip();
                        if (!value.isEmpty()) {
                            nonEmptyCells++;
                            if (values.size() < MAX_SAMPLE_CELLS) {
                                values.add(value);
                            }
                        }
                    }
                    if (!values.isEmpty()) {
                        nonEmptyRows++;
                        if (samples.size() < MAX_SAMPLE_ROWS) {
                            samples.add(limit(String.join(" | ", values)));
                        }
                    }
                }
                sheets.add(new SpreadsheetSummary.SheetSummary(
                        sheet.getSheetName(), nonEmptyRows, nonEmptyCells, samples
                ));
            }
        }
        return new SpreadsheetSummary(path.getFileName().toString(), sheets);
    }

    private static String limit(String value) {
        return value.length() <= MAX_SAMPLE_CHARACTERS
                ? value
                : value.substring(0, MAX_SAMPLE_CHARACTERS);
    }

    private static final class StreamingSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private long nonEmptyRows;
        private long nonEmptyCells;
        private final List<String> samples = new ArrayList<>();
        private List<String> currentValues = List.of();

        @Override
        public void startRow(int rowNum) {
            currentValues = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            if (!currentValues.isEmpty()) {
                nonEmptyRows++;
                if (samples.size() < MAX_SAMPLE_ROWS) {
                    samples.add(limit(String.join(" | ", currentValues)));
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
            if (formattedValue == null || formattedValue.isBlank()) {
                return;
            }
            nonEmptyCells++;
            if (currentValues.size() < MAX_SAMPLE_CELLS) {
                currentValues.add(formattedValue.strip());
            }
        }

        private SpreadsheetSummary.SheetSummary summary(String name) {
            return new SpreadsheetSummary.SheetSummary(name, nonEmptyRows, nonEmptyCells, samples);
        }
    }
}
