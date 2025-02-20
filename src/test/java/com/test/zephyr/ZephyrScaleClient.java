package com.test.zephyr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class ZephyrScaleClient {
    private static final Logger logger = LoggerFactory.getLogger(ZephyrScaleClient.class);
    private static final String API_BASE_URL = "https://api.zephyrscale.smartbear.com/v2";
    private final String accessToken;
    private final String projectKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ZephyrScaleClient(String accessToken, String projectKey) {
        this.accessToken = accessToken;
        this.projectKey = projectKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // Approach 1: Create test case from automation
    public String createTestCase(String name, String objective, List<String> steps) throws IOException, InterruptedException {
        Map<String, Object> testCase = new HashMap<>();
        testCase.put("projectKey", projectKey);
        testCase.put("name", name);
        testCase.put("objective", objective);
        testCase.put("status", "Approved");
        testCase.put("priority", "Normal");
        
        List<Map<String, String>> testSteps = new ArrayList<>();
        for (String step : steps) {
            Map<String, String> testStep = new HashMap<>();
            testStep.put("description", step);
            testSteps.add(testStep);
        }
        testCase.put("steps", testSteps);

        String response = sendRequest("POST", "/testcases", testCase);
        Map<?, ?> responseMap = objectMapper.readValue(response, Map.class);
        return (String) responseMap.get("key");
    }

    // Approach 2: Link existing test case to automation
    public void linkTestCaseToAutomation(String testCaseKey, String automationKey) throws IOException, InterruptedException {
        Map<String, Object> link = new HashMap<>();
        link.put("testCaseKey", testCaseKey);
        link.put("automationKey", automationKey);
        
        sendRequest("POST", "/automations/testcases/links", link);
    }

    // Publish test results
    public void publishTestResult(String testCaseKey, TestResult result) throws IOException, InterruptedException {
        Map<String, Object> testExecution = new HashMap<>();
        testExecution.put("projectKey", projectKey);
        testExecution.put("testCaseKey", testCaseKey);
        testExecution.put("statusName", result.getStatus());
        testExecution.put("environmentName", result.getEnvironment());
        testExecution.put("comment", result.getComment());
        testExecution.put("executedBy", result.getExecutedBy());
        
        if (!result.getEvidences().isEmpty()) {
            testExecution.put("evidences", result.getEvidences());
        }

        sendRequest("POST", "/testexecutions", testExecution);
    }

    // Create test cycle
    public String createTestCycle(String name, String description, List<String> testCaseKeys) throws IOException, InterruptedException {
        Map<String, Object> testCycle = new HashMap<>();
        testCycle.put("projectKey", projectKey);
        testCycle.put("name", name);
        testCycle.put("description", description);
        testCycle.put("testCaseKeys", testCaseKeys);

        String response = sendRequest("POST", "/testcycles", testCycle);
        Map<?, ?> responseMap = objectMapper.readValue(response, Map.class);
        return (String) responseMap.get("key");
    }

    private String sendRequest(String method, String endpoint, Object body) throws IOException, InterruptedException {
        String jsonBody = objectMapper.writeValueAsString(body);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + endpoint))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            logger.error("Zephyr Scale API error: {} - {}", response.statusCode(), response.body());
            throw new IOException("Zephyr Scale API error: " + response.statusCode());
        }
        
        return response.body();
    }

    // Helper class for test results
    public static class TestResult {
        private final String status;
        private final String environment;
        private final String comment;
        private final String executedBy;
        private final List<String> evidences;

        public TestResult(String status, String environment, String comment, String executedBy, List<String> evidences) {
            this.status = status;
            this.environment = environment;
            this.comment = comment;
            this.executedBy = executedBy;
            this.evidences = evidences;
        }

        public String getStatus() { return status; }
        public String getEnvironment() { return environment; }
        public String getComment() { return comment; }
        public String getExecutedBy() { return executedBy; }
        public List<String> getEvidences() { return evidences; }
    }
}