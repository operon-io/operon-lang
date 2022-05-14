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

package io.operon.runner.system.inputsourcedriver.file;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.FileAlreadyExistsException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

import io.operon.runner.OperonContext;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.model.streamvaluewrapper.*;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.system.BaseSystem;
import io.operon.runner.statement.FromStatement;
import io.operon.runner.processor.function.core.date.DateNow;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.Main;
import io.operon.runner.compiler.CompilerFlags;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FileSystem implements InputSourceDriver {
     // no logger 

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private long pollCounter = 0L;
    private ObjectType initialValue;
    private OperonContextManager ocm;
    private List<String> consumedFiles; // list of consumed while, for stopWhenNoNewFiles -option.
    
    public FileSystem() {
        this.consumedFiles = new ArrayList<String>();
    }
    
    public boolean isRunning() {
        return this.isRunning;
    }
    
    public void start() {
        this.start(null);
    }
    
    public OperonContextManager getOperonContextManager() {
        return this.ocm;
    }
    
    public void setOperonContextManager(OperonContextManager o) {
        this.ocm = o;
    }
    
    public void start(OperonContextManager o) {
        OperonContext ctx = null;
        //:OFF:log.debug("FileSystem.start");
        try {
            Info info = this.resolve();
            this.isRunning = true;
            if (this.getOperonContextManager() == null && o != null) {
                //:OFF:log.debug("FileSystem.start 1");
                ocm = o;
                if (info.contextManagement != null) {
                    ocm.setContextStrategy(info.contextManagement);
                }
                ctx = ocm.resolveContext("correlationId");
            }
            else if (o == null) {
                //:OFF:log.debug("FileSystem.start 2");
                ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, info.contextManagement);
            }
            
            Path folder = Paths.get(info.path);
            // If path does not exist, then create it if createPaths -options was set. Do not follow symbolic links
            if (info.createPaths && !Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) {
                //:OFF:log.debug("FileSystem.start create dir: " + folder);
                try {
                    Files.createDirectories(folder);
                } catch (UnsupportedOperationException e) {
                    System.err.println("UnsupportedOperationException :: " + e.getMessage());
                    throw e;
                } catch (FileAlreadyExistsException e) {
                    System.err.println("FileAlreadyExistsException  :: " + e.getMessage());
                    throw e;
                } catch (SecurityException e) {
                    System.err.println("SecurityException :: " + e.getMessage());
                    throw e;
                } catch (IOException e) {
                    System.err.println("IOException :: " + e.getMessage());
                    throw e;
                }
                //:OFF:log.debug("FileSystem.start dir created");
            }
            
            //:OFF:log.debug("FileSystem.start enter while");
            while (this.isRunning) {
                if (info.pollTimes != null 
                        && info.pollTimes > 0 
                        && this.getPollCounter() >= info.pollTimes ) {
                    this.isRunning = false;
                    break;
                }
                
                File folderCheck = null;
                if (info.path != null) {
                    folderCheck = new File(info.path);
                }
                
                if (info.fileName != null && info.fileName.length() > 0) {
                    //:OFF:log.debug("FileSystem.start try readlock");
                    Path path = Paths.get(info.path + File.separator + info.fileName);
                    //System.out.println("Initial file-path: " + path.toFile().getPath());
                    if (readLockFile(path.toFile().getPath(), info) != null) {
                        List<Path> pathsToFiles = new ArrayList<Path>();
                        pathsToFiles.add(path);
                        handleFrame(ocm, info, pathsToFiles);
                        this.consumedFiles.add(path.toString());
                    }
                    else {
                        System.err.println("Failed to readLock the file: " + info.fileName);
                    }
                }
                
                else {
                    if (folderCheck == null || folderCheck.isDirectory() == false) {
                        System.err.println("Directory not found. Path: " + info.path);
                        this.isRunning = false;
                    }
            
                    else {
                        //System.out.println("Directory found. Trying to consume files.");
                        this.handleFolder(info.path, info, ocm);
                    }
                }
                
                if (info.stopWhenFolderEmpty) {
                    //:OFF:log.debug("FileSystem.start stopWhenFolderEmpty");
                    if (folderCheck != null && folderCheck.isDirectory()) {
                        try (Stream<Path> entries = Files.list(Paths.get(info.path))) {
                            
                            boolean filesFound = entries
                                                    .filter(path -> path.toFile().isDirectory() == false)
                                                    .findFirst()
                                                    .isPresent();
                            
                            if (filesFound == false) {
                                this.isRunning = false;
                            }
                        }
                    }
                }
                
                else if (info.stopWhenNoNewFiles) {
                    if (folderCheck != null && folderCheck.isDirectory()) {
                        try (Stream<Path> entries = Files.list(Paths.get(info.path))) {
                            // Go through entries until there exists a new file. If
                            // all are known, then return true.
                    		List<String> fileNames = entries
                    		        .filter(path -> path.toFile().isDirectory() == false)
                    		        .map(path -> path.toString())
                    				.collect(Collectors.toList());
                            
                            boolean newFileFound = false;
                            for (String fileName : fileNames) {
                                if (this.consumedFiles.contains(fileName)) {
                                    //System.out.println("FOUND CONSUMED: " + fileName);
                                    continue;
                                }
                                else {
                                    //System.out.println("NOT CONSUMED: " + fileName);
                                    newFileFound = true;
                                    break;
                                }
                            }
                            if (newFileFound == false) {
                                this.isRunning = false;
                            }
                            //System.out.println("FILES: " + fileNames);
                            //System.out.println("CONSUMED FILES: " + this.consumedFiles);
                        }
                    }
                }
                this.pollCounter += 1;
                Thread.sleep(info.pollInterval);
            }

            if (info.sendEndSignal) {
                this.sendEndSignal(ocm);
            }
        } catch (OperonGenericException e) {
            //:OFF:log.error("Exception :: " + e.toString());
            ctx.setException(e);
        } catch (Exception ex) {
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
        }
    }
    
    private void sendEndSignal(OperonContextManager ocm) throws OperonGenericException, IOException {
        // Set the initial value into OperonContext:
        OperonContext ctx = ocm.resolveContext("correlationId");
        DefaultStatement stmt = new DefaultStatement(ctx);
        EndValueType endValue = new EndValueType(stmt);
        ctx.setInitialValue(endValue);

        // Evaluate the query against the intial value:
        OperonValue result = ctx.evaluateSelectStatement();
        ctx.outputResult(result);
    }
    
    private static boolean filterPredicate(String fileName, Info info) {
	    //
	    // Check include extensions:
	    //
	    if (info.includeExt.size() > 0) {
	        for (String includeExt : info.includeExt) {
	            int pos = fileName.lastIndexOf("." + includeExt);
	            if (pos == fileName.length() - includeExt.length() - 1) {
                    return true;
	            }
	        }
            return false;
	    }
	    //
	    // Check regexes:
	    //
	    if (info.includeRegex != null && info.includeRegex.length() > 0) {
            Pattern p = Pattern.compile(info.includeRegex);
            Matcher m = p.matcher(fileName);
            return m.matches();
	    }
	    return true;
    }
    
    // Returns true if the file can be consumed,
    // and false otherwise.
    // Uses the "changed" -strategy
    private static boolean possibleToConsume(File f, List<String> consumedFiles, Info info) throws OperonGenericException {
	    //System.out.println("possibleToConsume");
	    if (info.stopWhenNoNewFiles) {
	        if (consumedFiles.contains(f.toString())) {
	            return false;
	        }
	    }
	    
	    long fileLastModified = f.lastModified();
	    Date now = new Date();
	    long nowMillis = now.getTime();

	    // 
	    // Don't consume the file if it has just been updated.
	    // Doesn't currently check the file-length. That would require file-info repository.
	    if (nowMillis - info.changedTreshold < fileLastModified) {
	        //System.out.println("Skipping file: " + f.getName() + ", reason: file is too new, and another process might be writing it.");
	        return false;
	    }
	    else {
	        return true;
	    }
    }
    
    //
    // Tries to acquire file-lock for the file.
    // Essentially, appends .operon to file ext.
    //
    // Returns true if the file existed and was renamed or had already been renamed, false otherwise.
    private static Path readLockFile(String fileName, Info info) throws OperonGenericException, IOException {
        //System.out.println("readLockFile");
        File f = new File(fileName);
		File lockedFile = new File(fileName + ".operon");
        if (f.isDirectory() == false && f.exists()) {
            //System.out.println("Locking file");
            if (lockedFile.exists()) {
				return Paths.get(fileName + ".operon");
			}
			else {
				Path result = Files.move(Paths.get(fileName), Paths.get(fileName + ".operon")); // TODO: allow configure different extension
				//System.out.println("  Locked file: " + result);
				return result;
			}
        }
        else {
            // Not a true error, file could be consumed, and new one has not been generated:
            //System.err.println("Cannot lock file (file does not exist): " + f.getPath());
            return null;
        }
    }

    //
    // Unlock file (remove .operon -extension):
    // NOTE: filePath depends on configuration-options
    //
    private static void unlockFile(Info info, File f, String filePath, String fileName, long fileLastModified) {
        //System.out.println("unlockFile");
        //
        // Only do this, if the original file has not been updated
        // I.e. check the changed-timestamp first.
        //
        File originalFile = new File(filePath + File.separator + fileName);
        
        if (originalFile.exists() == false) {
            //System.out.println("RENAME FILE FROM " + f.getName() + " TO: " + filePath + File.separator + fileName);
            f.renameTo(new File(filePath + File.separator + fileName));
        }
        else if (originalFile.exists() == true && originalFile.lastModified() > fileLastModified) {
            // Do not rename, because original file has been updated.
            // Instead delete the temp-file:
            //System.out.println("DELETE LOCK: " + f.getName());
            f.delete();
        }
        else {
            // Failed to lock the file or new file was produced. Either case, delete the lock:
            f.delete();
        }
    }

    private void handleFolder(String folderPath, Info info, OperonContextManager ocm) throws OperonGenericException, InterruptedException {
        //System.out.println("No fileName, consuming all files from path :: " + info.path);
        //
        // Read all files from given folder.
        //
        Path path = Paths.get(folderPath);
        String [] rootPathParts = path.toString().split("/");
        
    	try (Stream<Path> walk = Files.walk(path)) {
    		List<String> fileNames = walk.map(f -> f.toString())
    				.filter(f -> {
    				    //
    				    // Test for subfolders (recursive -option):
    				    //
    				    String [] testPathParts = Paths.get(f).toString().split("/");
    				    //System.out.println("testPathParts length: " + testPathParts.length + ", " + Paths.get(f).toString());
    				    if ((info.recursive == false && testPathParts.length > rootPathParts.length + 1) 
    				        || (info.recursive && Paths.get(f).toFile().isDirectory())) {
    				        return false;
    				    }
    				    else {
    				        return true;
    				    }
    				})
    				.filter(f -> f.endsWith(".operon") == false) // readLocked files
    				.filter(f -> filterPredicate(f, info))
    				.collect(Collectors.toList());
    		
    		if (info.shuffle) {
    		    Collections.shuffle(fileNames);
    		}
    		
    		long fileCounter = 0L;
    		for (String fileName : fileNames) {
    		    //System.out.println("---------------------------------------------");
    		    //System.out.println("File: " + fileName);
    		    if (info.maxFilesPerPoll != null && (fileCounter >= info.maxFilesPerPoll)) {
    		        break;
    		    }
    		    if (fileName.equals(folderPath)) {
    		        // Do not output this, if folder is empty, then this is outputted each time the isd is triggered.
    		        //System.out.println("Resource was same as folderPath. Skipping");
    		        continue;
    		    }
    		    
    		    File f = new File(fileName);
    		    
    		    if (possibleToConsume(f, this.consumedFiles, info)) {
    		        if (readLockFile(fileName, info) != null) {
                		//Path filePath = Paths.get(folderPath + File.separator + fileName + ".operon");
                		Path filePath = Paths.get(fileName + ".operon");
                		//
                		// TODO: mark the file, so other processes know that we are trying to read from it
                		// TODO: batch-files if batch = true
                		//System.out.println("Consuming file :: " + fileName);
                		List<Path> pathsToFiles = new ArrayList<Path>();
                		pathsToFiles.add(Paths.get(fileName));
                        handleFrame(ocm, info, pathsToFiles);
                        this.consumedFiles.add(fileName);
    		        }
    		    }
    		    fileCounter += 1L;
                Thread.sleep(info.pollInterval);
    		}
    	} catch (IOException e) {
    		System.err.println("ERROR SIGNAL: file-system");
    	}
    }
    
    // @param jsonObj : wrapper for initialValue
    public void streamLines(OperonContext ctx, Info info, Path path) throws OperonGenericException, IOException {
        //System.out.println("handling streamLines");
        Statement stmt = new DefaultStatement(ctx);
        
        if (info.readAs == ReadAsType.JSON) {
            // Read the file-contents
            String fileContent = null;
            AtomicInteger lineCounter = new AtomicInteger(0);
            
            try (Stream<String> fileLinesStream = Files.lines(path)) {
                fileLinesStream.forEach(line -> {
                    lineCounter.incrementAndGet();
                    
                    if (line.isEmpty()) {
                        return;
                    }
                    try {
                        OperonValue initValue = null;
                        if (info.lwParser) {
                            initValue = JsonUtil.lwOperonValueFromString(line);
                        }
                        else {
                            if (info.index) {
                                CompilerFlags[] flags = {CompilerFlags.INDEX_ROOT};
                                initValue = JsonUtil.operonValueFromString(line, flags);
                            }
                            else {
                                initValue = JsonUtil.operonValueFromString(line);
                            }
                        }
                        
                        if (info.streamLinesWrapper) {
                            ObjectType jsonObj = new ObjectType(stmt);
                            PairType pair = new PairType(stmt);
                            pair.setPair("\"body\"", initValue);
                            jsonObj.addPair(pair);
                            
                            PairType pairLineCounter = new PairType(stmt);
                            NumberType lnNode = new NumberType(stmt);
                            lnNode.setDoubleValue((double) (lineCounter.longValue()));
                            pairLineCounter.setPair("\"lineCounter\"", lnNode);
                            jsonObj.addPair(pairLineCounter);
                            
                            // Set the initial value into OperonContext:
                            ctx.setInitialValue(jsonObj);
                        }
                        else {
                            ctx.setInitialValue(initValue);
                        }
                
                        // Evaluate the query against the intial value:
                        OperonValue result = ctx.evaluateSelectStatement();
                        ctx.outputResult(result);
                    } catch (OperonGenericException oge) {
                        System.err.println("ERROR SIGNAL: file-system: while reading file line-content: " + oge.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("ERROR SIGNAL: file-system: while reading file line-content: " + e.getMessage());
            }
        }
    }
    
    //
    // Implement the handling logic here.
    //
    public void handleFrame(OperonContextManager ocm, Info info, List<Path> pathsToFiles) throws OperonGenericException, IOException {
        //System.out.println("handleFrame");
        // Acquire OperonContext
        OperonContext ctx = ocm.resolveContext("correlationId");
        
        // 
        // TODO: Accumulate read files into this list
        //   NOTE: this is for batch-flag!
        // 
        List<OperonValue> readFiles = new ArrayList<OperonValue>();
        Path pathToFile = pathsToFiles.get(0); // until batch has been implemented
        Path path = Paths.get(pathToFile.toString() + ".operon"); // assumes that the file has been locked for consuming.
        Path pathWithoutLockExt = pathToFile;
        File f = path.toFile();
        Path onlyFileName = pathToFile.getFileName();
        
        if (f.isDirectory()) {
            // handleFrame does not handle directories.
            return;
        }
        
        if (f.exists() == false) {
            System.err.println("File does not exists: " + pathToFile.toString() + ".operon");
            return;
        }
        
        //
        // NOTE: this takes the lastModified -timestamp from the temp-file:
        //
        long fileLastModified = f.lastModified();
        
        try {
            if (info.streamLines) {
                this.streamLines(ctx, info, path);
            }
            
            else {
                Statement stmt = new DefaultStatement(ctx);
                ObjectType jsonObj = new ObjectType(stmt);
                PairType pairFileName = new PairType(stmt);
                StringType sNode = new StringType(stmt);
                sNode.setFromJavaString(pathWithoutLockExt.toFile().getName());
                pairFileName.setPair("\"fileName\"", sNode);
                jsonObj.addPair(pairFileName);
                
                PairType pairFilePath = new PairType(stmt);
                StringType spNode = new StringType(stmt);
                String sanitizedString = RawToStringType.sanitizeForStringType(pathWithoutLockExt.toFile().getPath()
                    .substring(0, pathWithoutLockExt.toFile().getPath()
                    .lastIndexOf(pathWithoutLockExt.toFile().getName()) - 1));
                spNode.setFromJavaString(sanitizedString);
                pairFilePath.setPair("\"filePath\"", spNode);
                jsonObj.addPair(pairFilePath);
                
                PairType pairFileLength = new PairType(stmt);
                NumberType lenNode = new NumberType(stmt);
                lenNode.setDoubleValue((double) (f.length()));
                pairFileLength.setPair("\"length\"", lenNode);
                jsonObj.addPair(pairFileLength);
                
                if (info.readAs == ReadAsType.JSON) {
                    // Read the file-contents
                    String fileContent = null;
                    try {
                        fileContent = new String(Files.readAllBytes(path), info.charSet);
                    } catch (Exception e) {
                        System.err.println("ERROR SIGNAL: file-system: while reading file content: " + e.getMessage());
                    }
                    if (fileContent == null) {
                        System.err.println("File not found (empty content read).");
                        return;
                    }
                    
                    OperonValue initValue = null;
                    if (info.lwParser) {
                        initValue = JsonUtil.lwOperonValueFromString(fileContent);
                    }
                    else {
                        if (info.index) {
                            CompilerFlags[] flags = {CompilerFlags.INDEX_ROOT};
                            initValue = JsonUtil.operonValueFromString(fileContent, flags);
                        }
                        else {
                            initValue = JsonUtil.operonValueFromString(fileContent);
                        }
                    }
                    
                    PairType pair = new PairType(stmt);
                    pair.setPair("\"body\"", initValue);
                    jsonObj.addPair(pair);
                }
                
                else if (info.readAs == ReadAsType.RAW) {
                    // Read the file-contents
                    String fileContent = new String(Files.readAllBytes(path), info.charSet);
                    if (fileContent == null) {
                        System.err.println("File not found (empty content read).");
                        return;
                    }
                    PairType pair = new PairType(stmt);
                    RawValue rawNode = new RawValue(stmt);
                    rawNode.setValue(fileContent.getBytes(StandardCharsets.UTF_8));
                    pair.setPair("\"body\"", rawNode);
                    jsonObj.addPair(pair);
                }
                
                else if (info.readAs == ReadAsType.STREAM) {
                    InputStream fileInputStream = new FileInputStream(path.toString());
                    PairType pair = new PairType(stmt);
                    StreamValue streamNode = new StreamValue(stmt);
                    StreamValueWrapper svw = new StreamValueInputStreamWrapper(fileInputStream);
                    // This option makes it possible to read the stream in the For-loop,
                    // but it requires that the file actually contains JSON per each line.
                    svw.setSupportsJson(true);
                    streamNode.setValue(svw);
                    pair.setPair("\"body\"", streamNode);
                    jsonObj.addPair(pair);
                }
                
                else {
                    String type = "INPUT";
                    String code = "FILE";
                    String message = "Unsupported readAs -type";
                    ErrorUtil.createErrorValueAndThrow(stmt, type, code, message);
                }
                
                // Set the initial value into OperonContext:
                ctx.setInitialValue(jsonObj);
    
                // Evaluate the query against the intial value:
                OperonValue result = ctx.evaluateSelectStatement();
                ctx.outputResult(result);
            }
            
            
            if (info.moveDone) {
                //System.out.println("Moving file to: " + info.moveDonePath + File.separator + onlyFileName.toString());
                
                // If path does not exist, then create it if createPaths -options was set. Do not follow symbolic links
                if (info.createPaths && !Files.exists(Paths.get(info.moveDonePath), LinkOption.NOFOLLOW_LINKS)) {
                    //System.out.println("Creating done-directory: " + info.moveDonePath);
                    Files.createDirectory(Paths.get(info.moveDonePath));
                }
                this.unlockFile(info, f, info.moveDonePath, onlyFileName.toString(), fileLastModified);
            }
            else {
                this.unlockFile(info, f, info.path, onlyFileName.toString(), fileLastModified);
            }
        } catch (Exception e) {
            System.err.println("FileSystem :: ERROR :: " + e.getMessage());
            if (info.moveFailed) {
                // Move to failed
                if (info.createPaths && !Files.exists(Paths.get(info.moveFailedPath), LinkOption.NOFOLLOW_LINKS)) {
                    System.out.println("Creating fail-directory: " + info.moveFailedPath);
                    Files.createDirectory(Paths.get(info.moveFailedPath));
                }
                this.unlockFile(info, f, info.moveFailedPath, onlyFileName.toString(), fileLastModified);
            }
            else {
                this.unlockFile(info, f, info.path, onlyFileName.toString(), fileLastModified);
            }
        }
    }
    
    public void requestNext() {}
    
    public void stop() {
        this.isRunning = false;
        //:OFF:log.info("Stopped");
    }
    
    public void setJsonConfiguration(ObjectType jsonConfig) { this.jsonConfiguration = jsonConfig; }
    public ObjectType getJsonConfiguration() { return this.jsonConfiguration; }
    public long getPollCounter() { return this.pollCounter; }
    
    public void setInitialValue(OperonValue iv) {
        this.initialValue = (ObjectType) iv;
    }
    
    public ObjectType getInitialValue() {
        return this.initialValue;
    }
    
    private Info resolve() throws OperonGenericException {
        List<PairType> jsonPairs = this.getJsonConfiguration().getPairs();
        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"path\"":
                    String fcPath = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.path = fcPath;
                    break;
                case "\"recursive\"":
                    Node recursiveValue = pair.getValue().evaluate();
                    if (recursiveValue instanceof FalseType) {
                        info.recursive = false;
                    }
                    else {
                        info.recursive = true;
                    }
                    break;
                case "\"filename\"":
                    String fcFileName = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.fileName = fcFileName;
                    break;
                case "\"readas\"":
                    String fcReadAs = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    try {
                        info.readAs = ReadAsType.valueOf(fcReadAs.toUpperCase());
                    } catch(Exception e) {
                        System.err.println("ERROR SIGNAL: readAs-property");
                    }
                    break;
                case "\"createpaths\"":
                    Node createPathsEvaluatedValue = pair.getValue().evaluate();
                    if (createPathsEvaluatedValue instanceof FalseType) {
                        info.createPaths = false;
                    }
                    else {
                        info.createPaths = true;
                    }
                    break;
                case "\"movedone\"":
                    Node moveDoneValue = pair.getValue().evaluate();
                    if (moveDoneValue instanceof FalseType) {
                        info.moveDone = false;
                    }
                    else {
                        info.moveDone = true;
                    }
                    break;
                case "\"movefailed\"":
                    Node moveFailedValue = pair.getValue().evaluate();
                    if (moveFailedValue instanceof FalseType) {
                        info.moveFailed = false;
                    }
                    else {
                        info.moveFailed = true;
                    }
                    break;
                case "\"batch\"":
                    Node batchValue = pair.getValue().evaluate();
                    if (batchValue instanceof FalseType) {
                        info.batch = false;
                    }
                    else {
                        info.batch = true;
                    }
                    break;
                case "\"shuffle\"":
                    Node shuffleValue = pair.getValue().evaluate();
                    if (shuffleValue instanceof FalseType) {
                        info.shuffle = false;
                    }
                    else {
                        info.shuffle = true;
                    }
                    break;
                case "\"stopwhenfolderempty\"":
                    Node stopWhenFolderEmptyValue = pair.getValue().evaluate();
                    if (stopWhenFolderEmptyValue instanceof FalseType) {
                        info.stopWhenFolderEmpty = false;
                    }
                    else {
                        info.stopWhenFolderEmpty = true;
                    }
                    break;
                case "\"stopwhennonewfiles\"":
                    Node stopWhenNoNewFilesValue = pair.getValue().evaluate();
                    if (stopWhenNoNewFilesValue instanceof FalseType) {
                        info.stopWhenNoNewFiles = false;
                    }
                    else {
                        info.stopWhenNoNewFiles = true;
                    }
                    break;
                case "\"sendendsignal\"":
                    Node sendEndSignalValue = pair.getValue().evaluate();
                    if (sendEndSignalValue instanceof FalseType) {
                        info.sendEndSignal = false;
                    }
                    else {
                        info.sendEndSignal = true;
                    }
                    break;
                case "\"streamlines\"":
                    Node streamLinesValue = pair.getValue().evaluate();
                    if (streamLinesValue instanceof FalseType) {
                        info.streamLines = false;
                    }
                    else {
                        info.streamLines = true;
                    }
                    break;
                case "\"streamlineswrapper\"":
                    Node streamLinesWrapperValue = pair.getValue().evaluate();
                    if (streamLinesWrapperValue instanceof FalseType) {
                        info.streamLinesWrapper = false;
                    }
                    else {
                        info.streamLinesWrapper = true;
                    }
                    break;
                case "\"lwparser\"":
                    Node lwParserValue = pair.getValue().evaluate();
                    if (lwParserValue instanceof FalseType) {
                        info.lwParser = false;
                    }
                    else {
                        info.lwParser = true;
                    }
                    break;
                case "\"index\"":
                    Node indexValue = pair.getValue().evaluate();
                    if (indexValue instanceof FalseType) {
                        info.index = false;
                    }
                    else {
                        info.index = true;
                    }
                    break;
                case "\"movedonepath\"":
                    String moveDonePath = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.moveDonePath = moveDonePath;
                    break;
                case "\"movefailedpath\"":
                    String moveFailedPath = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.moveFailedPath = moveFailedPath;
                    break;
                case "\"readlockstrategy\"":
                    String readLockStrategy = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.readLockStrategy = readLockStrategy;
                    break;
                case "\"includeregex\"":
                    String includeRegex = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.includeRegex = includeRegex;
                    break;
                case "\"pollinterval\"":
                    double fcPollInterval = ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    info.pollInterval = (long) fcPollInterval;
                    break;
                case "\"polltimes\"":
                    double fcPollTimes = ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    info.pollTimes = (long) fcPollTimes;
                    break;
                case "\"maxfilesperpoll\"":
                    double maxFilesPerPoll = ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    info.maxFilesPerPoll = (long) maxFilesPerPoll;
                    break;
                case "\"batchmaxsize\"":
                    double fcbatchMaxSize = ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    info.batchMaxSize = (long) fcbatchMaxSize;
                    break;
                case "\"changedtreshold\"":
                    double changedTreshold = ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    info.changedTreshold = (long) changedTreshold;
                    break;
                case "\"includeext\"":
                    List<Node> includeExtNodes = ((ArrayType) pair.getValue().evaluate()).getValues();
                    List<String> includeExtList = new ArrayList<String>();
                    for (Node incExtNode : includeExtNodes) {
                        StringType jstr = (StringType) incExtNode.evaluate();
                        includeExtList.add(jstr.getJavaStringValue());
                    }
                    info.includeExt = includeExtList;
                    break;
                case "\"charset\"":
                    String fcCharSet = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.charSet = Charset.forName(fcCharSet.toUpperCase());
                    break;
                // contextManagement is preferred option for ISD, consider before removing.
                case "\"contextmanagement\"":
                    String iContextManagementStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW) {
                        info.contextManagement = OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW;
                    }
                    else if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID) {
                        info.contextManagement = OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID;
                    }
                    else if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.SINGLETON) {
                        info.contextManagement = OperonContextManager.ContextStrategy.SINGLETON;
                    }
                    break;
                default:
                    System.err.println("file-isd: no mapping for configuration key: " + key);
            }
        }
        
        //
        // Post-processing:
        //
        
        if (info.path == null
                && info.fileName != null
                && info.pollTimes == null
                && info.moveDone == null) {
            info.pollTimes = 1L;
            info.moveDone = false;
        }
        
        if (info.pollInterval == null) {
            if (info.pollTimes != null && info.pollTimes == 1L) {
                info.pollInterval = 0L;
            }
            else {
                info.pollInterval = 1000L;
            }
        }
        
        if (info.path == null) {
            info.path = "./";
        }
        
        if (info.moveDone == null) {
            info.moveDone = true;
        }
        
        return info;
    }

    private class Info {
        private String fileName;
        private String path = null;
        private boolean recursive = false;
        private boolean createPaths = true;
        private Boolean moveDone = null;
        private boolean moveFailed = false;
        private boolean batch = false; // TODO: implement this. Should read all file-contents in one json-array: [{}, {}, {}, ...]
        private Long batchMaxSize = null; // unlimited. TODO: implement this.
        private boolean shuffle = false; // Read the files in random order.
        private boolean stopWhenFolderEmpty = false; // This is applicable only when moveDone: true
        private boolean stopWhenNoNewFiles = false; // This records the file-names that are consumed and does not consume same filename twice.
        private boolean sendEndSignal = false; // When stopping, then send the EndValueType to the OperonContext.
        private boolean streamLines = false; // sends each line for processing.
        private boolean streamLinesWrapper = false; // decide if add headers and body when streaming lines
        private boolean lwParser = false;
        private boolean index = false; // Build index of the value. This could speed up the query when accessing the same object multiple times.
        private String moveDonePath = ".done";
        private String moveFailedPath = ".failed";
        private String readLockStrategy = "changed"; // changed = check changed timestamp, markerfile = create markerfile where filename is told, prefix = prefix file which is being read
        private ReadAsType readAs = ReadAsType.JSON; // json  | raw | stream
        private Long pollInterval = null;
        private Long pollTimes = null; // unlimited
        private Long maxFilesPerPoll = null; // unlimited
        private long changedTreshold = 1000L; // if file is new than now - changedTreshold, then consume it (applies for changed -readlock-strategy)
        private List<String> includeExt = new ArrayList<String>(); // e.g. ["json", "txt"] --> includes files with given extension: 1.json, data.txt would be included.
        private String includeRegex = null; // Use regex-expression to match the filename
        private Charset charSet = Main.defaultCharset; // byte encoding scheme for read bytes
        
        // contextManagement is preferred option for ISD
        private OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.SINGLETON;
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