package com.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpEnvironmentConfig {
    private static final Logger logger = LoggerFactory.getLogger(SftpEnvironmentConfig.class);
    
    private static final String DEFAULT_PORT = "22";
    private static final String DEFAULT_TIMEOUT = "30000";
    
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
    
    public static SftpConfig loadConfig() {
        try {
            String hostname = getRequiredProperty("SFTP_HOSTNAME");
            String username = getRequiredProperty("SFTP_USERNAME");
            String ppkFilePath = getRequiredProperty("SFTP_PPK_FILE_PATH");
            String remoteBasePath = getRequiredProperty("SFTP_REMOTE_BASE_PATH");
            
            int port = Integer.parseInt(getPropertyWithDefault("SFTP_PORT", DEFAULT_PORT));
            int timeout = Integer.parseInt(getPropertyWithDefault("SFTP_TIMEOUT", DEFAULT_TIMEOUT));
            
            return SftpConfig.builder()
                    .hostname(hostname)
                    .port(port)
                    .username(username)
                    .ppkFilePath(ppkFilePath)
                    .remoteBasePath(remoteBasePath)
                    .timeout(timeout)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to load SFTP configuration from .env file", e);
            throw new IllegalStateException("SFTP configuration error: " + e.getMessage(), e);
        }
    }
    
    private static String getRequiredProperty(String name) {
        String value = dotenv.get(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required property '" + name + "' is not set in .env file");
        }
        return value.trim();
    }
    
    private static String getPropertyWithDefault(String name, String defaultValue) {
        String value = dotenv.get(name);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
}