package com.openFX.karate;

import com.openFX.db.DbQueryService;
import com.openFX.utils.LoggerUtil;
import org.slf4j.Logger;
import java.sql.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class DbSelectUsersStep {
    private static final Logger logger = LoggerUtil.getLogger(DbSelectUsersStep.class);
    private static DbQueryService dbQueryService;

    @BeforeAll
    public static void setUp() {
        try {
            dbQueryService = new DbQueryService();
        } catch (SQLException e) {
            logger.error("Error setting up DB connection", e);
        }
    }

    @Test
    public static ResultSet selectAllUsers() {
        try {
            return dbQueryService.executeQuery("SELECT_ALL_USERS");
        } catch (SQLException e) {
            logger.error("Error selecting users from the database", e);
            return null; // Return null if query fails
        }
    }

    @AfterAll
    public static void tearDown() {
        try {
            dbQueryService.closeConnection();
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }
}
