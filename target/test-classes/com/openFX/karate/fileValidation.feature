Feature: Fixed Length File Data Validation

Background:
    * def FileValidationSteps = Java.type('com.openFX.karate.steps.FileValidationSteps')
    * def testDataPath = 'src/test/resources/testdata/'
    
    # Define file layout
    * def fileLayout =
    """
    [
      { name: 'accountId', length: 10 },
      { name: 'customerName', length: 50 },
      { name: 'transactionDate', length: 8 },
      { name: 'amount', length: 12 },
      { name: 'status', length: 2 }
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
    * def validationResults = FileValidationSteps.validateFile(fileRecords, dbRecords)
    
    # Assertions
    * match validationResults.status == 'PASSED'
    * match validationResults.mismatches == '#[0]'
    * print 'Validation Summary:', validationResults
    
    # If there are mismatches, log them for review
    * if (validationResults.status == 'FAILED') karate.log('Validation failures:', validationResults.mismatches)

    Examples:
        | fileName              |
        | accounts_20240210.txt |
        | accounts_20240211.txt |