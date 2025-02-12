Feature: openFX File Transfer

Background:
    * def FileTransferSteps = Java.type('com.openFX.karate.steps.FileTransferSteps')
    * def testDataPath = 'src/test/resources/testdata/'
    * def fileData = karate.read('classpath:testdata/fileNames.csv')
    * url 'https://ga.openfx-nonprod.aws.fisv.cloud/v1/dcc/batches/maintenance/load'

@file-transfer
Scenario Outline: Transfer file to secure system after checking database
    Given file exists in database
    * def fileExists = FileTransferSteps.checkFileExistsInDatabase('<fileName>')
    
    When file exists
    * if (fileExists) FileTransferSteps.deleteFileRecord('<fileName>')
    
    Then transfer file
    * def transferResult = FileTransferSteps.transferFileToSecureSystem(testDataPath + '<fileName>')
    * match transferResult.success == true
    * match transferResult.message == 'File transferred successfully'
    
    Given path '/f2'
    when method GET
    Then status = 200

    * def fileLoaded = retryUntilTrue(() => FileTransferSteps.checkFileExistsInDatabase('<fileName>'), 5000, 500)
    * match fileLoaded == true

    Examples:
        | fileName          |
        | test_file1.txt   |
        | test_file2.pdf   |

@file-transfer @cleanup
Scenario: Cleanup transferred files
    * def FileTransferSteps.cleanupTransferredFiles()