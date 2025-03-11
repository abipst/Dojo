// File location: src/test/resources/hooks.js

function beforeScenario(scenario) {
    karate.log('************************************************************');
    karate.log('BEFORE SCENARIO: ' + scenario.name);
    karate.log('************************************************************');
    
    var SetupHelper = Java.type('com.test.TestSetupHelper');
    SetupHelper.setupBeforeScenario();
  }
  
  function afterScenario(scenario) {
    karate.log('************************************************************');
    karate.log('AFTER SCENARIO: ' + scenario.name + ' (Status: ' + scenario.status + ')');
    karate.log('************************************************************');
  
    var SetupHelper = Java.type('com.test.TestSetupHelper');
    SetupHelper.cleanupAfterScenario();
  }