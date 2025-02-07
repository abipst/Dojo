Feature: Move flat file to remote system

  Background:
    * def sftp = new Java('com.openFX.karate.SftpStepDefs')  # Initialize SftpStepDefs class

  Scenario: Transfer file from local to remote
    Given def localFilePath = '/local/source/directory/exampleFile.txt'
    And def remoteFilePath = '/remote/target/directory/exampleFile.txt'
    When sftp.removeFileIfExists(remoteFilePath)  # Remove existing file if present
    Then sftp.transferFile(localFilePath, remoteFilePath)  # Transfer new file

Feature: Test Database Operations

  Scenario: Select all users from the database
    * def result = karate.call('classpath:openfx/karate/DbSelectUsersStep.selectAllUsers')

    # Assert the result (e.g., check that at least one user exists)
    * match result.length() > 0
