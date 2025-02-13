package com.transfer;


import java.nio.file.Path;
import java.util.List;

public interface FileTransferService extends AutoCloseable {
    boolean uploadFile(Path sourcePath, String remoteDirectory);
    boolean downloadFile(String remoteFilePath, Path localDestination);
    boolean deleteFile(String remoteFilePath);
    List<String> listFiles(String remoteDirectory);
    boolean exists(String remoteFilePath);
}
