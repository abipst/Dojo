package com.openFX.karate;


import com.intuit.karate.*;
import com.openFX.db.DbQueryService;
import com.openFX.utils.LoggerUtil;
import org.slf4j.Logger;
import java.sql.*;

public class DbSelectUsersStep {
    private static final Logger logger = LoggerUtil.getLogger(DbSelectUsersStep.class);
    private static DbQueryService dbQueryService;

    @Karate.BeforeAll
    public static void setUp() {
        try {
            dbQueryService = new DbQueryService();
        } catch (SQLException e) {
            logger.error("Error setting up DB connection", e);
        }
    }

    @Karate.Test
    public static ResultSet selectAllUsers() {
        try {
            return dbQueryService.executeQuery("SELECT_ALL_USERS");
        } catch (SQLException e) {
            logger.error("Error selecting users from the database", e);
            return null;  // Return null if query fails
        }
    }

    @Karate.AfterAll
    public static void tearDown() {
        try {
            DbConnection.closeConnection();
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }
}
