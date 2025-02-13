package com.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class QueryLoader {
    private static final Logger logger = LoggerFactory.getLogger(QueryLoader.class);
    private static final String QUERY_FILE = "/queries.properties";
    private static final Properties properties = new Properties();

    static {
        try (InputStream inputStream = QueryLoader.class.getResourceAsStream(QUERY_FILE)) {
            if (inputStream != null) {
                properties.load(inputStream);
                logger.info("Loaded queries from properties file.");
            } else {
                logger.error("Query properties file not found: {}", QUERY_FILE);
            }
        } catch (IOException e) {
            logger.error("Error loading queries from properties file", e);
        }
    }

    public static String getQuery(String key) {
        var query = properties.getProperty(key);
        if (query == null) {
            logger.warn("Query key not found: {}", key);
        }
        return query;
    }
}

