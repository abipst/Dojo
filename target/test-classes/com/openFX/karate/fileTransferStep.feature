Feature: openFX File Transfer

Background:
    * def FileTransferSteps = Java.type('com.openFX.karate.steps.FileTransferSteps')
    * def testDataPath = 'src/test/resources/testdata/'
    * def fileData = karate.read('classpath:testdata/fileNames.csv')

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

    Examples:
        | fileName          |
        | test_file1.txt   |
        | test_file2.pdf   |

@file-transfer @cleanup
Scenario: Cleanup transferred files
    * def FileTransferSteps.cleanupTransferredFiles()