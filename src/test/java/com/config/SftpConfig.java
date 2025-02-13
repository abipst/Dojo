package com.config;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SftpConfig {
    private final String hostname;
    private final int port;
    private final String username;
    private final String ppkFilePath;
    private final String remoteBasePath;
    private final int timeout;
}
