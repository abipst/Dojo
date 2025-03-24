package com.test;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Utility to generate and email custom HTML reports based on Karate test results.
 * Follows SOLID principles with separate responsibilities for report generation and email sending.
 */
public class KarateEmailReporter {
    private static final Logger logger = LoggerFactory.getLogger(KarateEmailReporter.class);

    public static void main(String[] args) {
        try {
            var config = parseCommandLineArgs(args);
            var reportGenerator = new KarateReportGenerator();
            var emailSender = new EmailSender(new SmtpConfig());
            
            logger.info("Starting Karate email report generation");
            
            // Generate custom HTML report
            var reportData = reportGenerator.parseKarateSummary(config.summaryJsonPath());
            var htmlReport = reportGenerator.generateHtmlReport(reportData);
            
            // Save the HTML report to file
            saveReportToFile(htmlReport, config.outputHtmlPath());
            
            // Send email with report attached
            var reportFile = new File(config.outputHtmlPath());
            emailSender.sendReportEmail(reportFile, reportData.isTestsPassed());
            
            logger.info("Karate email report generated and sent successfully");
            
        } catch (ReportGenerationException e) {
            logger.error("Error generating report: {}", e.getMessage(), e);
            System.exit(1);
        } catch (EmailException e) {
            logger.error("Error sending email: {}", e.getMessage(), e);
            System.exit(2);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(3);
        }
    }
    
    private static void saveReportToFile(String htmlReport, String outputPath) throws ReportGenerationException {
        try {
            Path outputFilePath = Paths.get(outputPath);
            // Create directories if they don't exist
            Files.createDirectories(outputFilePath.getParent());
            Files.writeString(outputFilePath, htmlReport, StandardCharsets.UTF_8);
            logger.info("Report saved to {}", outputPath);
        } catch (IOException e) {
            throw new ReportGenerationException("Failed to save report to file: " + e.getMessage(), e);
        }
    }
    
    private static ReporterConfig parseCommandLineArgs(String[] args) {
        // Default paths
        String summaryJsonPath = "target/karate-reports/karate-summary.json";
        String outputHtmlPath = "target/karate-reports/email-report.html";
        
        if (args.length >= 1) {
            summaryJsonPath = args[0];
        }
        if (args.length >= 2) {
            outputHtmlPath = args[1];
        }
        
        return new ReporterConfig(summaryJsonPath, outputHtmlPath);
    }
    
    /**
     * Record containing configuration for the reporter
     */
    record ReporterConfig(String summaryJsonPath, String outputHtmlPath) {}
}

/**
 * Responsible for parsing Karate summary data and generating HTML reports.
 * Follows Single Responsibility Principle.
 */
class KarateReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(KarateReportGenerator.class);
    
    /**
     * Parses Karate summary JSON and extracts relevant test data.
     */
   /**
 * Parses Karate summary JSON and extracts relevant test data.
 * Enhanced to support Karate 1.4.1 with detailed error extraction from feature files.
 */
public ReportData parseKarateSummary(String summaryJsonPath) throws ReportGenerationException {
    try {
        logger.info("Parsing Karate summary from {}", summaryJsonPath);
        
        JSONParser parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
        JSONObject summary;
        
        try (var reader = new FileReader(summaryJsonPath)) {
            summary = (JSONObject) parser.parse(reader);
        }
        
        // Get the directory containing the summary file for accessing feature files
        Path summaryPath = Paths.get(summaryJsonPath);
        Path reportDir = summaryPath.getParent();
        
        // Extract feature data
        JSONArray featureResults = (JSONArray) summary.get("featureSummary");
        
        var featureStats = StreamSupport.stream(featureResults.spliterator(), false)
            .map(feature -> (JSONObject) feature)
            .map(feature -> {
                // Extract feature details
                String featureName = (String) feature.getOrDefault("name", "Unknown");
                boolean failed = (boolean) feature.getOrDefault("failed", false);
                String featurePath = (String) feature.getOrDefault("relativePath", "");
                
                // Extract detailed feature errors from the feature-specific JSON file
                List<ScenarioData> scenarios = extractScenariosWithDetailedErrors(reportDir, featurePath, failed);
                
                // If no scenarios were found in feature files, fall back to summary data
                if (scenarios.isEmpty()) {
                    JSONArray scenariosArray = (JSONArray) feature.getOrDefault("scenarios", new JSONArray());
                    
                    scenarios = StreamSupport.stream(scenariosArray.spliterator(), false)
                        .map(scenario -> (JSONObject) scenario)
                        .map(scenario -> {
                            String scenarioName = (String) scenario.getOrDefault("name", "Unknown");
                            boolean scenarioFailed = (boolean) scenario.getOrDefault("failed", false);
                            String errorMessage = scenarioFailed ? extractErrorMessage(scenario) : null;
                            
                            return new ScenarioData(
                                scenarioName,
                                scenarioFailed,
                                errorMessage
                            );
                        })
                        .collect(Collectors.toList());
                }
                
                return new FeatureData(
                    featureName,
                    failed,
                    ((Number) feature.getOrDefault("scenarioCount", 0)).intValue(),
                    ((Number) feature.getOrDefault("passedCount", 0)).intValue(),
                    ((Number) feature.getOrDefault("failedCount", 0)).intValue(),
                    ((Number) feature.getOrDefault("durationMillis", 0)).longValue(),
                    scenarios
                );
            })
            .collect(Collectors.toList());
        
        int passedFeatures = (int) featureStats.stream().filter(f -> !f.failed).count();
        int failedFeatures = (int) featureStats.stream().filter(f -> f.failed).count();
        
        // Extract scenario stats
        int totalScenarios = ((Number) summary.getOrDefault("scenarioCount", 0)).intValue();
        int passedScenarios = ((Number) summary.getOrDefault("scenarioPassed", 0)).intValue();
        int failedScenarios = ((Number) summary.getOrDefault("scenarioFailed", 0)).intValue();
        
        long totalDuration = ((Number) summary.getOrDefault("durationMillis", 0)).longValue();
        
        return new ReportData(
            passedFeatures,
            failedFeatures,
            passedScenarios,
            failedScenarios,
            totalDuration,
            featureStats
        );
        
    } catch (IOException e) {
        throw new ReportGenerationException("Failed to read summary file: " + e.getMessage(), e);
    } catch (ParseException e) {
        throw new ReportGenerationException("Failed to parse JSON: " + e.getMessage(), e);
    } catch (Exception e) {
        throw new ReportGenerationException("Unexpected error while parsing summary: " + e.getMessage(), e);
    }
}

/**
 * Extracts scenarios with detailed error information from feature-specific JSON files.
 * In Karate 1.4.1, these files contain more detailed error information.
 */
private List<ScenarioData> extractScenariosWithDetailedErrors(Path reportDir, String featurePath, boolean featureFailed) {
    List<ScenarioData> scenarios = new ArrayList<>();
    
    try {
        // Generate the expected feature file name pattern
        // In 1.4.1, feature files are named like: features.some.package.feature-name.json
        String featureName = featurePath;
        // Remove any directory path and file extension
        if (featureName.contains("/")) {
            featureName = featureName.substring(featureName.lastIndexOf('/') + 1);
        }
        if (featureName.endsWith(".feature")) {
            featureName = featureName.substring(0, featureName.lastIndexOf('.'));
        }
        
        // Look for feature files that match the pattern
        try (Stream<Path> paths = Files.list(reportDir)) {
            List<Path> featureFiles = paths
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.endsWith(".json") && 
                           fileName.contains(featureName) &&
                           fileName.startsWith("features.");
                })
                .collect(Collectors.toList());
            
            if (!featureFiles.isEmpty()) {
                // Parse the first matching feature file (there should be only one)
                Path featureFile = featureFiles.get(0);
                logger.info("Found detailed feature file: {}", featureFile);
                
                JSONParser parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
                JSONObject featureJson;
                
                try (var reader = new FileReader(featureFile.toFile())) {
                    Object parsedJson = parser.parse(reader);
                    
                    // Handle both array and object formats
                    if (parsedJson instanceof JSONArray) {
                        // If the root is an array, the first element might be our feature
                        JSONArray jsonArray = (JSONArray) parsedJson;
                        if (!jsonArray.isEmpty()) {
                            Object firstElement = jsonArray.get(0);
                            if (firstElement instanceof JSONObject) {
                                featureJson = (JSONObject) firstElement;
                            } else {
                                logger.warn("Feature file has unexpected format: root array does not contain JSONObject");
                                return scenarios; // Return empty list
                            }
                        } else {
                            logger.warn("Feature file contains empty array");
                            return scenarios; // Return empty list
                        }
                    } else if (parsedJson instanceof JSONObject) {
                        // Expected case - root is an object
                        featureJson = (JSONObject) parsedJson;
                    } else {
                        logger.warn("Feature file has unexpected format: root is neither JSONArray nor JSONObject");
                        return scenarios; // Return empty list
                    }
                }
                
                // Extract scenarios
                JSONArray scenariosArray = (JSONArray) featureJson.getOrDefault("elements", new JSONArray());
                for (Object scenarioObj : scenariosArray) {
                    JSONObject scenario = (JSONObject) scenarioObj;
                    // Check if it's a scenario (not background)
                    String type = (String) scenario.getOrDefault("type", "");
                    if (type.equals("scenario")) {
                        String scenarioName = (String) scenario.getOrDefault("name", "Unknown");
                        boolean failed = false;
                        StringBuilder errorMessage = new StringBuilder();
                        
                        // Check steps for failures
                        JSONArray steps = (JSONArray) scenario.getOrDefault("steps", new JSONArray());
                        for (Object stepObj : steps) {
                            JSONObject step = (JSONObject) stepObj;
                            JSONObject result = (JSONObject) step.getOrDefault("result", new JSONObject());
                            String status = (String) result.getOrDefault("status", "");
                            
                            if (status.equals("failed")) {
                                failed = true;
                                String keyword = (String) step.getOrDefault("keyword", "");
                                String name = (String) step.getOrDefault("name", "");
                                String errorMsg = (String) result.getOrDefault("error_message", "Unknown error");
                                
                                errorMessage.append(keyword).append(" ").append(name).append("\n")
                                           .append("Error: ").append(errorMsg).append("\n\n");
                            }
                        }
                        
                        scenarios.add(new ScenarioData(
                            scenarioName,
                            failed,
                            failed ? errorMessage.toString() : null
                        ));
                    }
                }
            }
        }
    } catch (Exception e) {
        logger.warn("Failed to extract detailed errors from feature files: {}", e.getMessage());
        // Continue with summary data if feature file parsing fails
    }
    
    return scenarios;
}

/**
 * Extracts the error message from a failed scenario in the summary JSON
 * Used as a fallback when detailed feature files are not available
 */
private String extractErrorMessage(JSONObject scenario) {
    try {
        StringBuilder errorMsg = new StringBuilder();
        
        // Try to get error message from error property first
        if (scenario.containsKey("error")) {
            return scenario.get("error").toString();
        }
        
        // If no direct error, look in steps
        JSONArray steps = (JSONArray) scenario.getOrDefault("steps", new JSONArray());
        for (Object stepObj : steps) {
            JSONObject step = (JSONObject) stepObj;
            if ((boolean) step.getOrDefault("failed", false)) {
                var result = (JSONObject) step.get("result");
                if (result != null && result.containsKey("errorMessage")) {
                    errorMsg.append(result.get("errorMessage"));
                    break;
                }
            }
        }
        
        return errorMsg.length() > 0 ? errorMsg.toString() : "Unknown error";
    } catch (Exception e) {
        logger.warn("Failed to extract error message: {}", e.getMessage());
        return "Error details not available";
    }
}
    
    /**
     * Generates HTML report from the parsed test data.
     */
    public String generateHtmlReport(ReportData data) {
        logger.info("Generating HTML report from parsed data");
        
        String reportTitle = "Karate Test Execution Report";
        String executionDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        int totalFeatures = data.passedFeatures() + data.failedFeatures();
        double passRate = totalFeatures > 0 ? (data.passedFeatures() * 100.0 / totalFeatures) : 0;
        
        int totalScenarios = data.passedScenarios() + data.failedScenarios();
        double scenarioPassRate = totalScenarios > 0 ? (data.passedScenarios() * 100.0 / totalScenarios) : 0;
        
        // Use StringBuilder for more efficient string concatenation
        var html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html lang='en'>
            <head>
              <meta charset='UTF-8'>
              <meta name='viewport' content='width=device-width, initial-scale=1.0'>
              <title>%s</title>
              <style>
                body { font-family: Arial, sans-serif; margin: 20px; color: #333; }
                .header { text-align: center; margin-bottom: 30px; }
                .summary-box { border: 1px solid #ddd; border-radius: 5px; padding: 15px; margin-bottom: 20px; }
                .summary-title { font-size: 18px; font-weight: bold; margin-bottom: 10px; }
                .stat-row { display: flex; justify-content: space-between; margin-bottom: 5px; }
                .stat-label { font-weight: bold; }
                .pass { color: green; }
                .fail { color: red; }
                .warning { color: orange; }
                table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }
                th { background-color: #f2f2f2; }
                tr.failed { background-color: #ffe6e6; }
                tr.passed { background-color: #e6ffe6; }
                .errors-section { margin-top: 30px; }
                .error-title { font-size: 16px; font-weight: bold; margin-top: 15px; }
                .error-message { 
                    background-color: #fff0f0; 
                    padding: 10px; 
                    border-left: 4px solid #d9534f;
                    font-family: monospace;
                    white-space: pre-wrap;
                    margin-top: 5px;
                    margin-bottom: 15px;
                    overflow-x: auto;
                }
                .scenario-list { margin-left: 20px; }
                .feature-error { 
                    background-color: #f8f9fa;
                    border: 1px solid #eaecef;
                    border-radius: 3px;
                    padding: 15px;
                    margin-bottom: 20px;
                }
                .toggle-btn {
                    background-color: #f8f9fa;
                    border: 1px solid #ddd;
                    padding: 5px 10px;
                    cursor: pointer;
                    font-size: 14px;
                    border-radius: 3px;
                }
                .hidden { display: none; }
              </style>
              <script>
                function toggleErrorDetails(id) {
                    var element = document.getElementById(id);
                    if (element.classList.contains('hidden')) {
                        element.classList.remove('hidden');
                    } else {
                        element.classList.add('hidden');
                    }
                }
              </script>
            </head>
            <body>
              <div class='header'>
                <h1>%s</h1>
                <p>Generated on: %s</p>
              </div>
            """.formatted(reportTitle, reportTitle, executionDate));
        
        // Overall Summary
        html.append("""
              <div class='summary-box'>
                <div class='summary-title'>Test Execution Summary</div>
                <div class='stat-row'>
                  <span class='stat-label'>Overall Status:</span>
            """);
        
        if (data.isTestsPassed()) {
            html.append("      <span class='pass'>PASSED</span>\n");
        } else {
            html.append("      <span class='fail'>FAILED</span>\n");
        }
        
        html.append("""
                </div>
                <div class='stat-row'>
                  <span class='stat-label'>Execution Time:</span>
                  <span>%d ms</span>
                </div>
              </div>
            """.formatted(data.totalDuration()));
        
        // Feature Summary
        html.append("""
              <div class='summary-box'>
                <div class='summary-title'>Feature Summary</div>
                <div class='stat-row'>
                  <span class='stat-label'>Total Features:</span>
                  <span>%d</span>
                </div>
                <div class='stat-row'>
                  <span class='stat-label'>Passed Features:</span>
                  <span class='pass'>%d</span>
                </div>
                <div class='stat-row'>
                  <span class='stat-label'>Failed Features:</span>
            """.formatted(totalFeatures, data.passedFeatures()));
        
        if (data.failedFeatures() > 0) {
            html.append("      <span class='fail'>").append(data.failedFeatures()).append("</span>\n");
        } else {
            html.append("      <span>").append(data.failedFeatures()).append("</span>\n");
        }
        
        html.append("""
                </div>
                <div class='stat-row'>
                  <span class='stat-label'>Pass Rate:</span>
            """);
        
        if (passRate >= 95) {
            html.append("      <span class='pass'>").append(String.format("%.2f", passRate)).append("%</span>\n");
        } else if (passRate >= 80) {
            html.append("      <span class='warning'>").append(String.format("%.2f", passRate)).append("%</span>\n");
        } else {
            html.append("      <span class='fail'>").append(String.format("%.2f", passRate)).append("%</span>\n");
        }
        
        html.append("""
                </div>
              </div>
            """);
        
        // Scenario Summary
        html.append("""
              <div class='summary-box'>
                <div class='summary-title'>Scenario Summary</div>
                <div class='stat-row'>
                  <span class='stat-label'>Total Scenarios:</span>
                  <span>%d</span>
                </div>
                <div class='stat-row'>
                  <span class='stat-label'>Passed Scenarios:</span>
                  <span class='pass'>%d</span>
                </div>
                <div class='stat-row'>
                  <span class='stat-label'>Failed Scenarios:</span>
            """.formatted(totalScenarios, data.passedScenarios()));
        
        if (data.failedScenarios() > 0) {
            html.append("      <span class='fail'>").append(data.failedScenarios()).append("</span>\n");
        } else {
            html.append("      <span>").append(data.failedScenarios()).append("</span>\n");
        }
        
        html.append("""
                </div>
                <div class='stat-row'>
                  <span class='stat-label'>Scenario Pass Rate:</span>
            """);
        
        if (scenarioPassRate >= 95) {
            html.append("      <span class='pass'>").append(String.format("%.2f", scenarioPassRate)).append("%</span>\n");
        } else if (scenarioPassRate >= 80) {
            html.append("      <span class='warning'>").append(String.format("%.2f", scenarioPassRate)).append("%</span>\n");
        } else {
            html.append("      <span class='fail'>").append(String.format("%.2f", scenarioPassRate)).append("%</span>\n");
        }
        
        html.append("""
                </div>
              </div>
            """);
        
        // Feature Details Table
        html.append("""
              <div class='summary-box'>
                <div class='summary-title'>Feature Details</div>
                <table>
                  <thead>
                    <tr>
                      <th>Feature</th>
                      <th>Status</th>
                      <th>Scenarios</th>
                      <th>Passed</th>
                      <th>Failed</th>
                      <th>Duration</th>
                    </tr>
                  </thead>
                  <tbody>
            """);
        
        for (FeatureData feature : data.features()) {
            String rowClass = feature.failed ? "failed" : "passed";
            
            html.append("""
                    <tr class='%s'>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%d</td>
                      <td>%d</td>
                      <td>%d</td>
                      <td>%d ms</td>
                    </tr>
            """.formatted(
                rowClass,
                feature.name,
                feature.failed ? "<span class='fail'>FAILED</span>" : "<span class='pass'>PASSED</span>",
                feature.scenarioCount,
                feature.passedCount,
                feature.failedCount,
                feature.durationMillis
            ));
        }
        
        html.append("""
                  </tbody>
                </table>
              </div>
            """);
        
        // NEW SECTION: Error Details for Failed Scenarios
        boolean hasFailures = data.features().stream()
    .anyMatch(feature -> feature.failed || feature.scenarios.stream().anyMatch(s -> s.failed));

if (hasFailures) {
    html.append("""
    <div class="error-details-section">
        <h2>Error Details</h2>
    """);
    
    // Group by features
    int featureCounter = 0;
    for (FeatureData feature : data.features()) {
        // Skip features with no failures
        if (!feature.failed && feature.scenarios.stream().noneMatch(s -> s.failed)) {
            continue;
        }
        
        featureCounter++;
        String featureId = "feature-" + featureCounter;
        
        html.append("""
        <div class="feature-error">
            <div class="feature-header collapsible" onclick="toggleElement('%s')">
                <span class="toggle-icon">▼</span> Feature: %s %s
            </div>
        """.formatted(
            featureId, 
            escapeHtml(feature.name), 
            feature.failed ? "<span class=\"failed-badge\">[FAILED]</span>" : ""
        ));
        
        html.append("""
            <div id="%s" class="feature-details">
        """.formatted(featureId));
        
        // Add scenario details with errors
        boolean hasFailedScenarios = false;
        int scenarioCounter = 0;
        
        for (ScenarioData scenario : feature.scenarios) {
            if (scenario.failed && scenario.errorMessage != null) {
                hasFailedScenarios = true;
                scenarioCounter++;
                String scenarioId = featureId + "-scenario-" + scenarioCounter;
                
                // Format the error message with better styling
                String formattedError = formatErrorMessage(scenario.errorMessage);
                
                html.append("""
                <div class="scenario-error">
                    <div class="scenario-header">
                        Scenario: %s
                    </div>
                    <div class="error-message">
                        %s
                    </div>
                </div>
                """.formatted(
                    escapeHtml(scenario.name),
                    formattedError
                ));
            }
        }
        
        if (!hasFailedScenarios) {
            html.append("""
            <div class="no-details-message">
                Feature failed but no specific scenario errors were found.
            </div>
            """);
        }
        
        html.append("""
            </div>
        </div>
        """);
    }
    
    html.append("""
    </div>
    """);
}
        
        html.append("""
            </body>
            </html>
            """);
        
        return html.toString();
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    /**
 * Formats the error message for better HTML display
 * Handles both the original format and the new detailed format from feature files
 */
private String formatErrorMessage(String errorMessage) {
    if (errorMessage == null) {
        return "";
    }
    
    // Check if this is a detailed error message from feature files (contains step information)
    if (errorMessage.contains("Given ") || errorMessage.contains("When ") || 
        errorMessage.contains("Then ") || errorMessage.contains("And ") || 
        errorMessage.contains("* ")) {
        
        // Split the error message into lines
        String[] lines = errorMessage.split("\n");
        StringBuilder formattedError = new StringBuilder();
        
        boolean inErrorSection = false;
        for (String line : lines) {
            // Highlight step definitions (Given, When, Then, And, *)
            if (line.trim().startsWith("Given ") || line.trim().startsWith("When ") || 
                line.trim().startsWith("Then ") || line.trim().startsWith("And ") || 
                line.trim().startsWith("* ")) {
                
                if (inErrorSection) {
                    formattedError.append("</pre>");
                    inErrorSection = false;
                }
                
                formattedError.append("<div class=\"step-definition\">")
                             .append(escapeHtml(line))
                             .append("</div>");
            }
            // Highlight error messages
            else if (line.trim().startsWith("Error:")) {
                if (inErrorSection) {
                    formattedError.append("</pre>");
                }
                
                formattedError.append("<pre class=\"error-details\">");
                formattedError.append(escapeHtml(line)).append("\n");
                inErrorSection = true;
            }
            // Stack trace and other details
            else if (inErrorSection) {
                formattedError.append(escapeHtml(line)).append("\n");
            }
            // Other lines
            else {
                formattedError.append("<div>").append(escapeHtml(line)).append("</div>");
            }
        }
        
        if (inErrorSection) {
            formattedError.append("</pre>");
        }
        
        return formattedError.toString();
    }
    
    // For traditional error messages (not from feature files)
    return "<pre class=\"error-details\">" + escapeHtml(errorMessage) + "</pre>";
}

    /**
     * Data record for a single scenario
     */
    record ScenarioData(
        String name,
        boolean failed,
        String errorMessage
    ) {}
    
    /**
     * Data record for a single feature
     */
    record FeatureData(
        String name, 
        boolean failed, 
        int scenarioCount, 
        int passedCount, 
        int failedCount, 
        long durationMillis,
        List<ScenarioData> scenarios
    ) {}
    
    /**
     * Data record containing parsed report information
     */
    record ReportData(
        int passedFeatures, 
        int failedFeatures, 
        int passedScenarios, 
        int failedScenarios, 
        long totalDuration,
        List<FeatureData> features
    ) {
        /**
         * Returns true if all tests passed
         */
        public boolean isTestsPassed() {
            return failedFeatures == 0 && failedScenarios == 0;
        }
    }
}

/**
 * Custom exception for report generation errors
 */
class ReportGenerationException extends Exception {
    public ReportGenerationException(String message) {
        super(message);
    }
    
    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Configuration record for SMTP settings
 */
record SmtpConfig(
    String host,
    int port,
    String username,
    String password,
    boolean useTls,
    String fromAddress,
    String distributionList
) {
    // Default constructor with sensible defaults
    public SmtpConfig() {
        this(
            "your-smtp-server.com", 
            587, 
            "your-email-username", 
            "your-email-password", 
            true, 
            "karate-reports@yourcompany.com", 
            "test-reports-dl@yourcompany.com"
        );
    }
}

/**
 * Custom exception for email sending errors
 */
class EmailException extends Exception {
    public EmailException(String message) {
        super(message);
    }
    
    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Responsible for sending email notifications with report attachments.
 * Follows Single Responsibility Principle.
 */
class EmailSender {
    private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);
    private static final String EMAIL_SUBJECT_PREFIX = "[Test Results] Karate Test Report - ";
    
    private final SmtpConfig smtpConfig;
    
    public EmailSender(SmtpConfig smtpConfig) {
        this.smtpConfig = smtpConfig;
    }
    
    /**
     * Sends an email with the report file attached
     */
    public void sendReportEmail(File reportFile, boolean testsPassed) throws EmailException {
        logger.info("Preparing to send email with report attachment");
        
        try {
            // Set mail properties
            var props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", String.valueOf(smtpConfig.useTls()));
            props.put("mail.smtp.host", smtpConfig.host());
            props.put("mail.smtp.port", String.valueOf(smtpConfig.port()));
            
            // Get the Session object
            var session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(smtpConfig.username(), smtpConfig.password());
                    }
                });
            
            // Create a default MimeMessage object
            var message = new MimeMessage(session);
            
            // Set From: header field
            message.setFrom(new InternetAddress(smtpConfig.fromAddress()));
            
            // Set To: header field
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(smtpConfig.distributionList()));
            
            // Set Subject: header field
            String status = testsPassed ? "PASSED" : "FAILED";
            String subject = EMAIL_SUBJECT_PREFIX + 
                             LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + 
                             " - " + status;
            message.setSubject(subject);
            
            // Create the message parts
            var messageBodyPart = new MimeBodyPart();
            
            // Set the email body with HTML formatting
            var emailBody = createEmailBody(testsPassed);
            messageBodyPart.setContent(emailBody, "text/html; charset=utf-8");
            
            // Create a multipart message
            var multipart = new MimeMultipart();
            
            // Set text message part
            multipart.addBodyPart(messageBodyPart);
            
            // Add attachment
            messageBodyPart = new MimeBodyPart();
            DataSource source = new FileDataSource(reportFile);
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName("Karate-Test-Report.html");
            multipart.addBodyPart(messageBodyPart);
            
            // Send the complete message parts
            message.setContent(multipart);
            
            // Send message
            Transport.send(message);
            logger.info("Email sent successfully to {}", smtpConfig.distributionList());
            
        } catch (MessagingException e) {
            throw new EmailException("Failed to send email: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates the HTML body for the email
     */
    private String createEmailBody(boolean testsPassed) {
        var emailBody = new StringBuilder();
        emailBody.append("""
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <h2>Karate Test Execution Report</h2>
                <p>Hello Team,</p>
                <p>Please find attached the Karate test execution report for the latest run.</p>
            """);
        
        if (!testsPassed) {
            emailBody.append("""
                <p style="color: #d9534f; font-weight: bold;">
                    ⚠️ Some tests have FAILED during this execution. 
                    Please review the attached report for details.
                </p>
            """);
        } else {
            emailBody.append("""
                <p style="color: #5cb85c; font-weight: bold;">
                    ✅ All tests have PASSED successfully.
                </p>
            """);
        }
        
        emailBody.append("""
                <p>This is an automated email. Please do not reply.</p>
                <p>
                    Regards,<br>
                    Karate Test Automation Team
                </p>
            </body>
            </html>
        """);
        
        return emailBody.toString();
    }
}
