package com.test;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

public class TestRunner {
	
    @Test
    void testParallel() {
        Results results = Runner.path("classpath:test/TCExec.feature")
                .outputCucumberJson(true)
             
                .parallel(5);
    }
}
