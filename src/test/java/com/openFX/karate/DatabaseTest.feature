Feature: Test Database Operations

  Scenario: Select all users from the database
    * def result = karate.call('classpath:openfx/karate/DbSelectUsersStep.selectAllUsers')

    # Assert the result (e.g., check that at least one user exists)
    * match result.length() > 0
