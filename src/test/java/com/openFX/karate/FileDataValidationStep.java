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

    public static List<Map<String, Object>> validateRecordAgainstDb(String accountId) {
        return dbService.executeQuery("SELECT_ACCOUNT_DATA", accountId);
    }

    public static Map<String, Object> compareRecords(Map<String, String> fileRecord, Map<String, Object> dbRecord) {
        Map<String, Object> comparison = new HashMap<>();
        List<String> mismatches = new ArrayList<>();

        if (dbRecord == null || dbRecord.isEmpty()) {
            comparison.put("status", "FAILED");
            comparison.put("error", "No matching record found in database");
            return comparison;
        }

        // Compare each field
        if (!fileRecord.get("customerName").equals(dbRecord.get("CUSTOMER_NAME"))) {
            mismatches.add("customerName");
        }
        if (!fileRecord.get("transactionDate").equals(dbRecord.get("TRANSACTION_DATE"))) {
            mismatches.add("transactionDate");
        }
        if (!fileRecord.get("amount").trim().equals(String.valueOf(dbRecord.get("AMOUNT")).trim())) {
            mismatches.add("amount");
        }
        if (!fileRecord.get("status").equals(dbRecord.get("STATUS"))) {
            mismatches.add("status");
        }

        comparison.put("status", mismatches.isEmpty() ? "PASSED" : "FAILED");
        comparison.put("mismatches", mismatches);
        comparison.put("fileRecord", fileRecord);
        comparison.put("dbRecord", dbRecord);

        return comparison;
    }

    public static void closeDbConnection() {
        try {
            dbService.close();
        } catch (Exception e) {
            logger.error("Error closing database connection", e);
        }
    }
}
