// src/test/java/openfx/db/DbQueryService.java
package com.openFX.db;

import com.openFX.utils.QueryLoader;
import com.openFX.utils.LoggerUtil;
import org.slf4j.Logger;

import java.sql.*;

public class DbQueryService {
    private static final Logger logger = LoggerUtil.getLogger(DbQueryService.class);
    private Connection connection;

    public DbQueryService() throws SQLException {
        this.connection = DbConnection.getConnection();
    }

    public ResultSet executeQuery(String queryKey) throws SQLException {
        var query = QueryLoader.getQuery(queryKey);
        logger.info("Executing query: {}", query);
        try (var stmt = connection.createStatement(); var rs = stmt.executeQuery(query)) {
            return rs;
        } catch (SQLException e) {
            logger.error("Error executing query: {}", query, e);
            throw e;
        }
    }

    public int executeUpdate(String queryKey, String... params) throws SQLException {
        var query = QueryLoader.getQuery(queryKey);
        if (params != null && params.length > 0) {
            query = String.format(query, (Object[]) params);
        }
        logger.info("Executing update: {}", query);
        try (var stmt = connection.createStatement()) {
            return stmt.executeUpdate(query);
        } catch (SQLException e) {
            logger.error("Error executing update: {}", query, e);
            throw e;
        }
    }
}
