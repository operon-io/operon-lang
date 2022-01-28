/** OPERON-LICENSE **/
package io.operon.runner.command;

import java.util.List;
import java.util.ArrayList;

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

public class TestsFolderCommand implements MainCommand {

    private String folder = "";
    private String filename = null;
    private String query = null;
    
    public TestsFolderCommand(String folder) {
        this.folder = folder;
    }

    public int execute(List<CommandLineOption> options) throws OperonGenericException {
        boolean inputstream = false;
        OperonConfigs configs = new OperonConfigs();
        
        List<String> onlyRunTests = new ArrayList<String>();
        for (CommandLineOption option : options) {
            if (option.getOptionName().equals("inputstream")) {
                inputstream = true;
            }
            else if (option.getOptionName().equals("tests")) {
                String [] testFiles = option.getOptionValue().split(",");
                for (int i = 0; i < testFiles.length; i ++) {
                    String testFileStr = testFiles[i].trim();
                    onlyRunTests.add(testFileStr);
                    System.out.println("Run test: " + testFileStr);
                }
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
                ErrorUtil.createErrorValueAndThrow(null, "COMMAND_TESTS_FOLDER", "IO_ERROR", e.getMessage());
            }
        }
        
        Path path = Paths.get(this.folder);
        
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
        
        // 
        // list files and start each test
        //
        List<String> couldNotRunTests = new ArrayList<String>();
        List<AssertComponent> failedTests = new ArrayList<AssertComponent>();
        List<AssertComponent> requiredButNotRunned = new ArrayList<AssertComponent>();
        OperonRunner runner = new OperonRunner();
        
        try (Stream<Path> walk = Files.walk(path)) {
            List<File> testFiles = walk.map(f -> f.toFile()).collect(Collectors.toList());
            for (File testsFile : testFiles) {
                if (testsFile.isDirectory()) {
                    // read the directory files and start tests against them
                    //System.out.println("Skip directory: " + testsFile.getName());
                    continue;
                }
                if (onlyRunTests.size() > 0 && onlyRunTests.contains(testsFile.getName()) == false) {
                    continue;
                }
                String testsContent = Main.readFile(testsFile.getPath(), StandardCharsets.UTF_8);
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
        } catch (IOException e) {
	        System.err.println("ERROR SIGNAL");
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
}
