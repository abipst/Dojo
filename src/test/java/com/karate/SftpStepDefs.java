package com.karate;

import org.slf4j.Logger;

import com.file.SftpConnection;
import com.file.SftpService;
import com.utils.LoggerUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class SftpStepDefs {

    private static final Logger logger = LoggerUtil.getLogger(SftpStepDefs.class);
    private static SftpConnection sftpConnection;
    private static SftpService sftpService;

    // This method will be run before all tests in the feature file
    @BeforeAll
    public static void setupSftpConnection() throws IOException, NoSuchAlgorithmException {
        sftpConnection = new SftpConnection();
        sftpService = new SftpService(sftpConnection);
        sftpConnection.connect();
        logger.info("SFTP connection established.");
    }

    // This method will be run after all tests in the feature file
    @AfterAll
    public static void disconnectSftpConnection() {
        if (sftpConnection != null) {
            sftpConnection.disconnect();
            logger.info("SFTP connection disconnected.");
        }
    }

    // Remove existing file from remote directory if it exists
    @Test
    public void removeFileIfExists(String remoteFilePath) throws IOException {
        try {
            sftpService.removeFile(remoteFilePath);
        } catch (IOException e) {
            logger.error("Error removing file: " + remoteFilePath, e);
            throw new IOException("Failed to remove file: " + remoteFilePath, e);
        }
    }

    // Transfer file from local to remote
    @Test
    public void transferFile(String localFilePath, String remoteFilePath) throws IOException {
        try {
            sftpService.transferFile(localFilePath, remoteFilePath);
        } catch (IOException e) {
            logger.error("Error transferring file from " + localFilePath + " to " + remoteFilePath, e);
            throw new IOException("Failed to transfer file", e);
        }
    }
}
