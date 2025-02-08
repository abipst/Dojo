package com.openFX.karate;

import com.openFX.db.DbService;
import com.openFX.db.DbServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class UserSteps {
    private static final Logger logger = LoggerFactory.getLogger(UserSteps.class);

    public static List<Map<String, Object>> getAllUsers() {
        logger.info("Fetching all users from database");
        try (DbService dbService = new DbServiceImpl()) {
            return dbService.executeQuery("SELECT_ALL_USERS");
        } catch (Exception e) {
            logger.error("Error while fetching all users", e);
            throw new RuntimeException("Failed to fetch users", e);
        }
    }

    public static List<Map<String, Object>> getUserById(Long userId) {
        logger.info("Fetching user with ID: {}", userId);
        try (DbService dbService = new DbServiceImpl()) {
            return dbService.executeQuery("SELECT_USER_BY_ID", userId);
        } catch (Exception e) {
            logger.error("Error while fetching user by ID: {}", userId, e);
            throw new RuntimeException("Failed to fetch user", e);
        }
    }

    public static int createUser(String username, String email) {
        logger.info("Creating new user with username: {}", username);
        try (DbService dbService = new DbServiceImpl()) {
            return dbService.executeUpdate("INSERT_USER", username, email);
        } catch (Exception e) {
            logger.error("Error while creating user: {}", username, e);
            throw new RuntimeException("Failed to create user", e);
        }
    }
}
