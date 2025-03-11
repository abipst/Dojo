package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.config.DatabaseConfig;
import com.config.SftpConfig;
import com.config.SftpEnvironmentConfig;
import com.db.DbService;
import com.db.DbServiceImpl;
import com.intuit.karate.junit5.Karate;
import com.transfer.FileTransferService;
import com.transfer.SecureFileTransfer;

public abstract class BaseKarateTest {
    private static final Logger logger = LoggerFactory.getLogger(BaseKarateTest.class);
    
    // Instance services for each test scenario
    protected DbService dbService;
    protected FileTransferService fileTransferService;
    
    @BeforeEach
    public void setUp() {
        logger.info("Initializing resources for the scenario");
        
        // Initialize database service for the scenario
        initDbService();
        
        // Initialize SFTP service for the scenario
        initFileTransferService();
    }
    
    private void initDbService() {
        try {
            logger.info("Initializing database connection");
            dbService = new DbServiceImpl();
            logger.info("Database connection established successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database connection: {}", e.getMessage());
            throw new RuntimeException("Could not establish database connection", e);
        }
    }
    
    private void initFileTransferService() {
        try {
            logger.info("Initializing SFTP connection");
            SftpConfig config = SftpEnvironmentConfig.loadConfig();
            fileTransferService = new SecureFileTransfer(config);
            logger.info("SFTP connection established successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize SFTP connection: {}", e.getMessage());
            throw new RuntimeException("Could not establish SFTP connection", e);
        }
    }

    @AfterEach
    public void tearDown() {
        logger.info("Cleaning up resources for the scenario");
        
        // Close database service
        if (dbService != null) {
            try {
                dbService.close();
                logger.info("Database connection closed successfully");
            } catch (Exception e) {
                logger.error("Error closing database connection", e);
            }
        }
        
        // Close SFTP service
        if (fileTransferService != null) {
            try {
                fileTransferService.close();
                logger.info("SFTP connection closed successfully");
            } catch (Exception e) {
                logger.error("Error closing SFTP connection", e);
            }
        }
        
        // Ensure the DataSource is also closed
        DatabaseConfig.closeDataSource();
    }

    // Common configuration for Karate tests
    public Karate runTest() {
        return Karate.run(getClass().getSimpleName().toLowerCase());
    }
}
