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
    public ReportData parseKarateSummary(String summaryJsonPath) throws ReportGenerationException {
        try {
            logger.info("Parsing Karate summary from {}", summaryJsonPath);
            
            JSONParser parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
            JSONObject summary;
            
            try (var reader = new FileReader(summaryJsonPath)) {
                summary = (JSONObject) parser.parse(reader);
            }
            
            // Extract feature data
            JSONArray featureResults = (JSONArray) summary.get("featureSummary");
            
            var featureStats = StreamSupport.stream(featureResults.spliterator(), false)
                .map(feature -> (JSONObject) feature)
                .map(feature -> new FeatureData(
                    (String) feature.getOrDefault("name", "Unknown"),
                    (boolean) feature.getOrDefault("failed", false),
                    ((Number) feature.getOrDefault("scenarioCount", 0)).intValue(),
                    ((Number) feature.getOrDefault("passedCount", 0)).intValue(),
                    ((Number) feature.getOrDefault("failedCount", 0)).intValue(),
                    ((Number) feature.getOrDefault("durationMillis", 0)).longValue()
                ))
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
              </style>
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
            </body>
            </html>
            """);
        
        return html.toString();
    }
    
    /**
     * Data record for a single feature
     */
    record FeatureData(
        String name, 
        boolean failed, 
        int scenarioCount, 
        int passedCount, 
        int failedCount, 
        long durationMillis
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
