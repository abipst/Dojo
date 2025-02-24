package com.test;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.db.DbService;
import com.db.DbServiceImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FileDataValidationStep {
    private static final Logger logger = LoggerFactory.getLogger(FileDataValidationStep.class);
    private static final DbService dbService = new DbServiceImpl();
    private static final String DELIMITER = ",";
    private static final String DETAIL_RECORD_C = "C";
    private static final String DETAIL_RECORD_D = "D";

    public static List<Map<String, String>> parseFixedLengthFile(String filePath, List<Map<String, Object>> layout) {
        try {
            List<String> lines = FileUtils.readLines(new File(filePath), "UTF-8");
            List<Map<String, String>> records = new ArrayList<>();

            for (String line : lines) {
                Map<String, String> record = new HashMap<>();
                int position = 0;

                for (Map<String, Object> field : layout) {
                    String fieldName = (String) field.get("name");
                    int length = ((Number) field.get("length")).intValue();
                    String value = line.substring(position, Math.min(position + length, line.length())).trim();
                    record.put(fieldName, value);
                    position += length;
                }
                records.add(record);
            }
            return records;
        } catch (Exception e) {
            logger.error("Error parsing fixed length file: {}", filePath, e);
            throw new RuntimeException("Failed to parse fixed length file", e);
        }
    }

    public static List<Map<String, String>> parseCSVFile(String filePath, 
                                                        List<Map<String, Object>> cLayout,
                                                        List<Map<String, Object>> dLayout) {
        List<Map<String, String>> records = new ArrayList<>();
        List<String> allLines = new ArrayList<>();

        // First read all lines from the file
        try (BufferedReader reader = new BufferedReader(
                new FileReader(new File(filePath), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    allLines.add(line);
                }
            }
        } catch (Exception e) {
            logger.error("Error reading CSV file: {}", filePath, e);
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage(), e);
        }

        // Validate minimum file structure
        if (allLines.size() < 3) { // At least header, one detail record, and trailer
            throw new RuntimeException("File must contain at least 3 lines (header, detail, trailer)");
        }

        // Process all lines except header and trailer
        for (int i = 1; i < allLines.size() - 1; i++) {
            String line = allLines.get(i);
            try {
                String[] fields = line.split(DELIMITER, -1);
                if (fields.length == 0) {
                    continue;
                }

                String recordType = fields[0].trim();
                switch (recordType) {
                    case DETAIL_RECORD_C -> records.add(parseRecord(fields, cLayout));
                    case DETAIL_RECORD_D -> records.add(parseRecord(fields, dLayout));
                    default -> logger.warn("Unknown record type '{}' at line {}, skipping", recordType, i + 1);
                }
            } catch (Exception e) {
                logger.error("Error processing line {}: {}", i + 1, e.getMessage());
                throw new RuntimeException("Failed to process line " + (i + 1), e);
            }
        }

        return records;
    }

    private static Map<String, String> parseRecord(String[] fields, List<Map<String, Object>> layout) {
        Map<String, String> record = new HashMap<>();
        
        // Add the record type to the map
        record.put("recordType", fields[0].trim());

        // Parse remaining fields according to layout
        for (int i = 0; i < layout.size() && (i + 1) < fields.length; i++) {
            Map<String, Object> fieldDef = layout.get(i);
            String fieldName = (String) fieldDef.get("name");
            String value = fields[i + 1].trim();
            
            // Validate field length if specified
            Integer maxLength = (Integer) fieldDef.get("length");
            if (maxLength != null && value.length() > maxLength) {
                logger.warn("Field {} exceeds maximum length of {}. Truncating.", fieldName, maxLength);
                value = value.substring(0, maxLength);
            }
            
            record.put(fieldName, value);
        }

        return record;
    }

    public static List<Map<String, Object>> getDbRecordsByFileName(String fileName) {
        // Extract date from filename (assuming format: accounts_YYYYMMDD.txt)
        String dateStr = fileName.replaceAll("accounts_", "").replaceAll(".txt", "");

        // Query DB for all records for this file date
        return dbService.executeQuery("SELECT_ACCOUNTS_BY_FILE_DATE", dateStr);
    }

    public static Map<String, Object> validateFile(List<Map<String, String>> fileRecords,
            List<Map<String, Object>> dbRecords,
            List<Map<String, Object>> layout) {
        Map<String, Object> validationResult = new HashMap<>();
        List<Map<String, Object>> mismatches = new ArrayList<>();

        // Get field to column mappings from layout
        Map<String, String> fieldToColumnMap = createFieldToColumnMap(layout);

        // Validate records by position
        int recordsToValidate = Math.min(fileRecords.size(), dbRecords.size());

        for (int i = 0; i < recordsToValidate; i++) {
            Map<String, String> fileRecord = fileRecords.get(i);
            Map<String, Object> dbRecord = dbRecords.get(i);
            List<String> fieldMismatches = new ArrayList<>();

            // Compare each field defined in the layout
            for (Map.Entry<String, String> mapping : fieldToColumnMap.entrySet()) {
                String fileField = mapping.getKey();
                String dbColumn = mapping.getValue();

                String fileValue = fileRecord.get(fileField);
                String dbValue = getFormattedDbValue(dbRecord.get(dbColumn));

                if (!compareValues(fileValue, dbValue)) {
                    fieldMismatches.add(String.format("%s (File: '%s', DB: '%s')",
                            fileField, fileValue, dbValue));
                }
            }

            if (!fieldMismatches.isEmpty()) {
                Map<String, Object> mismatch = new HashMap<>();
                mismatch.put("recordNumber", i + 1);
                mismatch.put("fileRecord", fileRecord);
                mismatch.put("dbRecord", dbRecord);
                mismatch.put("mismatchedFields", fieldMismatches);
                mismatches.add(mismatch);
            }
        }

        // Check for count mismatch
        if (fileRecords.size() != dbRecords.size()) {
            logger.warn("Record count mismatch - File: {}, DB: {}",
                    fileRecords.size(), dbRecords.size());
        }

        // Prepare validation result
        validationResult.put("totalFileRecords", fileRecords.size());
        validationResult.put("totalDbRecords", dbRecords.size());
        validationResult.put("recordsCompared", recordsToValidate);
        validationResult.put("matchedRecords", recordsToValidate - mismatches.size());
        validationResult.put("mismatches", mismatches);
        validationResult.put("status", mismatches.isEmpty() &&
                fileRecords.size() == dbRecords.size() ? "PASSED" : "FAILED");

        return validationResult;
    }

    public static Map<String, Object> validateFileData(Object fileRecords,
            Object dbRecords,
            Object layout) {
        Map<String, Object> validationResult = new HashMap<>();
        List<Map<String, Object>> mismatches = new ArrayList<>();

        // Type validation
        if (!(fileRecords instanceof List<?>) ||
                !(dbRecords instanceof List<?>) ||
                !(layout instanceof List<?>)) {
            throw new IllegalArgumentException("Inputs must be List instances");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, String>> typedFileRecords = (List<Map<String, String>>) fileRecords;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> typedDbRecords = (List<Map<String, Object>>) dbRecords;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> typedLayout = (List<Map<String, Object>>) layout;

        // Get list of fields to validate by checking which layout fields exist in DB
        // records
        List<String> fieldsToValidate = typedLayout.stream()
                .map(field -> (String) field.get("name"))
                .filter(fieldName -> !typedDbRecords.isEmpty() && typedDbRecords.get(0).containsKey(fieldName))
                .collect(Collectors.toList());

        // Get list of fields to validate by intersecting layout fields with DB fields
        // List<String> fieldsToValidate = getFieldsToValidate(typedLayout,
        // typedDbRecords);
        // Validate records by position
        int recordsToValidate = Math.min(typedFileRecords.size(), typedDbRecords.size());

        for (int i = 0; i < recordsToValidate; i++) {
            Map<String, String> fileRecord = typedFileRecords.get(i);
            Map<String, Object> dbRecord = typedDbRecords.get(i);
            List<String> fieldMismatches = new ArrayList<>();

            // Compare each field directly using the field name
            for (String fieldName : fieldsToValidate) {
                String fileValue = fileRecord.get(fieldName);
                String dbValue = getFormattedDbValue(dbRecord.get(fieldName));

                if (!compareValues(fileValue, dbValue)) {
                    fieldMismatches.add(String.format("%s (File: '%s', DB: '%s')",
                            fieldName, fileValue, dbValue));
                }
            }

            if (!fieldMismatches.isEmpty()) {
                Map<String, Object> mismatch = new HashMap<>();
                mismatch.put("recordNumber", i + 1);
                mismatch.put("fileRecord", fileRecord);
                mismatch.put("dbRecord", dbRecord);
                mismatch.put("mismatchedFields", fieldMismatches);
                mismatches.add(mismatch);
            }
        }

        // Prepare validation result
        validationResult.put("totalFileRecords", typedFileRecords.size());
        validationResult.put("totalDbRecords", typedDbRecords.size());
        validationResult.put("recordsCompared", recordsToValidate);
        validationResult.put("matchedRecords", recordsToValidate - mismatches.size());
        validationResult.put("mismatches", mismatches);
        validationResult.put("status", mismatches.isEmpty() &&
                typedFileRecords.size() == typedDbRecords.size() ? "PASSED" : "FAILED");

        return validationResult;
    }

    private static Map<String, String> createFieldToColumnMap(List<Map<String, Object>> layout) {
        Map<String, String> fieldToColumnMap = new HashMap<>();
        for (Map<String, Object> field : layout) {
            String fieldName = (String) field.get("name");
            String dbColumn = (String) field.get("dbColumn"); // Add dbColumn to your layout definition
            fieldToColumnMap.put(fieldName, dbColumn);
        }
        return fieldToColumnMap;
    }

    @SuppressWarnings("unused")
    private static List<String> getFieldsToValidate(List<Map<String, Object>> layout,
            List<Map<String, Object>> dbRecords) {
        // Get DB field names from first record
        Set<String> dbFields = dbRecords.isEmpty() ? Collections.emptySet() : new HashSet<>(dbRecords.get(0).keySet());

        // Filter layout fields that exist in DB
        return layout.stream()
                .map(field -> (String) field.get("name"))
                .filter(dbFields::contains)
                .collect(Collectors.toList());
    }

    private static String getFormattedDbValue(Object dbValue) {
        if (dbValue == null) {
            return "";
        }
        return dbValue.toString().trim();
    }

    private static boolean compareValues(String fileValue, String dbValue) {
        // Handle null values
        if (fileValue == null && dbValue == null) {
            return true;
        }
        if (fileValue == null || dbValue == null) {
            return false;
        }

        // Compare trimmed values
        return fileValue.trim().equals(dbValue.trim());
    }

    public static void closeDbConnection() {
        try {
            dbService.close();
        } catch (Exception e) {
            logger.error("Error closing database connection", e);
        }
    }
}