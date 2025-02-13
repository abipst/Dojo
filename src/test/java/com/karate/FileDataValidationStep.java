package com.karate;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.db.DbService;
import com.db.DbServiceImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileDataValidationStep {
    private static final Logger logger = LoggerFactory.getLogger(FileDataValidationStep.class);
    private static final DbService dbService = new DbServiceImpl();

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

    private static Map<String, String> createFieldToColumnMap(List<Map<String, Object>> layout) {
        Map<String, String> fieldToColumnMap = new HashMap<>();
        for (Map<String, Object> field : layout) {
            String fieldName = (String) field.get("name");
            String dbColumn = (String) field.get("dbColumn"); // Add dbColumn to your layout definition
            fieldToColumnMap.put(fieldName, dbColumn);
        }
        return fieldToColumnMap;
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