package com.openFX.karate;

import com.openFX.db.DbService;
import com.openFX.db.DbServiceImpl;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static Map<String, Object> validateFile(List<Map<String, String>> fileRecords, List<Map<String, Object>> dbRecords) {
        Map<String, Object> validationResult = new HashMap<>();
        List<Map<String, Object>> mismatches = new ArrayList<>();
        
        // Create a map of DB records for easier lookup
        Map<String, Map<String, Object>> dbRecordsMap = new HashMap<>();
        for (Map<String, Object> dbRecord : dbRecords) {
            dbRecordsMap.put(String.valueOf(dbRecord.get("ACCOUNT_ID")), dbRecord);
        }

        // Validate each file record
        for (Map<String, String> fileRecord : fileRecords) {
            Map<String, Object> dbRecord = dbRecordsMap.get(fileRecord.get("accountId"));
            
            if (dbRecord == null) {
                mismatches.add(createMismatchRecord(fileRecord, null, "Record not found in database"));
                continue;
            }

            List<String> fieldMismatches = new ArrayList<>();
            
            // Compare fields
            if (!fileRecord.get("customerName").equals(dbRecord.get("CUSTOMER_NAME"))) {
                fieldMismatches.add("customerName");
            }
            if (!fileRecord.get("transactionDate").equals(dbRecord.get("TRANSACTION_DATE"))) {
                fieldMismatches.add("transactionDate");
            }
            if (!fileRecord.get("amount").trim().equals(String.valueOf(dbRecord.get("AMOUNT")).trim())) {
                fieldMismatches.add("amount");
            }
            if (!fileRecord.get("status").equals(dbRecord.get("STATUS"))) {
                fieldMismatches.add("status");
            }

            if (!fieldMismatches.isEmpty()) {
                Map<String, Object> mismatch = createMismatchRecord(fileRecord, dbRecord, "Field mismatch");
                mismatch.put("mismatchedFields", fieldMismatches);
                mismatches.add(mismatch);
            }
        }

        validationResult.put("totalRecords", fileRecords.size());
        validationResult.put("matchedRecords", fileRecords.size() - mismatches.size());
        validationResult.put("mismatches", mismatches);
        validationResult.put("status", mismatches.isEmpty() ? "PASSED" : "FAILED");

        return validationResult;
    }

    private static Map<String, Object> createMismatchRecord(Map<String, String> fileRecord, 
                                                          Map<String, Object> dbRecord, 
                                                          String error) {
        Map<String, Object> mismatch = new HashMap<>();
        mismatch.put("accountId", fileRecord.get("accountId"));
        mismatch.put("fileRecord", fileRecord);
        mismatch.put("dbRecord", dbRecord);
        mismatch.put("error", error);
        return mismatch;
    }

    public static void closeDbConnection() {
        try {
            dbService.close();
        } catch (Exception e) {
            logger.error("Error closing database connection", e);
        }
    }
}