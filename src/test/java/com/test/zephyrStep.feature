Feature: Zephyr Integration Steps

  Background:
    * url 'https://api.zephyrscale.smartbear.com/v2/'
    * header Authorization = 'Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJjb250ZXh0Ijp7ImJhc2VVcmwiOiJodHRwczovL2FiaXNkZXQuYXRsYXNzaWFuLm5ldCIsInVzZXIiOnsiYWNjb3VudElkIjoiNzEyMDIwOjZkOWJlOWZlLTg2ZmMtNDViZi05NzMyLTU2N2RmZTkxYTQwNCIsInRva2VuSWQiOiI3NmNmNjNjYS0wMjkzLTQ5YzQtYTBkOS1hNWVmYWUzOTVmM2EifX0sImlzcyI6ImNvbS5rYW5vYWgudGVzdC1tYW5hZ2VyIiwic3ViIjoiYjNjNzU5ZDAtZjg0YS0zYzkwLTlhYTQtMjQ5NWU0ODMwOTk1IiwiZXhwIjoxNzcxNTU0MDUxLCJpYXQiOjE3NDAwMTgwNTF9.tBSsLZO4sxiuOZQt1ldMlKiuBOWgWBZu1W7jCVe-H5g'
    * header Content-Type = 'application/json'

  Scenario: Create Test Case in Zephyr Scale
    Given path 'testcases'
    And request
      """
      {
        "projectKey": "DS_ALGO",
        "name": "Fixed Length File Data Validation",
        "description": "Automated test for file validation using Karate",
        "statusName": "Draft",
        "folder": { "name": "Automation" },
        "priorityName": "High"
      }
      """
    When method POST
    Then status 201
    * def testCaseId = response.key
    * print 'Test case created with ID:', testCaseId

  Scenario: Create a Test Execution in Zephyr Scale
    Given path 'testexecutions'
    And request
      """
      {
        "projectKey": "PROJ",
        "testCaseKey": "#(testCaseId)",
        "statusName": "In Progress"
      }
      """
    When method POST
    Then status 201
    * def executionId = response.id
    * print 'Execution started with ID:', executionId

  Scenario: Update Test Result in Zephyr Scale
    Given path 'testexecutions/' + executionId
    And request
      """
      {
        "statusName": "<statusName>" # "Pass" or "Fail"
      }
      """
    When method PUT
    Then status 200
    * print 'Test result updated for execution ID:', executionId
