package com.openFX.transfer;

import com.jcraft.jsch.*;
import com.openFX.config.SftpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class SftpService implements FileTransferService {
    private static final Logger logger = LoggerFactory.getLogger(SftpService.class);
    
    private final JSch jsch;
    private final Session session;
    private ChannelSftp sftpChannel;
    private final SftpConfig config;

    public SftpService(SftpConfig config) {
        this.config = config;
        try {
            jsch = new JSch();
            jsch.addIdentity(config.getPpkFilePath());
            
            session = jsch.getSession(config.getUsername(), config.getHostname(), config.getPort());
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(config.getTimeout());
            
            connect();
        } catch (JSchException e) {
            throw new FileTransferException("Failed to initialize SFTP service", e);
        }
    }

    private void connect() throws JSchException {
        try {
            if (!session.isConnected()) {
                session.connect();
            }
            
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftpChannel = (ChannelSftp) channel;
            
            logger.info("Successfully connected to SFTP server: {}", config.getHostname());
        } catch (JSchException e) {
            throw new FileTransferException("Failed to connect to SFTP server", e);
        }
    }

    @Override
    public boolean uploadFile(Path sourcePath, String remoteDirectory) {
        try {
            ensureConnected();
            createDirectoryIfNotExists(remoteDirectory);
            
            String remoteFilePath = remoteDirectory + "/" + sourcePath.getFileName();
            logger.info("Uploading file to: {}", remoteFilePath);
            
            sftpChannel.put(sourcePath.toString(), remoteFilePath);
            return true;
        } catch (SftpException e) {
            logger.error("Failed to upload file: {}", sourcePath, e);
            throw new FileTransferException("File upload failed", e);
        }
    }

    @Override
    public boolean downloadFile(String remoteFilePath, Path localDestination) {
        try {
            ensureConnected();
            logger.info("Downloading file from: {} to: {}", remoteFilePath, localDestination);
            
            sftpChannel.get(remoteFilePath, localDestination.toString());
            return true;
        } catch (SftpException e) {
            logger.error("Failed to download file: {}", remoteFilePath, e);
            throw new FileTransferException("File download failed", e);
        }
    }

    @Override
    public boolean deleteFile(String remoteFilePath) {
        try {
            ensureConnected();
            logger.info("Deleting file: {}", remoteFilePath);
            
            sftpChannel.rm(remoteFilePath);
            return true;
        } catch (SftpException e) {
            logger.error("Failed to delete file: {}", remoteFilePath, e);
            throw new FileTransferException("File deletion failed", e);
        }
    }

    @Override
    public List<String> listFiles(String remoteDirectory) {
        try {
            ensureConnected();
            List<String> files = new ArrayList<>();
            
            Vector<ChannelSftp.LsEntry> fileList = sftpChannel.ls(remoteDirectory);
            for (ChannelSftp.LsEntry entry : fileList) {
                if (!entry.getAttrs().isDir()) {
                    files.add(entry.getFilename());
                }
            }
            return files;
        } catch (SftpException e) {
            logger.error("Failed to list files in directory: {}", remoteDirectory, e);
            throw new FileTransferException("Failed to list files", e);
        }
    }

    @Override
    public boolean exists(String remoteFilePath) {
        try {
            ensureConnected();
            sftpChannel.lstat(remoteFilePath);
            return true;
        } catch (SftpException e) {
            return false;
        }
    }

    private void ensureConnected() {
        try {
            if (!session.isConnected() || !sftpChannel.isConnected()) {
                connect();
            }
        } catch (JSchException e) {
            throw new FileTransferException("Failed to reconnect to SFTP server", e);
        }
    }

    private void createDirectoryIfNotExists(String path) throws SftpException {
        try {
            sftpChannel.lstat(path);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                sftpChannel.mkdir(path);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void close() {
        try {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            logger.info("SFTP connection closed successfully");
        } catch (Exception e) {
            logger.error("Error closing SFTP connection", e);
            throw new FileTransferException("Failed to close SFTP connection", e);
        }
    }
}
