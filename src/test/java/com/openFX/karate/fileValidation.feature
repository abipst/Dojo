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
    
    # Parse the fixed length file using FileValidationSteps
    * def fileRecords = FileValidationSteps.parseFixedLengthFile(filePath, fileLayout)
    * print 'Parsed file records:', fileRecords
    
    # Validate each record
    * def validationResults = []
    * def validate = 
    """
    function() {
        for (var i = 0; i < fileRecords.length; i++) {
            var record = fileRecords[i];
            var dbResults = FileValidationSteps.validateRecordAgainstDb(record.accountId);
            var comparisonResult = FileValidationSteps.compareRecords(record, dbResults[0]);
            if (comparisonResult.status === 'FAILED') {
                validationResults.push(comparisonResult);
            }
        }
    }
    """
    * call validate
    
    # Assert validation results
    * match validationResults == '#[0]'
    * if (validationResults.length > 0) karate.log('Validation failures:', validationResults)

    Examples:
        | fileName              |
        | accounts_20240210.txt |
        | accounts_20240211.txt |

@file-validation @cleanup
Scenario: Cleanup database connection
    * eval FileValidationSteps.closeDbConnection()