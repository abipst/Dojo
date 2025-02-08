package com.openFX.db;

import com.openFX.config.DatabaseConfig;
import com.openFX.utils.QueryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbServiceImpl implements DbService {
    private static final Logger logger = LoggerFactory.getLogger(DbServiceImpl.class);
    private final DataSource dataSource;
    private Connection currentConnection;

    public DbServiceImpl() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    private Connection getConnection() throws SQLException {
        if (currentConnection == null || currentConnection.isClosed()) {
            currentConnection = dataSource.getConnection();
        }
        return currentConnection;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String queryKey, Object... params) {
        String query = QueryLoader.getQuery(queryKey);
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            setParameters(stmt, params);
            logger.info("Executing query: {}", query);

            try (ResultSet rs = stmt.executeQuery()) {
                results = mapResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("Error executing query: {}", query, e);
            throw new DatabaseException("Database query failed", e);
        }
        return results;
    }

    @Override
    public int executeUpdate(String queryKey, Object... params) {
        String query = QueryLoader.getQuery(queryKey);
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            setParameters(stmt, params);
            logger.info("Executing update: {}", query);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error executing update: {}", query, e);
            throw new DatabaseException("Database update failed", e);
        }
    }

    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnName(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }

    @Override
    public void close() throws Exception {
        if (currentConnection != null && !currentConnection.isClosed()) {
            try {
                currentConnection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
                throw new DatabaseException("Failed to close database connection", e);
            }
        }
    }
}
