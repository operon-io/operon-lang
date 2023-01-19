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

package io.operon.runner.system.integration.file;

import java.util.Collections;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.charset.StandardCharsets;

import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.model.streamvaluewrapper.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.LogManager;

//
// This component is a "producer", i.e. it only writes data.
//
public class FileComponent extends BaseComponent implements IntegrationComponent {

     // no logger 

    public FileComponent() {
        //:OFF:log.debug("file :: constructor");
    }
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        //:OFF:log.debug("file :: produce");
        try {
            Info info = resolve(currentValue);
            OperonValue result = this.handleTask(currentValue, info);
            //System.out.println("FileComponent produce result = " + result);
            return result;
        } catch (OperonGenericException | IOException e) {
            ErrorValue error = ErrorUtil.createErrorValue(currentValue.getStatement(), "COMPONENT", "FILE", e.getMessage());
            throw new OperonComponentException(error);
        }
    }

    private OperonValue handleTask(OperonValue currentValue, Info info) throws OperonGenericException, IOException {
        //
        // Write to given file (TODO: if fileName empty, then create new random filename)
        // 
        String fileName = info.fileName;
        Path folder = Paths.get(info.path);
        Path path = null;
        
        if (fileName != null) {
            path = Paths.get(info.path + File.separator + fileName);
        }
        else {
            path = folder;
        }

        if (info.method == MethodType.WRITE) {
            // If path does not exist, then create it. Do not follow symbolic links
            if (info.createPaths && !Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(folder);
            }
            
            if (info.writeAs == WriteAsType.JSON) {
                String valueToWrite = null;
                if (info.prettyPrint) {
                    valueToWrite = OperonContext.serializeAsPrettyJson(currentValue);
                }
                else {
                    valueToWrite = currentValue.toString();
                }
                
                if (info.addLineBreak) {
                    valueToWrite += System.lineSeparator();
                }
                byte[] strToBytes = valueToWrite.getBytes();
                try {
                    Files.write(path, strToBytes, info.openOptions);
                } catch (NoSuchFileException nsfe) {
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "COMPONENT", "FILE", "Could not open file. Please add CREATE, CREATE_NEW or TRUNCATE_EXISTING -option.");
                } catch (Exception e) {
                    System.out.println("ERROR: " + e.getMessage() + ", " + e.getClass().getName());
                }
            }
            
            else if (info.writeAs == WriteAsType.YAML) {
                String valueToWrite = null;
                valueToWrite = OperonContext.serializeAsYaml(currentValue);
                
                if (info.addLineBreak) {
                    valueToWrite += System.lineSeparator();
                }
                byte[] strToBytes = valueToWrite.getBytes();
                try {
                    Files.write(path, strToBytes, info.openOptions);
                } catch (NoSuchFileException nsfe) {
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "COMPONENT", "FILE", "Could not open file. Please add CREATE, CREATE_NEW or TRUNCATE_EXISTING -option.");
                } catch (Exception e) {
                    System.out.println("ERROR: " + e.getMessage() + ", " + e.getClass().getName());
                }
            }
            
            else if (info.writeAs == WriteAsType.TOML) {
                String valueToWrite = null;
                valueToWrite = OperonContext.serializeAsToml(currentValue);
                
                if (info.addLineBreak) {
                    valueToWrite += System.lineSeparator();
                }
                byte[] strToBytes = valueToWrite.getBytes();
                try {
                    Files.write(path, strToBytes, info.openOptions);
                } catch (NoSuchFileException nsfe) {
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "COMPONENT", "FILE", "Could not open file. Please add CREATE, CREATE_NEW or TRUNCATE_EXISTING -option.");
                } catch (Exception e) {
                    System.out.println("ERROR: " + e.getMessage() + ", " + e.getClass().getName());
                }
            }
            
            else if (info.writeAs == WriteAsType.RAW) {
                //System.out.println("writeAs binary :: " + currentValue.getClass().getName());
                
                try {
                    if (currentValue instanceof OperonValue) {
                        currentValue = (OperonValue) currentValue.evaluate();
                    }
                    
                    if (currentValue instanceof RawValue) {
                        RawValue raw = (RawValue) currentValue.evaluate();
                        byte[] uninterpretedBytes = raw.getBytes();
                        Files.write(path, uninterpretedBytes, info.openOptions);
                    }
                    else if (currentValue instanceof StreamValue) {
                        StreamValue sv = (StreamValue) currentValue.evaluate();
                        StreamValueWrapper svw = sv.getStreamValueWrapper();
                        if (svw instanceof StreamValueByteArrayWrapper) {
                            ByteArrayInputStream is = ((StreamValueByteArrayWrapper) svw).getByteArrayInputStream();
                            Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);
                        }
                        else {
                            // TODO: read the stream with read()-method.
                        }
                    }
    
                } catch (Exception e) {
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "COMPONENT", "FILE", "Could not convert into binary, please check writeAs -option.");
                }
            }
            else {
                ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "COMPONENT", "FILE", "Cannot deduce writeAs -type");
            }
        }
        else if (info.method == MethodType.INFO) {
            return FileComponent.info(currentValue.getStatement(), path);
        }
        else if (info.method == MethodType.DELETE) {
            FileComponent.delete(currentValue.getStatement(), path);
        }
        else if (info.method == MethodType.RENAME) {
            FileComponent.rename(currentValue.getStatement(), path, info);
        }
        else if (info.method == MethodType.MKDIR) {
            FileComponent.mkdir(currentValue.getStatement(), info);
        }
        else if (info.method == MethodType.LIST) {
            return FileComponent.list(currentValue.getStatement(), path, info);
        }
        return currentValue;
    }

    public static ObjectType info(Statement stmt, Path path) throws OperonGenericException {
        //System.out.println("INFO-method:");
        ObjectType infoObj = new ObjectType(stmt);
        File f = path.toFile();
        //System.out.println("file: " + f.getName());
        boolean canExecute = f.canExecute();
        boolean canRead = f.canRead();
        boolean canWrite = f.canWrite();
        boolean exists = f.exists();
        boolean isAbsolute = f.isAbsolute();
        boolean isDirectory = f.isDirectory();
        boolean isFile = f.isFile();
        boolean isHidden = f.isHidden();
        String absolutePath = f.getAbsolutePath();
        //long freeSpace = f.getFreeSpace(); // These are not relevant in the File's info-context.
        //long totalSpace = f.getTotalSpace();
        //long usableSpace = f.getUsableSpace();
        long lastModified = f.lastModified();
        long length = f.length();
        
        //System.out.println("file length: " + length);
        
        PairType canExecutePair = new PairType(stmt);
        canExecutePair.setPair("\"canExecute\"", OperonValue.fromBoolean(stmt, canExecute));
        infoObj.addPair(canExecutePair);

        PairType canReadPair = new PairType(stmt);
        canReadPair.setPair("\"canRead\"", OperonValue.fromBoolean(stmt, canRead));
        infoObj.addPair(canReadPair);
        
        PairType canWritePair = new PairType(stmt);
        canWritePair.setPair("\"canWrite\"", OperonValue.fromBoolean(stmt, canWrite));
        infoObj.addPair(canWritePair);
        
        PairType existsPair = new PairType(stmt);
        existsPair.setPair("\"exists\"", OperonValue.fromBoolean(stmt, exists));
        infoObj.addPair(existsPair);
        
        PairType isAbsolutePair = new PairType(stmt);
        isAbsolutePair.setPair("\"isAbsolute\"", OperonValue.fromBoolean(stmt, isAbsolute));
        infoObj.addPair(isAbsolutePair);
        
        PairType isDirectoryPair = new PairType(stmt);
        isDirectoryPair.setPair("\"isDirectory\"", OperonValue.fromBoolean(stmt, isDirectory));
        infoObj.addPair(isDirectoryPair);
        
        PairType isFilePair = new PairType(stmt);
        isFilePair.setPair("\"isFile\"", OperonValue.fromBoolean(stmt, isFile));
        infoObj.addPair(isFilePair);
        
        PairType isHiddenPair = new PairType(stmt);
        isHiddenPair.setPair("\"isHidden\"", OperonValue.fromBoolean(stmt, isHidden));
        infoObj.addPair(isHiddenPair);
        
        PairType absolutePathPair = new PairType(stmt);
        absolutePathPair.setPair("\"absolutePath\"", StringType.create(stmt, absolutePath));
        infoObj.addPair(absolutePathPair);
        
        /*
        PairType freeSpacePair = new PairType(stmt);
        freeSpacePair.setPair("\"freeSpace\"", NumberType.create(stmt, (double) freeSpace, (byte) 0));
        infoObj.addPair(freeSpacePair);
        
        PairType totalSpacePair = new PairType(stmt);
        totalSpacePair.setPair("\"totalSpace\"", NumberType.create(stmt, (double) totalSpace, (byte) 0));
        infoObj.addPair(totalSpacePair);
        
        PairType usableSpacePair = new PairType(stmt);
        usableSpacePair.setPair("\"usableSpace\"", NumberType.create(stmt, (double) usableSpace, (byte) 0));
        infoObj.addPair(usableSpacePair);
        */
        
        PairType lastModifiedPair = new PairType(stmt);
        lastModifiedPair.setPair("\"lastModified\"", NumberType.create(stmt, (double) lastModified, (byte) 0));
        infoObj.addPair(lastModifiedPair);
        
        PairType lengthPair = new PairType(stmt);
        lengthPair.setPair("\"length\"", NumberType.create(stmt, (double) length, (byte) 0));
        infoObj.addPair(lengthPair);
        
        return infoObj;
    }

    //
    // Returns a simple list, such as: ["./target/temp/1/foo.xdata", "./target/temp/1/foo2.xdata", ...]
    //
    // - If option listFileNameOnly has been toggled, then no path is taken.
    //
    public static ArrayType list(Statement stmt, Path path, Info info) throws OperonGenericException {
        //System.out.println("LIST-method. Path = " + path);
        ArrayType listArr = new ArrayType(stmt);
    	try (Stream<Path> walk = Files.walk(path)) {
    		List<Node> fileNames = walk.map(p -> p.toFile())
    		    .filter(f -> f.isDirectory() == false)
    		    .map(f -> {
    		        if (info.listFileNameOnly == false) {
            		    StringType fileStr = StringType.create(stmt, f.toString());
            		    return fileStr;
    		        }
    		        else {
    		            String fName = f.getName();
            		    StringType fileStr = StringType.create(stmt, fName);
            		    return fileStr;
    		        }
    		    })
    		    .collect(Collectors.toList());
    		listArr.setValues(fileNames);
    	} catch (IOException e) {
    		if (info.debug) {
    		    System.err.println("ERROR SIGNAL: file-component: " + e.getMessage());
    		}
    	}
    	return listArr;
    }

    public static void delete(Statement stmt, Path path) throws OperonGenericException {
        boolean success = path.toFile().delete();
        if (!success) {
            ErrorUtil.createErrorValueAndThrow(stmt, /*type*/ "COMPONENT", /*code*/ "FILE", /*message*/ "Could not delete file.");
        }
    }

    public static void rename(Statement stmt, Path path, Info info) throws OperonGenericException {
        if (info.renameToFileName == null) {
            ErrorUtil.createErrorValueAndThrow(stmt, /*type*/ "COMPONENT", /*code*/ "FILE", /*message*/ "renameToFileName was not defined.");
        }
        Path renameToFileNamePath = Paths.get(info.path + File.separator + info.renameToFileName);
        File renameToFileNameFile = renameToFileNamePath.toFile();
        boolean success = path.toFile().renameTo(renameToFileNameFile);
        if (!success) {
            ErrorUtil.createErrorValueAndThrow(stmt, /*type*/ "COMPONENT", /*code*/ "FILE", /*message*/ "Could not rename file.");
        }
    }

    public static void mkdir(Statement stmt, Info info) throws OperonGenericException {
        Path folder = Paths.get(info.path);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            ErrorUtil.createErrorValueAndThrow(stmt, /*type*/ "COMPONENT", /*code*/ "FILE", /*message*/ "Could not make directory " + folder);
        }
    }

    //
    // This can be used to read e.g. configuration or data
    // Remove if no use.
    //
    public static String readFile(String filePath) {
        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        catch (IOException e) {
            System.err.println("ERROR SIGNAL");
        }
        return contentBuilder.toString();
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
            switch (key.toLowerCase()) {
                case "\"path\"":
                    String sPath = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.path = sPath;
                    break;
                case "\"filename\"":
                    String sFn = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.fileName = sFn;
                    break;
                case "\"renametofilename\"":
                    String renameToFileName = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.renameToFileName = renameToFileName;
                    break;
                case "\"writeas\"":
                    String sWa = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.writeAs = WriteAsType.valueOf(sWa.toUpperCase());
                    break;
                case "\"createpaths\"":
                    Node createPaths_Node = pair.getEvaluatedValue();
                    if (createPaths_Node instanceof TrueType) {
                        info.createPaths = true;
                    }
                    else {
                        info.createPaths = false;
                    }
                    break;
                case "\"addlinebreak\"":
                    Node addLineBreak_Node = pair.getEvaluatedValue();
                    if (addLineBreak_Node instanceof TrueType) {
                        info.addLineBreak = true;
                    }
                    else {
                        info.addLineBreak = false;
                    }
                    break;
                case "\"prettyprint\"":
                    Node prettyPrint_Node = pair.getEvaluatedValue();
                    if (prettyPrint_Node instanceof TrueType) {
                        info.prettyPrint = true;
                    }
                    else {
                        info.prettyPrint = false;
                    }
                    break;
                case "\"debug\"":
                    Node debug_Node = pair.getEvaluatedValue();
                    if (debug_Node instanceof TrueType) {
                        info.debug = true;
                    }
                    else {
                        info.debug = false;
                    }
                    break;
                case "\"listfilenameonly\"":
                    Node listFileNameOnly_Node = pair.getEvaluatedValue();
                    if (listFileNameOnly_Node instanceof TrueType) {
                        info.listFileNameOnly = true;
                    }
                    else {
                        info.listFileNameOnly = false;
                    }
                    break;
                case "\"openoptions\"":
                    List<OpenOption> openOptions = new ArrayList<OpenOption>();
                    List<Node> options = ((ArrayType) pair.getEvaluatedValue()).getValues();
                    for (Node option : options) {
                        StringType optStr = (StringType) option.evaluate();
                        String opt = optStr.getJavaStringValue();
                        //System.out.println("OPTION: " + opt);
                        OpenOption oo = StandardOpenOption.valueOf(opt.toUpperCase());
                        openOptions.add(oo);
                    }
                    openOptions.toArray(info.openOptions);
                    break;
                case "\"method\"":
                    String method = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.method = MethodType.valueOf(method.toUpperCase());
                    break;
                default:
                    //:OFF:log.debug("file -producer: no mapping for configuration key: " + key);
                    System.err.println("file -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "FILE", "ERROR", "file -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }
    
    private class Info {
        private String path = "./";
        private String fileName = null;
        private String renameToFileName = null;
        private WriteAsType writeAs = WriteAsType.JSON;
        /*
        APPEND
            If the file is opened for WRITE access then bytes will be written to the end of the file rather than the beginning.
        CREATE
            Create a new file if it does not exist.
        CREATE_NEW
            Create a new file, failing if the file already exists.
        DELETE_ON_CLOSE
            Delete on close.
        DSYNC
            Requires that every update to the file's content be written synchronously to the underlying storage device.
        READ
            Open for read access.
        SPARSE
            Sparse file.
        SYNC
            Requires that every update to the file's content or metadata be written synchronously to the underlying storage device.
        TRUNCATE_EXISTING
            If the file already exists and it is opened for WRITE access, then its length is truncated to 0.
        WRITE
            Open for write access.
        */
        private OpenOption [] openOptions = new StandardOpenOption[] { StandardOpenOption.valueOf("WRITE"), StandardOpenOption.valueOf("CREATE")};
        private boolean createPaths = true;
        
        // Add line break ('\n' or (\r\n)) after writing the data.
        // Works only when writeAs is JSON.
        private boolean addLineBreak = false;
        private boolean prettyPrint = false;
        private boolean debug = false; // when set, the errors can be outputted for debugging purposes.
        
        // For LIST-method:
        private boolean listFileNameOnly = false;
        private MethodType method = MethodType.WRITE;
    }

    private enum WriteAsType {
        JSON, RAW, YAML, TOML;
    }

    private enum MethodType {
        WRITE, INFO, DELETE, RENAME, MKDIR, LIST;
    }

}