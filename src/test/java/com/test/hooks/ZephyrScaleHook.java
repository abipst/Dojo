package com.test.hooks;

import com.intuit.karate.Results;
import com.intuit.karate.core.Scenario;
import com.intuit.karate.core.ScenarioResult;
import com.test.zephyr.ZephyrScaleClient;
import com.test.zephyr.ZephyrScaleClient.TestResult;
import com.intuit.karate.core.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ZephyrScaleHook {
    private static final Logger logger = LoggerFactory.getLogger(ZephyrScaleHook.class);
    private static final Map<String, String> scenarioToTestCaseMap = new ConcurrentHashMap<>();
    private final ZephyrScaleClient zephyrClient;

    public ZephyrScaleHook(String accessToken, String projectKey) {
        this.zephyrClient = new ZephyrScaleClient(accessToken, projectKey);
    }

    public void beforeScenario(Scenario scenario) {
        try {
            // Option 1: Create test case from automation
            if (shouldCreateTestCase(scenario)) {
                List<String> steps = extractSteps(scenario);
                String testCaseKey = zephyrClient.createTestCase(
                    scenario.getName(),
                    getScenarioObjective(scenario),
                    steps
                );
                scenarioToTestCaseMap.put(scenario.getUniqueId(), testCaseKey);
            }
            
            // Option 2: Link existing test case
            String testCaseKey = getTestCaseKeyFromTag(scenario);
            if (testCaseKey != null) {
                zephyrClient.linkTestCaseToAutomation(testCaseKey, scenario.getUniqueId());
                scenarioToTestCaseMap.put(scenario.getUniqueId(), testCaseKey);
            }
        } catch (Exception e) {
            logger.error("Error in Zephyr Scale before scenario hook", e);
        }
    }

    public void afterScenario(ScenarioResult scenarioResult) {
        try {
            String testCaseKey = scenarioToTestCaseMap.get(scenarioResult.getScenario().getUniqueId());
            if (testCaseKey != null) {
                TestResult result = new TestResult(
                    convertKarateStatus(scenarioResult.isFailed() ? "FAILED" : "PASSED"),
                    System.getProperty("karate.env", "default"),
                    generateExecutionComment(scenarioResult),
                    System.getProperty("user.name"),
                    collectEvidences(scenarioResult)
                );
                zephyrClient.publishTestResult(testCaseKey, result);
            }
        } catch (Exception e) {
            logger.error("Error in Zephyr Scale after scenario hook", e);
        }
    }

    public void afterFeature(Results results) {
        try {
            List<ScenarioResult> scenarioResults = results.getScenarioResults().collect(Collectors.toList());
            if (!scenarioResults.isEmpty()) {
                String featureName = scenarioResults.get(0).getScenario().getFeature().getName();
                String cycleName = "Automated Run - " + featureName + " - " + System.currentTimeMillis();
                
                List<String> testCaseKeys = new ArrayList<>(scenarioToTestCaseMap.values());
                zephyrClient.createTestCycle(cycleName, "Automated test execution", testCaseKeys);
            }
        } catch (Exception e) {
            logger.error("Error in Zephyr Scale after feature hook", e);
        }
    }

    private boolean shouldCreateTestCase(Scenario scenario) {
        return scenario.getTags().contains("@create-zephyr-tc");
    }

    private String getTestCaseKeyFromTag(Scenario scenario) {
        return scenario.getTags().stream()
            .map(Tag::getName)
            .filter(tag -> tag.startsWith("@TestCaseKey="))
            .map(tag -> tag.substring("@TestCaseKey=".length()))
            .findFirst()
            .orElse(null);
    }

    private List<String> extractSteps(Scenario scenario) {
        List<String> steps = new ArrayList<>();
        // Here you would implement the logic to extract steps from your scenario
        // This might involve parsing the scenario's step definitions
        return steps;
    }

    private String getScenarioObjective(Scenario scenario) {
        return scenario.getDescription() != null ? 
            scenario.getDescription() : "Automated test case for " + scenario.getName();
    }

    private String convertKarateStatus(String karateStatus) {
        return switch (karateStatus.toUpperCase()) {
            case "PASSED" -> "Pass";
            case "FAILED" -> "Fail";
            default -> "Not Executed";
        };
    }

    private String generateExecutionComment(ScenarioResult scenarioResult) {
        long durationMillis = System.currentTimeMillis() - scenarioResult.getStartTime();
        return String.format("Execution completed with status: %s\nDuration: %d ms",
            scenarioResult.isFailed() ? "FAILED" : "PASSED",
            durationMillis);
    }

    private List<String> collectEvidences(ScenarioResult scenarioResult) {
        ArrayList<String> evidences = new ArrayList<>();
        
        // Add error message if scenario failed
        if (scenarioResult.isFailed() && scenarioResult.getError() != null) {
            evidences.add("Error: " + scenarioResult.getError().getMessage());
        }
        
        return evidences;
    }
}