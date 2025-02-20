Feature: User Management Tests

  Background:
   # * def UserSteps = Java.type('com.openFX.karate.steps.UserSteps')

  @users
  Scenario: Get all users
    * def users = 1
    * match users === 1