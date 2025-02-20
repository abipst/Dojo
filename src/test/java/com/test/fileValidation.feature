Feature: Fixed Length File Data Validation

  Background:
    * def FileValidationSteps = Java.type('com.openFX.karate.steps.FileValidationSteps')
    * def testDataPath = 'src/test/resources/testdata/'
    * def ZephyrSteps = call read('zephyrStep.feature')
    # Define file layout with DB column mappings
    * def fileLayout =
      """
      [
        { name: 'accountId', length: 10, dbColumn: 'ACCOUNT_ID' },
        { name: 'customerName', length: 50, dbColumn: 'CUSTOMER_NAME' },
        { name: 'transactionDate', length: 8, dbColumn: 'TRANSACTION_DATE' },
        { name: 'amount', length: 12, dbColumn: 'AMOUNT' },
        { name: 'status', length: 2, dbColumn: 'STATUS' }
      ]
      """

   * def testCaseId = ZephyrSteps.testCaseId
    * def executionId = ZephyrSteps.executionId

  @file-validation
  Scenario Outline: Validate fixed length file data against database records
    Given def fileName = '<fileName>'
    And def filePath = testDataPath + fileName
    # Parse the fixed length file
    * def fileRecords = FileValidationSteps.parseFixedLengthFile(filePath, fileLayout)
    * print 'Total records in file:', fileRecords.length
    # Get matching DB records
    * def dbRecords = FileValidationSteps.getDbRecordsByFileName(fileName)
    * print 'Total records in DB:', dbRecords.length
    # Validate all records
    * def validationResults = FileValidationSteps.validateFileData(fileRecords, dbRecords, fileLayout)
    # Assertions
    * match validationResults.status == 'PASSED'
    * match validationResults.mismatches == '#[0]'
    * print 'Validation Summary:', validationResults
    # Additional validations for data consistency
    * def recordCountsMatch = fileRecords.length == dbRecords.length
    * assert recordCountsMatch, 'File and DB record counts do not match'
    # Detailed error reporting if validation fails
    * if (validationResults.status == 'FAILED') {
    * def mismatchDetails = karate.map(validationResults.mismatches, function(x){ return x.error + ' at record ' + (x.recordIndex || 'N/A') })
    * print 'Validation Failures:'
    * print '===================='
    * print mismatchDetails
    * fail 'Validation failed with ' + validationResults.mismatches.length + ' mismatches'
    # If there are mismatches, log them for review
    * if (validationResults.status == 'FAILED') karate.log('Validation failures:', validationResults.mismatches)

    # Determine Zephyr test status
    * def testStatus = validationResults.status == 'PASSED' ? 1 : 2

    # Update Zephyr test execution result
    * call read('zephyrStep.feature@Update Test Result in Zephyr') { executionId: '#(executionId)', statusId: '#(testStatus)' }

    Examples:
      | fileName              |
      | accounts_20240210.txt |

  @file-validation-negative
  Scenario Outline: Validate handling of files with known discrepancies
    Given def fileName = '<fileName>'
    And def filePath = testDataPath + fileName
    # Parse the fixed length file
    * def fileRecords = FileValidationSteps.parseFixedLengthFile(filePath, fileLayout)
    * print 'Total records in file:', fileRecords.length
    # Get matching DB records
    * def dbRecords = FileValidationSteps.getDbRecordsByFileName(fileName)
    * print 'Total records in DB:', dbRecords.length
    # Perform enhanced validation
    * def validationResults = FileValidationSteps.validateGenericRecords(fileRecords, dbRecords, fileLayout)
    * print 'Validation Results:', validationResults
    # Assertions for expected failures
    * match validationResults.status == 'FAILED'
    * match validationResults.mismatches == '#[_ > 0]'
    # Verify specific error conditions
    * def hasMismatchType = function(mismatches, errorType) { return karate.filter(mismatches, function(x){ return x.error.contains(errorType) }).length > 0 }
    * if ('<expectedError>' == 'RecordCount') assert hasMismatchType(validationResults.mismatches, 'Record count mismatch')
    * if ('<expectedError>' == 'FieldValue') assert hasMismatchType(validationResults.mismatches, 'Field value mismatch')
    # Log detailed mismatch information
    * print 'Expected Validation Failures for:', fileName
    * print '================================='
    * karate.forEach(validationResults.mismatches, function(mismatch){ karate.log('Mismatch:', mismatch) })
    # Clean up resources
    * eval FileValidationSteps.closeDbConnection()

    Examples:
      | fileName                       | expectedError |
      | accounts_missing_20240210.txt  | RecordCount   |
      | accounts_mismatch_20240211.txt | FieldValue    |
