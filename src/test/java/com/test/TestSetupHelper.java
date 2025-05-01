package com.test;

import com.config.DatabaseConfig;
import com.config.SftpConfig;
import com.config.SftpEnvironmentConfig;
import com.db.DbService;
import com.db.DbServiceImpl;
import com.transfer.FileTransferService;
import com.transfer.SecureFileTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class TestSetupHelper {
    private static final Logger logger = LoggerFactory.getLogger(TestSetupHelper.class);
    
    // Static service instances with thread safety
    private static final Object dbLock = new Object();
    private static final Object ftpLock = new Object();
    private static DbService dbServiceInstance;
    private static FileTransferService fileTransferServiceInstance;
    
    // Getters for the services
    public static DbService getDbService() {
        if (dbServiceInstance == null) {
            throw new IllegalStateException("Database service has not been initialized.");
        }
        return dbServiceInstance;
    }
    
    public static FileTransferService getFileTransferService() {
        if (fileTransferServiceInstance == null) {
            throw new IllegalStateException("File transfer service has not been initialized.");
        }
        return fileTransferServiceInstance;
    }
    
    // Called by Karate hook before each scenario
    public static void setupBeforeScenario() {
        logger.info("Setting up resources for scenario");
        
        // Initialize database service
        initDbService();
        
        // Initialize SFTP service
        initFileTransferService();
        
        logger.info("Setup completed for scenario");
    }
    
    private static void initDbService() {
        try {
            synchronized (dbLock) {
                logger.info("Initializing database connection");
                if (dbServiceInstance == null) {
                    dbServiceInstance = new DbServiceImpl();
                    logger.info("Database connection established successfully");
                } else {
                    logger.info("Reusing existing database connection");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize database connection: {}", e.getMessage());
            throw new RuntimeException("Could not establish database connection", e);
        }
    }
    
    private static void initFileTransferService() {
        try {
            synchronized (ftpLock) {
                logger.info("Initializing SFTP connection");
                if (fileTransferServiceInstance == null) {
                    SftpConfig config = SftpEnvironmentConfig.loadConfig();
                    fileTransferServiceInstance = new SecureFileTransfer(config);
                    logger.info("SFTP connection established successfully");
                } else {
                    logger.info("Reusing existing SFTP connection");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize SFTP connection: {}", e.getMessage());
            throw new RuntimeException("Could not establish SFTP connection", e);
        }
    }
    
    // Called by Karate hook after each scenario
    public static void cleanupAfterScenario() {
        logger.info("Cleaning up resources after scenario");
        
        try {
            // Reset any scenario-specific data in the database
            if (dbServiceInstance != null) {
                logger.info("Resetting database state for next scenario");
                try {
                    dbServiceInstance.executeUpdate("DELETE_TEST_DATA");
                    // Add more specific cleanup queries as needed
                } catch (Exception e) {
                    logger.warn("Error during database cleanup: {}", e.getMessage());
                }
            }
            
            // Clean up any test files on the SFTP server
            if (fileTransferServiceInstance != null) {
                logger.info("Cleaning up test files from SFTP server");
                try {
                    // Delete test files from common test directories
                    List<String> testFiles = fileTransferServiceInstance.listFiles("incoming");
                    for (String file : testFiles) {
                        if (file.startsWith("test_") || file.contains("_test_")) {
                            logger.info("Deleting test file: {}", file);
                            fileTransferServiceInstance.deleteFile(file);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error during SFTP cleanup: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error during scenario cleanup", e);
        }
        
        logger.info("Scenario cleanup completed");
    }
    
    // Called when all tests are complete (needs to be called from a JUnit @AfterAll method)
    public static void tearDownAllResources() {
        logger.info("Tearing down all test resources");
        
        // Close database service
        synchronized (dbLock) {
            if (dbServiceInstance != null) {
                try {
                    dbServiceInstance.close();
                    logger.info("Database connection closed successfully");
                    dbServiceInstance = null;
                } catch (Exception e) {
                    logger.error("Error closing database connection", e);
                }
            }
        }
        
        // Close SFTP service
        synchronized (ftpLock) {
            if (fileTransferServiceInstance != null) {
                try {
                    fileTransferServiceInstance.close();
                    logger.info("SFTP connection closed successfully");
                    fileTransferServiceInstance = null;
                } catch (Exception e) {
                    logger.error("Error closing SFTP connection", e);
                }
            }
        }
        
        // Ensure the DataSource is also closed
        DatabaseConfig.closeDataSource();
        
        logger.info("All resources have been released");
    }

    /**
     * Save the test case key to shared memory
     */
    private void saveTestCaseKey() {
        try {
            // Write the test case key to shared memory
            SharedMemoryUtil.writeValue(TEST_CASE_KEY_NAME, testCaseKey);
            System.out.println("TestCaseKey saved to shared memory: " + testCaseKey);
        } catch (IOException e) {
            System.err.println("Failed to save TestCaseKey to shared memory: " + e.getMessage());
            e.printStackTrace();
        }
    }
}