Feature: Fixed Length File Data Validation

Background:
    * def FileValidationSteps = Java.type('com.openFX.karate.steps.FileValidationSteps')
    * def testDataPath = 'src/test/resources/testdata/'
    
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
    * def validationResults = FileValidationSteps.validateFile(fileRecords, dbRecords, fileLayout)
    
    # Assertions
    * match validationResults.status == 'PASSED'
    * match validationResults.mismatches == '#[0]'
    * print 'Validation Summary:', validationResults
    
    # If there are mismatches, log them for review
    * if (validationResults.status == 'FAILED') karate.log('Validation failures:', validationResults.mismatches)

    Examples:
        | fileName              |
        | accounts_20240210.txt |