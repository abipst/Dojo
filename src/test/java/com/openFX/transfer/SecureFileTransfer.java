package com.openFX.transfer;

import com.openFX.config.SftpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class SecureFileTransfer implements FileTransferService {
    private static final Logger logger = LoggerFactory.getLogger(SecureFileTransfer.class);
    private final FileTransferService transferService;
    private final String remoteBasePath;

    public SecureFileTransfer(SftpConfig config) {
        this.transferService = new SftpService(config);
        this.remoteBasePath = config.getRemoteBasePath();
    }

    @Override
    public boolean uploadFile(Path sourcePath, String remoteDirectory) {
        String fullRemotePath = remoteBasePath + "/" + remoteDirectory;
        try {
            logger.info("Starting secure file transfer to: {}", fullRemotePath);
            return transferService.uploadFile(sourcePath, fullRemotePath);
        } catch (Exception e) {
            logger.error("Secure file transfer failed", e);
            throw new FileTransferException("Secure file transfer failed", e);
        }
    }

    @Override
    public boolean downloadFile(String remoteFilePath, Path localDestination) {
        String fullRemotePath = remoteBasePath + "/" + remoteFilePath;
        try {
            return transferService.downloadFile(fullRemotePath, localDestination);
        } catch (Exception e) {
            throw new FileTransferException("Secure file download failed", e);
        }
    }

    @Override
    public boolean deleteFile(String remoteFilePath) {
        String fullRemotePath = remoteBasePath + "/" + remoteFilePath;
        try {
            return transferService.deleteFile(fullRemotePath);
        } catch (Exception e) {
            throw new FileTransferException("Secure file deletion failed", e);
        }
    }

    @Override
    public List<String> listFiles(String remoteDirectory) {
        String fullRemotePath = remoteBasePath + "/" + remoteDirectory;
        try {
            return transferService.listFiles(fullRemotePath);
        } catch (Exception e) {
            throw new FileTransferException("Failed to list files", e);
        }
    }

    @Override
    public boolean exists(String remoteFilePath) {
        String fullRemotePath = remoteBasePath + "/" + remoteFilePath;
        try {
            return transferService.exists(fullRemotePath);
        } catch (Exception e) {
            throw new FileTransferException("Failed to check file existence", e);
        }
    }

    @Override
    public void close() {
        try {
            transferService.close();
        } catch (Exception e) {
            logger.error("Failed to close transfer service", e);
        }
    }
}
