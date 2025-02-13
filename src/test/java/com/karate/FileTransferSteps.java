package com.karate;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import com.config.SftpConfig;
import com.config.SftpEnvironmentConfig;
import com.db.DbService;
import com.db.DbServiceImpl;
import com.transfer.FileTransferService;
import com.transfer.SecureFileTransfer;

public class FileTransferSteps {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferSteps.class);
    
    private static FileTransferService createFileTransfer() {
        SftpConfig config = SftpEnvironmentConfig.loadConfig();
        SecureFileTransfer fileTransfer = new SecureFileTransfer(config);
        return fileTransfer;
    }

    public static boolean checkFileExistsInDatabase(String fileName) {
        logger.info("Checking if file exists in database: {}", fileName);
        try (DbService dbService = new DbServiceImpl()) {
            List<Map<String, Object>> result = dbService.executeQuery(
                "SELECT_FILE_BY_NAME",
                fileName
            );
            return !result.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking file existence: {}", fileName, e);
            throw new RuntimeException("Failed to check file existence", e);
        }
    }

    public static void deleteFileRecord(String fileName) {
        logger.info("Deleting file record from database: {}", fileName);
        try (DbService dbService = new DbServiceImpl()) {
            int result = dbService.executeUpdate(
                "DELETE_FILE_RECORD",
                fileName
            );
            if (result == 0) {
                logger.warn("No file record found to delete for: {}", fileName);
            }
        } catch (Exception e) {
            logger.error("Error deleting file record: {}", fileName, e);
            throw new RuntimeException("Failed to delete file record", e);
        }
    }

    public static Map<String, Object> transferFileToSecureSystem(String filePath) {
        Map<String, Object> result = new HashMap<>();
        
        try (FileTransferService fileTransfer = createFileTransfer()) {
            Path path = Paths.get(filePath);
            if (!path.toFile().exists()) {
                throw new IllegalArgumentException("File not found: " + filePath);
            }

            // Transfer file to remote directory
            boolean transferred = fileTransfer.uploadFile(path, "incoming");
            
            if (transferred) {
                // Record successful transfer in database
                try (DbService dbService = new DbServiceImpl()) {
                    dbService.executeUpdate(
                        "INSERT_FILE_RECORD",
                        path.getFileName().toString(),
                        "TRANSFERRED",
                        LocalDateTime.now().toString()
                    );
                }
                
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
}