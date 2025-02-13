package com.karate;

import com.config.DatabaseConfig;
import com.intuit.karate.junit5.Karate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseKarateTest {
    
    @BeforeAll
    public static void setUp() {
        // Initialize any common test resources
        // DatabaseConfig is already initialized statically
    }

    @AfterAll
    public static void tearDown() {
        DatabaseConfig.closeDataSource();
    }

    // Common configuration for Karate tests
    public Karate runTest() {
        return Karate.run(getClass().getSimpleName().toLowerCase());
    }
}
