Feature: Send Test Case Result

  Scenario: Send Test Result for a specific test case
    Given url 'https://your-api-endpoint.com/testrun/' + testRunKey + '/testcase/' + testCaseKey + '/testresult'
    And request requestBody
    And header Content-Type = 'application/json'
    When method POST
    Then status 200
    And print 'Response:', response
