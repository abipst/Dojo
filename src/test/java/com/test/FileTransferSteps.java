package com.test;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import com.db.DbService;
import com.transfer.FileTransferService;

public class FileTransferSteps extends BaseKarateTest {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferSteps.class);

    // No need for @BeforeEach and @AfterEach here since BaseKarateTest handles it
    // Instance services for this class
    private DbService dbService;
    private FileTransferService fileTransferService;

    // You can directly access the dbService and fileTransferService initialized in BaseKarateTest
    
    public boolean checkFileExistsInDatabase(String fileName) {
        logger.info("Checking if file exists in database: {}", fileName);
        try {
            List<Map<String, Object>> result = dbService.executeQuery(
                    "SELECT_FILE_BY_NAME",
                    fileName);
            return !result.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking file existence: {}", fileName, e);
            throw new RuntimeException("Failed to check file existence", e);
        }
    }

    public void deleteFileRecord(String fileName) {
        logger.info("Deleting file record from database: {}", fileName);
        try {
            int result = dbService.executeUpdate(
                    "DELETE_FILE_RECORD",
                    fileName);
            if (result == 0) {
                logger.warn("No file record found to delete for: {}", fileName);
            }
        } catch (Exception e) {
            logger.error("Error deleting file record: {}", fileName, e);
            throw new RuntimeException("Failed to delete file record", e);
        }
    }

    public Map<String, Object> transferFileToSecureSystem(String filePath) {
        Map<String, Object> result = new HashMap<>();

        try {
            Path path = Paths.get(filePath);
            if (!path.toFile().exists()) {
                throw new IllegalArgumentException("File not found: " + filePath);
            }

            // Transfer file to remote directory using the shared connection
            boolean transferred = fileTransferService.uploadFile(path, "incoming");

            if (transferred) {
                // Record successful transfer in database
                dbService.executeUpdate(
                        "INSERT_FILE_RECORD",
                        path.getFileName().toString(),
                        "TRANSFERRED",
                        LocalDateTime.now().toString());

                result.put("success", true);
                result.put("message", "File transferred successfully");
            } else {
                result.put("success", false);
                result.put("message", "File transfer failed");
            }
        } catch (Exception e) {
            logger.error("Error transferring file: {}", filePath, e);
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
        }

        return result;
    }

    public boolean cleanupTransferredFiles(String fileName) {
        logger.info("Cleaning up transferred files");
        try {
            // Delete from secure system using the shared connection
            return fileTransferService.deleteFile(fileName);
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
            throw new RuntimeException("Failed to cleanup transferred files", e);
        }
    }
}
