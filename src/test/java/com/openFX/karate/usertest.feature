Feature: User Management Tests

  Background:
    * def UserSteps = Java.type('com.openFX.karate.steps.UserSteps')

  @users
  Scenario: Get all users
    * def users = UserSteps.getAllUsers()
    * match users != null
    * assert users.length > 0