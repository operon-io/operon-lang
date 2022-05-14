package io.operon.runner.util;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.type.OperonValue;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.OperonRunner;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonContextManager;
import io.operon.runner.model.test.*;

public class TestUtil {
	//
	// Run expr (i.e. the query) against Assert-query.
	//
	// Returns true if query passes tests, otherwise false.
	//
	public static boolean queryWithTests(String query, List<String> tests) {
		try {
			for (int i = 0; i < tests.size(); i ++) {
				String test = tests.get(i);
		        OperonValue result = doTestQuery(query, test);
		        //System.out.println("RESULT=" + result);
		        if (result.toString().startsWith("Error")) {
		        	return false;
		        }
			}
			return true;
		} catch (OperonGenericException oge) {
			System.err.println("Answer is not correct. Got also reported error: " + oge.getMessage());
			return false;
		} catch (Exception e) {
			System.err.println("Answer is not correct. Got also reported error: " + e.getMessage());
			return false;
		}
	}

    //
    // Test consists of Assert- and Mock-statements.
    //
    public static OperonValue doTestQuery(String query, String test) throws OperonGenericException {
        try {
            System.out.println("Starting to compile tests");
            OperonRunner runner = new OperonRunner();
            runner.setContextStrategy(OperonContextManager.ContextStrategy.SINGLETON);
            runner.setQuery(query);
            runner.setTestsContent(test);
            runner.setIsTest(true);
            runner.run();
            OperonContext ctx = runner.getOperonContext();
            OperonValue selectResult = ctx.getOutputOperonValue();
            
            List<AssertComponent> requiredButNotRunnedAsserts = getRequiredButNotRunnedAsserts(runner);
            if (requiredButNotRunnedAsserts.size() > 0) {
                for (int i = 0; i < requiredButNotRunnedAsserts.size(); i ++) {
                    if (requiredButNotRunnedAsserts.get(i).getAssertName() == null) {
                        System.err.println("REQUIRED BUT NOT RUN: " + requiredButNotRunnedAsserts.get(i));
                    }
                    else {
                        System.err.println("REQUIRED BUT NOT RUN: " + requiredButNotRunnedAsserts.get(i));
                    }
                }
                throw new RuntimeException("Assert was required but not run.");
            }
            return selectResult;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static List<AssertComponent> getRequiredButNotRunnedAsserts(OperonRunner runner) { 
        Map<String, List<AssertComponent>> componentAsserts = runner.getOperonContext().getOperonTestsContext().getComponentAsserts();
        List<AssertComponent> requiredButNotRunned = new ArrayList<AssertComponent>();
        //
        // check if some required assert was not run.
        //
        for (Map.Entry<String, List<AssertComponent>> componentAssertEntry : componentAsserts.entrySet()) {
        	List<AssertComponent> asserts = componentAssertEntry.getValue();
        	for (int i = 0; i < asserts.size(); i ++) {
            	try {
            	    if (asserts.get(i).isRequired() && asserts.get(i).isAssertRunned() == false) {
            	        requiredButNotRunned.add(asserts.get(i));
            	    }
        	    } catch (OperonGenericException oge) {
        	        System.err.println("Invalid Assert-configuration: " + asserts.get(i).getComponentName());
        	    }
        	}
        }
        return requiredButNotRunned;
    }

}