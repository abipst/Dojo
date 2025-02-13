package com.test;

import com.db.DbQueryService;
import com.utils.LoggerUtil;

import org.slf4j.Logger;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

public class DbSelectUsersStep {
    private static final Logger logger = LoggerUtil.getLogger(DbSelectUsersStep.class);
    private static DbQueryService dbQueryService;

    @BeforeAll
    public static void setUp() {
        try {
            dbQueryService = new DbQueryService();
            logger.info("DB connection setup successfully");
        } catch (SQLException e) {
            logger.error("Error setting up DB connection", e);
        }
    }

    // This method fetches all users from the database and returns them as a List of
    // Maps
    public static List<Map<String, Object>> selectAllUsers() {
        if (dbQueryService == null) {
            logger.error("@BeforeAll method not called or dbQueryService not initialized");
            throw new IllegalStateException("DB connection not initialized");
        }
        try {
            ResultSet rs = dbQueryService.executeQuery("SELECT_ALL_USERS");
            List<Map<String, Object>> resultList = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("username", rs.getString("username"));
                row.put("email", rs.getString("email"));
                resultList.add(row);
            }
            return resultList;
        } catch (SQLException e) {
            logger.error("Error selecting users from the database", e);
            return Collections.emptyList();
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
