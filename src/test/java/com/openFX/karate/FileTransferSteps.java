package com.openFX.karate;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import com.openFX.config.SftpConfig;
import com.openFX.config.SftpEnvironmentConfig;
import com.openFX.transfer.FileTransferService;
import com.openFX.transfer.SecureFileTransfer;
import com.openFX.db.DbServiceImpl;
import com.openFX.db.DbService;

public class FileTransferSteps {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferSteps.class);
    
    private static FileTransferService createFileTransfer() {
        SftpConfig config = SftpEnvironmentConfig.loadConfig();
        SecureFileTransfer fileTransfer = new SecureFileTransfer(config);
        return fileTransfer;
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