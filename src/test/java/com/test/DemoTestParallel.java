package com.test;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import com.intuit.karate.core.ScenarioResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;

public class DemoTestParallel {

    private static final String TEST_RUN_KEY = "12345"; // Replace with actual testRunKey

    @Test
    void testParallel() {
        Results results = Runner.path("classpath:demo")
                .outputCucumberJson(true)
                .karateEnv("demo")
                .parallel(5);

        generateReport(results.getReportDir());

        // Loop through each scenario result and send API request with unique payload
        for (ScenarioResult scenarioResult : results.getScenarioResults()) {
            String scenarioName = scenarioResult.getScenario().getName();
            String scenarioStatus = scenarioResult.isFailed() ? "Fail" : "Pass";
            long executionTime = scenarioResult.getDurationMillis();

            // Extract testCaseKey (last word in scenarioName)
            String testCaseKey = extractTestCaseKey(scenarioName);

            // Prepare dynamic API request payload for each test case
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("status", scenarioStatus); // Pass/Fail based on scenario result
            requestBody.put("environment", "QA");
            requestBody.put("executionTime", executionTime); // Dynamic execution time

            // Include error message if the scenario failed
            if (scenarioResult.isFailed()) {
                String errorMessage = scenarioResult.getErrorMessage();
                requestBody.put("comment", "Test failed: " + errorMessage); // Include error message in the comment
            } else {
                requestBody.put("comment", "Test executed for scenario: " + scenarioName); // Success comment
            }

            // Prepare variables to pass to Karate
            Map<String, Object> karateVars = new HashMap<>();
            karateVars.put("testRunKey", TEST_RUN_KEY);
            karateVars.put("testCaseKey", testCaseKey);
            karateVars.put("requestBody", requestBody);  // Unique payload per test case

            // Send API request for each scenario with different request body
            karate.call("classpath:api/sendResult.feature", karateVars);  // Executes with dynamic payload
        }

        // Assert no failures
        assertTrue(results.getFailCount() == 0, results.getErrorMessages());
    }

    public static void generateReport(String karateOutputPath) {
        // Generate report logic...
    }

    public static String extractTestCaseKey(String scenarioName) {
    	// Trim to remove leading/trailing spaces
        String trimmedScenario = scenarioName.trim();

        // Regex pattern for test case ID: DSMPTEAM-T followed by 4 digits
        Pattern pattern = Pattern.compile("(DSMPTEAM-T\\d{4})");
        Matcher matcher = pattern.matcher(trimmedScenario);

        // Extract test case ID if found
        return matcher.find() ? matcher.group(1) : "UNKNOWN-TC"; 
        // Extract the test case ID from the scenario name...
    }
}

