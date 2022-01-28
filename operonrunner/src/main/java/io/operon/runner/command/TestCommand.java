/*
 *   Copyright 2022, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.operon.runner.command;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

import io.operon.runner.Main;
import io.operon.runner.OperonContextManager;
import io.operon.runner.OperonTestsContext;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonRunner;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.test.AssertComponent;

public class TestCommand implements MainCommand {

    private String tests = "operon.tests";
    private String filename = null;
    private String query = null;
    
    public TestCommand() {}

    public int execute(List<CommandLineOption> options) throws OperonGenericException {
        boolean inputstream = false;
        OperonConfigs configs = new OperonConfigs();
        
        for (CommandLineOption option : options) {
            if (option.getOptionName().equals("inputstream")) {
                inputstream = true;
            }
            else if (option.getOptionName().equals("query")) {
                this.query = option.getOptionValue();
            }
            else if (option.getOptionName().equals("filename")) {
                this.filename = option.getOptionValue();
            }
            else if (option.getOptionName().toLowerCase().equals("timezone")) {
                configs.setTimezone(option.getOptionValue());
            }
        }
        
        if (query == null && filename != null) {
            try {
                query = Main.readFile(this.filename, StandardCharsets.UTF_8);
            } catch (IOException e) {
                ErrorUtil.createErrorValueAndThrow(null, "COMMAND_TEST", "IO_ERROR", e.getMessage());
            }
        }
        
        //
        // read stream here and materialize, so can be used multiple times:
        //
        String initialValueStr = "";
        if (inputstream) {
            BufferedReader f = new BufferedReader(new InputStreamReader(System.in));
            String dataInput = "";
            StringBuilder sb = new StringBuilder();
            do {
               try {
                    dataInput = f.readLine();
                 if (dataInput != null) {
                     sb.append(dataInput);
                 }
               } catch (IOException e) {
                   System.err.println("ERROR SIGNAL while reading inputstream");
                   return 1;
               }
            } while (dataInput != null);
            initialValueStr = sb.toString();
        }
        
        String [] testFiles = this.tests.split(",");
        List<String> couldNotRunTests = new ArrayList<String>();
        List<AssertComponent> failedTests = new ArrayList<AssertComponent>();
        List<AssertComponent> requiredButNotRunned = new ArrayList<AssertComponent>();
        OperonRunner runner = new OperonRunner();
        
        if (testFiles.length > 0) {
            for (int i = 0; i < testFiles.length; i ++) {
                String testFileStr = testFiles[i].trim();
                //System.out.println("Testfile: " + testFileStr);
                File testsFile = new File(testFileStr);
                if (testsFile.exists() == false) {
                    System.err.println("Could not find the tests file: " + testsFile.getName());
                    System.exit(1);
                }
                if (testsFile.isDirectory()) {
                    continue;
                }
                String testsContent = null;
                try {
                    testsContent = Main.readFile(testsFile.getPath(), StandardCharsets.UTF_8);
                    //System.out.println("TEST=" + testsContent);
                } catch (IOException e) {
                    ErrorUtil.createErrorValueAndThrow(null, "COMMAND_TEST", "IO_ERROR", e.getMessage());
                }
                System.out.println("START TESTS: " + testsFile.getName());
                System.out.println("------------------");
            
                if (inputstream) {
                    OperonValue initialValue = JsonUtil.operonValueFromString(initialValueStr);
                    runner.setInitialValueForJsonSystem(initialValue);
                    runner.setContextStrategy(OperonContextManager.ContextStrategy.SINGLETON);
                }
                runner.setQuery(query);
                runner.setConfigs(configs);
                runner.setTestsContent(testsContent);
                runner.setIsTest(true);
                
                // When starting from Main, do not create a Thread.
                runner.run();
                    
                System.out.println("------------------");
                System.out.println("END TESTS: " + testsFile.getName());
                System.out.println("------------------");
                
                if (runner.getOperonContext() == null) {
                    couldNotRunTests.add(testsFile.getName());
                }
                else {
                    failedTests.addAll(runner.getOperonContext().getOperonTestsContext().getFailedComponents());
                    requiredButNotRunned.addAll(TestCommand.getRequiredButNotRunnedAsserts(runner));
                }
            }
            
            if (couldNotRunTests.size() > 0) {
                System.err.println(Main.ANSI_RED + "ERROR RUNNING TEST" + Main.ANSI_RESET);
                System.err.println("------------------");
                for (String test : couldNotRunTests) {
                    System.err.println(Main.ANSI_RED + "ERROR: " + test + Main.ANSI_RESET);
                }
            }
            
            if (failedTests.size() > 0) {
                System.err.println(Main.ANSI_RED + "TEST FAILURES" + Main.ANSI_RESET);
                System.err.println("------------------");
                for (AssertComponent ac : failedTests) {
                    System.err.println(Main.ANSI_RED + "FAILED: " + ac + Main.ANSI_RESET);
                }
            }

            if (requiredButNotRunned.size() > 0) {
                System.err.println(Main.ANSI_RED + "REQUIRED BUT NOT RUN" + Main.ANSI_RESET);
                System.err.println("------------------");
                for (AssertComponent ac : requiredButNotRunned) {
                    System.err.println(Main.ANSI_RED + "NOT RUN: " + ac + Main.ANSI_RESET);
                }
            }
  
            if (couldNotRunTests.size() == 0 && failedTests.size() == 0 && requiredButNotRunned.size() == 0) {
                System.err.println(Main.ANSI_GREEN + "TESTS PASSED" + Main.ANSI_RESET);
                return 0;
            }
            
            else {
                return 1;
            }
        }
        else {
            System.err.println(Main.ANSI_RED + "NO TESTS WERE FOUND" + Main.ANSI_RESET);
        }
        return 0;
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
