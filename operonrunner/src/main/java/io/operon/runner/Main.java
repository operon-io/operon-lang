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

package io.operon.runner;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

import io.operon.runner.ModuleContext;
import io.operon.runner.model.ComponentDefinition;
import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.util.HttpDownloadUtility;
import io.operon.runner.util.StringUtil;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.test.AssertComponent;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.command.*;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class Main {
     // no logger 
    
    public static final String VERSION = "0.9.7.0";
    public static final boolean isNative = false;
    
    public static final int SUCCESS_VALUE = 0;
    public static final int FAILURE_VALUE = 1;
    
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_WHITE = "\u001B[37m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_BLACK = "\u001B[30m";
    
    private static List<String> quotes = new ArrayList<String>();
    private static List<String> changes = new ArrayList<String>();
    public static Charset defaultCharset = StandardCharsets.UTF_8;
    
    public static void main(String [] args) throws Exception, OperonGenericException, IOException {
        try {
            List<CommandLineOption> options = null;
            
            //System.out.println("Starting");
            if (args.length == 0) {
                if (System.in.available() > 0) {
                    //
                    // If there is input in the buffer, then we assume that it is JSON,
                    // and we'll try to format it.
                    //
                    BufferedReader f = new BufferedReader(new InputStreamReader(System.in));
                    String dataInput = "";
                    StringBuilder sb = new StringBuilder();
                    
                    do {
                        try {
                            dataInput = f.readLine();
                            if (dataInput != null) {
                                sb.append(dataInput);
                            }
                            else {
                                break;
                            }
                        } catch (IOException e) {
                            System.err.println("ERROR SIGNAL while reading inputstream");
                            return;
                        }
                    } while (dataInput != null);
                    
                    String inputJson = sb.toString();
                    if (inputJson.isEmpty() == false) {
                        String prettyJson = OperonContext.serializeStringAsPrettyJson(inputJson);
                        System.out.println(prettyJson);
                        System.exit(SUCCESS_VALUE);
                    }
                }
                
                else {
                    throw new Exception("Please give the file name of the query. Use option --help for help.");
                }
            }
            
            else {
                //System.out.println("Parse options");
                CommandLineOptionParser optionsParser = new CommandLineOptionParser();
                options = optionsParser.parse(args);
            }
            
            if (options.size() > 0) {
                //System.out.println("Options found");
                try {
                    boolean commandRunned = false;
                    int returnValue = 0;
                    for (CommandLineOption option : options) {
                        if (commandRunned) {
                            break;
                        }
                        if (option.getOptionName().equals("help")) {
                            returnValue = (new HelpCommand()).execute(options);
                            commandRunned = true;
                        }
                        if (option.getOptionName().equals("version")) {
                            returnValue = (new VersionCommand()).execute(options);
                            commandRunned = true;
                        }
                        if (option.getOptionName().equals("example")) {
                            returnValue = (new ExampleCommand()).execute(options);
                            commandRunned = true;
                        }
                        else if (option.getOptionName().equals("query")) {
                            returnValue = (new QueryCommand(option.getOptionValue())).execute(options);
                            commandRunned = true;
                        }
                        else if (option.getOptionName().equals("testsfolder")) {
                            returnValue = (new TestsFolderCommand(option.getOptionValue())).execute(options);
                            commandRunned = true;
                        }
                        else if (option.getOptionName().equals("tests")) {
                            returnValue = (new TestsCommand(option.getOptionValue())).execute(options);
                            commandRunned = true;
                        }
                        else if (option.getOptionName().equals("test")) {
                            returnValue = (new TestCommand()).execute(options);
                            commandRunned = true;
                        }
                        else if (option.getOptionName().equals("filename")) {
                            try {
                                String query = Main.readFile(option.getOptionValue(), Main.defaultCharset);
                                returnValue = (new QueryCommand(query)).execute(options);
                                commandRunned = true;
                            } catch (java.nio.file.NoSuchFileException e) {
                                throw new Exception("Could not find the query-file: " + option.getOptionValue());
                            }
                        }
                    }
                    System.exit(returnValue);
                } catch (OperonGenericException oge) {
                    System.err.println(oge.getErrorValue());
                }
            }
    
            else if (args.length == 1) {
                //System.out.println("Found query-file");
                String arg = args[0];
                if (arg.startsWith("-") || arg.startsWith("--")) {
                    throw new Exception("Invalid option: missing query.");
                }
                String query = Main.readFile(arg, Main.defaultCharset);
                //System.out.println("query="+query);
                //:OFF:log.debug("QUERY :: " + query);
                OperonRunner runner = new OperonRunner();
                runner.setQuery(query);
                //Thread q1 = new Thread(runner, "Default Query");
                //q1.start();
                runner.run();
            }
            
            // --compileModule path/to/module/modulename.op
            else if (args.length >= 2) {
                String option1 = args[0];
                String option2 = args[1];
                
                //System.out.println("args[0] :: " + option1);
                //System.out.println("args[1] :: " + option2);
                
                if (option1.toLowerCase().equals("--compilemodule")) {
                    String filePathAndName = option2;
                    //System.out.println("args[1] :: " + filePathAndName);
                    String moduleContent = Main.readFile(filePathAndName, Main.defaultCharset);
                    File moduleFile = new File(filePathAndName);
                    //
                    // TODO: tests are not supported yet when compiling just the module
                    // NOTE: the namespace is "local" during the compilation. TODO: the namespace should be able to give from command-line option
                    ModuleContext mc = (ModuleContext) OperonRunner.compileModule(null, null, moduleContent, moduleFile.getPath(), "local");
                    // Save mc to file
                    // TODO: take filename as option
                    OperonRunner.saveOperonContextToFile(mc, "module.opmc");
                }
    
                // TODO: --inputstream --query="Select $.body"
                else if (option1.toLowerCase().equals("--compile")) {
                    String filePathAndName = args[1];
                    System.out.println("args[1] :: " + filePathAndName);
                    String queryContent = Main.readFile(filePathAndName, Main.defaultCharset);
                    File queryFile = new File(filePathAndName);
                    System.out.println("File exists :: " + queryFile.exists());
                    OperonContext operonContext = OperonRunner.createNewOperonContext(queryContent, "query", null);
                    OperonContext oc = (OperonContext) OperonRunner.compile(
                        operonContext, queryContent, queryFile.getPath());
                    // Save oc to file
                    // TODO: take filename as option
                    OperonRunner.saveOperonContextToFile(oc, "operon.opc");
                }
                else if (option1.toLowerCase().equals("--load")) {
                    String filePathAndName = args[1];
                    OperonContext ctx = OperonRunner.loadOperonContextFromFile(filePathAndName);
                    ctx.start(OperonContextManager.ContextStrategy.SINGLETON);
                }
                else if (option1.toLowerCase().equals("--installcomponents")) {
                    // Loop through the file's ArrayType, check from each ObjectTypeect if the
                    // resolveUri is empty. For empty resolveUri's try to download the component
                    // from the sourceUri
                    System.out.println("Installing missing components.");
                    ComponentDefinition cd = new ComponentDefinition();
                    ArrayType components = ComponentSystemUtil.loadComponentDefinitionFile(null); // TODO: could allow different component-filenames.
                    System.out.println("Components file: " + components);
                    List<ComponentDefinition> componentUpdates = new ArrayList<ComponentDefinition>();
                    
                    for (Node arrObj : components.getValues()) {
                        ObjectType obj = (ObjectType) arrObj.evaluate();
                        String valueStr = null;
                        for (PairType pair : obj.getPairs()) {
                            switch (pair.getKey().substring(1, pair.getKey().length() - 1)) {
                                case "type":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setType(valueStr);
                                    break;
                                case "name":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setName(valueStr);
                                    break;
                                case "version":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setVersion(valueStr);
                                    break;
                                case "resolveUri":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setResolveUri(valueStr);
                                    break;
                                case "sourceUri":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setSourceUri(valueStr);
                                    break;
                                case "description":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setDescription(valueStr);
                                    break;
                                case "operonVersion":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setOperonVersion(valueStr);
                                    break;
                                case "hash":
                                    valueStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                                    cd.setHash(valueStr);
                                    break;
                                case "installedTs":
                                    long valueLong = (long) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                                    cd.setInstalledTs(valueLong);
                                    break;
                                case "configuration":
                                    ObjectType valueObj = (ObjectType) pair.getValue().evaluate();
                                    cd.setConfiguration(valueObj);
                                    break;
                                default:
                                    System.err.println("Unknown property: " + pair.getKey());
                                    break;
                            }
                        }
                        System.out.println("ComponentDefinition :: resolveUri :: " + cd.getResolveUri());
                        if (cd.getResolveUri() != null && cd.getResolveUri().isEmpty()) {
                            //   1. create directory for component: ./components/<type>/<name>/<version>
                            //   2. download component into above directory
                            //   3. TODO: update the component -definition file with new resolveUri
                            //      -> this can be done looping components-obj again, and this time writing the updated component-path 
                            File saveDirectory = new File("components" + File.separatorChar + cd.getType() + File.separatorChar + cd.getName() + File.separatorChar + cd.getVersion());
                            if (! saveDirectory.exists()){
                                saveDirectory.mkdirs();
                            }
                            // TODO: before downloading, check if the file exists? NOTE: we don't know the fileName yet.
                            System.out.println("Downloading component: " + cd.getType() + "/" + cd.getName() + "/" + cd.getVersion());
                            String fileName = HttpDownloadUtility.downloadFile(cd.getSourceUri(), saveDirectory.toString());
                            System.out.println("Filename :: " + fileName);
                            File savedComponent = new File("components" + File.separatorChar + cd.getType() + File.separatorChar + cd.getName() + File.separatorChar + cd.getVersion() + File.separatorChar + fileName);
                            if (savedComponent.exists()) {
                                System.out.println("Checking hash (SHA-256)...");
                                byte[] encoded = Main.readFileAsBytes(savedComponent.getPath());
                                String sha256 = StringUtil.sha256Hex(encoded);
                                if (cd.getHash().equals(sha256)) {
                                    System.out.println("  Hash: OK");
                                    Date now = new Date();
                                    Long currentTs = now.getTime();
                                    cd.setInstalledTs(currentTs);
                                    cd.setResolveUri("file:" + savedComponent.getPath());
                                }
                                else {
                                    System.err.println("  Hash: MISMATCH. Expected: [" + cd.getHash() + "], but got: [" + sha256 + "]");
                                    System.err.println("Will not proceed further. Please check the issue.");
                                    System.exit(FAILURE_VALUE);
                                }
                            }
                            else {
                                System.err.println("ERROR :: could not determine if saved component exists.");
                            }
                        }
                        componentUpdates.add(cd);
                    }
    
                    System.out.println("Updating the components-file.");
                    Main.updateComponentDefinitions(componentUpdates, "components.json");
                    
                }
                // The default-testfile:
                else if (option1.toLowerCase().endsWith("--test")) {
                    /*
                    String fileName = option2;
                    String query = Main.readFile(fileName, Main.defaultCharset);
                    String testsContent = Main.readFile("operon.tests", Main.defaultCharset);
                    //:OFF:log.debug("QUERY :: " + query);
                    OperonRunner runner = new OperonRunner();
                    runner.setQuery(query);
                    runner.setTestsContent(testsContent);
                    runner.setIsTest(true);
                    //Thread q1 = new Thread(runner, "Default Query");
                    //q1.start();
                    runner.run();
                    */
                    throw new Exception("main.java :: SHOULD NOT GET HERE: --test");
                }
                else if (option1.toLowerCase().startsWith("--testsfolder=")) {
                    System.out.println("--testsfolder cmd already handled");
                    throw new Exception("main.java :: SHOULD NOT GET HERE: --testsFolder");
                }
                else if (option1.toLowerCase().startsWith("--test=")) {
                    System.out.println("--tests cmd already handled");
                    throw new Exception("main.java :: SHOULD NOT GET HERE: --test");
                }
                else {
                    throw new Exception("Please give the file name of the query or module file.");
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public synchronized static void setInitialValueForJsonSystem(OperonContext ctx, OperonValue initialValue) {
        InputSource fromInputSource = ctx.getFromStatement().getInputSource();
        fromInputSource.setInitialValue(initialValue);
        ctx.getFromStatement().setInputSource(fromInputSource);
    }

    public static String readFile(String path, Charset encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }
    
    public static byte[] readFileAsBytes(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return encoded;
    }
    
    // TODO: move to componentsUtils
    // Should write the updated components.json to file-system
    public static void updateComponentDefinitions(List<ComponentDefinition> newCds, String savePath) throws OperonGenericException, IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        int componentCount = newCds.size();
        for (int i = 0; i < componentCount; i ++) {
            ComponentDefinition cd = newCds.get(i);
            sb.append(cd.toString());
            if (i < componentCount - 1) {
                sb.append(",\n");
            }
        }
        sb.append("]");
        //System.out.println("Write back to file: " + sb.toString());
        // write to file
        byte[] strToBytes = sb.toString().getBytes();
        Path path = Paths.get(savePath);
        Files.write(path, strToBytes);
    }

}
