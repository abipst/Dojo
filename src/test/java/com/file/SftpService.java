package com.file;

import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;

import com.utils.LoggerUtil;

import java.io.IOException;
import java.nio.file.Paths;
import java.io.InputStream;
import java.nio.file.Files;

public class SftpService {
    private static final Logger logger = LoggerUtil.getLogger(SftpService.class);

    private final SftpConnection sftpConnection;

    public SftpService(SftpConnection sftpConnection) {
        this.sftpConnection = sftpConnection;
    }

    // Remove file from the remote directory
    public void removeFile(String remoteFilePath) throws IOException {
        SftpClient sftpClient = sftpConnection.getSftpClient();
        if (sftpClient.stat(remoteFilePath) != null) {
            sftpClient.remove(remoteFilePath);
            logger.info("Removed existing file: {}", remoteFilePath);
        }
    }

    // Transfer file from local to remote directory
    public void transferFile(String localFilePath, String remoteFilePath) throws IOException {
        SftpClient sftpClient = sftpConnection.getSftpClient();
        try (InputStream inputStream = Files.newInputStream(Paths.get(localFilePath))) {
            try (SftpClient.CloseableHandle handle = sftpClient.open(remoteFilePath, SftpClient.OpenMode.Write,
                    SftpClient.OpenMode.Create)) {
                byte[] buffer = new byte[1024];
                int read;
                long offset = 0;
                while ((read = inputStream.read(buffer)) != -1) {
                    sftpClient.write(handle, offset, buffer, 0, read);
                    offset += read;
                }
            }
        }
        logger.info("File successfully transferred to: {}", remoteFilePath);
    }
}
