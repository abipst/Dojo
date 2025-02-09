Feature: openFX File Transfer

Background:
    * def FileTransferSteps = Java.type('com.openFX.karate.steps.FileTransferSteps')
    * def testDataPath = 'src/test/resources/testdata/'

@file-transfer
Scenario Outline: Transfer file to secure system after checking database
    # Check if file exists in database
    * def fileExists = FileTransferSteps.checkFileExistsInDatabase('<fileName>')
    
    # Delete if file exists
    * if (fileExists) FileTransferSteps.deleteFileRecord('<fileName>')
    
    # Transfer file
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