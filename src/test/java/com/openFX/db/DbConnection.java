package com.openFX.db;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnection {
    private static Connection conn;
    private static final Logger logger = LoggerFactory.getLogger(DbConnection.class);
    private static final Dotenv dotenv = Dotenv.load();  // Load environment variables from .env file

    public static Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            try {
                // Get credentials from the .env file
                String dbUrl = dotenv.get("DB_URL");
                String username = dotenv.get("DB_USERNAME");
                String password = dotenv.get("DB_PASSWORD");

                if (dbUrl == null || username == null || password == null) {
                    logger.error("Database credentials are missing in the .env file.");
                    throw new SQLException("Database credentials are missing.");
                }

                logger.info("Connecting to PostgreSQL database...");
                conn = DriverManager.getConnection(dbUrl, username, password);
                logger.info("Database connection established.");
            } catch (SQLException e) {
                logger.error("Error establishing database connection", e);
                throw e;
            }
        }
        return conn;
    }

    public static void closeConnection() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            try {
                conn.close();
                logger.info("Database connection closed.");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
                throw e;
            }
        }
    }
}


