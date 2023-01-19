/*
 *   Copyright 2022-2023, operon.io
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

package io.operon.runner.system.integration.readfile;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.charset.Charset;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.model.streamvaluewrapper.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.Main;

import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

//
// This component is a "producer".
//
public class ReadfileComponent extends BaseComponent implements IntegrationComponent, java.io.Serializable {
     // no logger 

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private Long pollCounter = 0L;
    private ObjectType initialValue;
    
    public ReadfileComponent() {}

    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        //:OFF:log.debug("readfile :: produce");
        try {
            Info info = resolve(currentValue);
            OperonValue result = this.handleTask(currentValue, info);
            return result;
        } catch (OperonGenericException | IOException | InterruptedException e) {
            ErrorValue error = ErrorUtil.createErrorValue(currentValue.getStatement(), "COMPONENT", "READFILE", e.getMessage());
            throw new OperonComponentException(error);
        }
    }

    private OperonValue handleTask(OperonValue currentValue, Info info) throws OperonGenericException, IOException, InterruptedException {
        ObjectType result = new ObjectType(currentValue.getStatement());
        Statement stmt = currentValue.getStatement();
        
        //System.out.println("Path=" + info.path);
        //System.out.println("FileName=" + info.fileName);
        
        String fileName = info.fileName;
        Path folder = Paths.get(info.path);
        Path path = null;
        
        if (info.fileName != null && info.fileName.isEmpty() == false) {
            path = Paths.get(info.path + File.separator + fileName);
        }
        
        //
        // If path does not exist, then create it. Do not follow symbolic links
        //
        if (info.createPaths && !Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(folder);
        }
        
        //
        // Read a single file
        //
        if (folder != null && path != null) {
            OperonValue fileValue = readSingleFile(stmt, folder, path, info);
            PairType isEmptyPair = new PairType(stmt);
            
            if (fileValue == null) {
                isEmptyPair.setPair("\"isEmpty\"", new TrueType(stmt));
                fileValue = new EmptyType(stmt);
            }
            else {
                isEmptyPair.setPair("\"isEmpty\"", new FalseType(stmt));
            }
            
            result.addPair(isEmptyPair);
            PairType bodyPair = new PairType(stmt);
            bodyPair.setPair("\"body\"", fileValue);
            result.addPair(bodyPair);
        }
        
        //
        // Read all files from the folder
        //
        else {
            //System.out.println("Read files from the folder: " + info.path);
            File folderCheck = new File(info.path);
    
            if (folderCheck.isDirectory() == false) {
                System.err.println("Folder not found :: " + info.path);
                // TODO: return empty-value or wrapper
                return result;
            }
            //System.out.println("Scan folder");
            List<String> fileNames = this.readFolderFilenames(folder, info);
            //System.out.println("Scan folder done.");
            //System.out.println("  Files: " + fileNames);
            ArrayType resultFiles = new ArrayType(stmt);
            
            //
            // Read all files into list
            //
            for (int i = 0; i < fileNames.size(); i ++) {
                ObjectType resultFile = new ObjectType(stmt);
                //System.out.println("Filename :: " + fileNames.get(i));
                Path folderFilePath = Paths.get(fileNames.get(i));
                OperonValue fileValue = readSingleFile(stmt, folder, folderFilePath, info);
                PairType isEmptyPair = new PairType(stmt);
                
                if (fileValue == null) {
                    isEmptyPair.setPair("\"isEmpty\"", new TrueType(stmt));
                    fileValue = new EmptyType(stmt);
                }
                else {
                    isEmptyPair.setPair("\"isEmpty\"", new FalseType(stmt));
                }
                
                resultFile.addPair(isEmptyPair);
                PairType bodyPair = new PairType(stmt);
                bodyPair.setPair("\"body\"", fileValue);
                resultFile.addPair(bodyPair);
                resultFiles.addValue(resultFile);
                
                // Stop reading files if maxFileAmount was reached.
                if (info.maxFileAmount != null) {
                    if (i >= info.maxFileAmount - 1) {
                        break;
                    }
                }
            }
            
            PairType isEmptyPair = new PairType(stmt);
            
            if (resultFiles.getValues().isEmpty()) {
                isEmptyPair.setPair("\"isEmpty\"", new TrueType(stmt));
            }
            else {
                isEmptyPair.setPair("\"isEmpty\"", new FalseType(stmt));
            }
 
            result.addPair(isEmptyPair);
            PairType bodyPair = new PairType(stmt);
            bodyPair.setPair("\"body\"", resultFiles);
            result.addPair(bodyPair);
        }
        
        return result;
    }

    private List<String> readFolderFilenames(Path path, Info info) throws OperonGenericException, InterruptedException {
        //System.out.println("Scan folder: " + path); // FIXME: path is null

        //
        // Read all files from given folder.
        //
		String separatorPattern = Pattern.quote(System.getProperty("file.separator"));
        String [] rootPathParts = path.toString().split(separatorPattern);
        //System.out.println("rootPathParts: " + rootPathParts);
    	try (Stream<Path> walk = Files.walk(path)) {
    	    //System.out.println("walk folder");
    		List<String> fileNames = walk.map(f -> f.toString())
    				.filter(f -> {
    				    //System.out.println("filter f");
    				    //
    				    // Test for subfolders (recursive -option):
    				    //
    				    String [] testPathParts = Paths.get(f).toString().split(separatorPattern);
    				    //System.out.println("testPathParts length: " + testPathParts.length + ", " + Paths.get(f).toString());
    				    //System.out.println("  rootPathParts length: " + rootPathParts.length + 1);
    				    //System.out.println("  info.path: " + info.path);
    				    if (info.recursive == false && Paths.get(f).toFile().isDirectory()
    				        ||
    				        // No not scan subfolder if not set to recursive:
    				        (info.recursive == false && (testPathParts.length > rootPathParts.length + 1)) 
    				        ||
    				        // If set to recursive, skip the directory-name itself:
    				        (info.recursive && Paths.get(f).toFile().isDirectory())) {
    				        return false;
    				    }
    				    else {
    				        return true;
    				    }
    				})
    				//.filter(f -> f.endsWith(".operon") == false) // readLocked files
    				//.filter(f -> filterPredicate(f, info))
    				.collect(Collectors.toList());
    		
    		if (info.shuffle) {
    		    Collections.shuffle(fileNames);
    		}
            return fileNames;
    	} catch (IOException e) {
    		System.err.println("ERROR SIGNAL: file-system");
    		return new ArrayList<String>();
    	}
    }

    public OperonValue readSingleFile(Statement stmt, Path folder, Path path, Info info) throws OperonGenericException, IOException {
        if (folder == null || path == null) {
            return null;
        }
        OperonValue bodyValue = null;
        
        if (info.readAs == ReadAsType.JSON) {
            String readData = "";
            try {
                if (path != null && Files.exists(path)) {
                    readData = new String(Files.readAllBytes(path), info.charSet);
                    if (readData.length() == 0) {
                        return null;
                    }
                    else {
                        //
                        // json-parse:
                        //
                        bodyValue = JsonUtil.operonValueFromString(readData);
                    }
                }
                else {
                    return null;
                }
            } catch (Exception e) {
                String type = "COMPONENT";
                String code = "READFILE";
                String message = e.getMessage();
                ErrorUtil.createErrorValueAndThrow(stmt, type, code, message);
            }
        }
        
        else if (info.readAs == ReadAsType.RAW) {
            byte[] uninterpretedBytes = Files.readAllBytes(path);
            if (uninterpretedBytes.length == 0) {
                return null;
            }
            else {
                RawValue raw = new RawValue(stmt);
                raw.setValue(uninterpretedBytes);
                bodyValue = raw;
            }
        }
        
        else if (info.readAs == ReadAsType.STREAM) {
            InputStream fileInputStream = new FileInputStream(path.toString());
            StreamValue streamNode = new StreamValue(stmt);
            StreamValueWrapper svw = new StreamValueInputStreamWrapper(fileInputStream);
            
            if (info.streamLines == false) {
                svw.setSupportsJson(false);
                streamNode.setValue(svw);
                bodyValue = streamNode;
            }
            
            else {
                svw.setSupportsJson(true);
                streamNode.setValue(svw);
                bodyValue = streamNode;
            }
        }
        
        else {
            String type = "COMPONENT";
            String code = "READFILE";
            String message = "Unsupported readAs -type";
            ErrorUtil.createErrorValueAndThrow(stmt, type, code, message);
        }
        
        return bodyValue;
    }

    public Info resolve(OperonValue currentValue) throws OperonGenericException {
        OperonValue currentValueCopy = currentValue;
        
        ObjectType jsonConfiguration = this.getJsonConfiguration();
        jsonConfiguration.getStatement().setCurrentValue(currentValueCopy);
        List<PairType> jsonPairs = jsonConfiguration.getPairs();

        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            OperonValue currentValueCopy2 = currentValue;
            pair.getStatement().setCurrentValue(currentValueCopy2);
            //System.out.println("KEY=" + key);
            switch (key.toLowerCase()) {
                case "\"path\"":
                    String sPath = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.path = sPath;
                    break;
                case "\"filename\"":
                    String sFn = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.fileName = sFn;
                    break;
                case "\"readas\"":
                    String sRa = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    try {
                        info.readAs = ReadAsType.valueOf(sRa.toUpperCase());
                    } catch(Exception e) {
                        System.err.println("ERROR SIGNAL: invalid readAs-property in readfile-component");
                    }
                    break;
                case "\"createpaths\"":
                    OperonValue bool_Node = pair.getEvaluatedValue();
                    if (bool_Node instanceof TrueType) {
                        info.createPaths = true;
                    }
                    else {
                        info.createPaths = false;
                    }
                    break;
                case "\"streamlines\"":
                    OperonValue streamLines_Node = pair.getEvaluatedValue();
                    if (streamLines_Node instanceof TrueType) {
                        info.streamLines = true;
                    }
                    else {
                        info.streamLines = false;
                    }
                    break;
                
                case "\"shuffle\"":
                    OperonValue shuffle_Node = pair.getEvaluatedValue();
                    if (shuffle_Node instanceof TrueType) {
                        info.shuffle = true;
                    }
                    else {
                        info.shuffle = false;
                    }
                    break;
                case "\"recursive\"":
                    OperonValue recursive_Node = pair.getEvaluatedValue();
                    if (recursive_Node instanceof TrueType) {
                        info.recursive = true;
                    }
                    else {
                        info.recursive = false;
                    }
                    break;
                case "\"maxfileamount\"":
                    double maxFileAmount = ((NumberType) pair.getEvaluatedValue()).getDoubleValue();
                    info.maxFileAmount = (long) maxFileAmount;
                    break;
                default:
                    //:OFF:log.debug("readfile -producer: no mapping for configuration key: " + key);
                    System.err.println("readfile -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "READFILE", "ERROR", "readfile -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }
    
    private class Info {
        private String path = "./";
        private String fileName;
        private ReadAsType readAs = ReadAsType.JSON;
        private boolean streamLines = false;
        private boolean createPaths = true;
        private boolean shuffle = false;
        private boolean recursive = false;
        private Long maxFileAmount = null; // unlimited
        private Charset charSet = Main.defaultCharset; // byte encoding scheme for read bytes
    }

    private enum ReadAsType {
        JSON("json"), RAW("raw"), STREAM("stream");
        private String readAsType = "json";
        ReadAsType(String type) {
            this.readAsType = type;
        }
        public String getReadAsType() { return this.readAsType; }
    }

}
