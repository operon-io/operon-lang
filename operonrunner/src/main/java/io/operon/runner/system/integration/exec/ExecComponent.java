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

package io.operon.runner.system.integration.exec;

import io.operon.runner.OperonContext;
import io.operon.runner.util.RandomUtil;

import java.util.Date;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.Context;
import io.operon.runner.processor.function.core.string.StringToRaw;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.model.streamvaluewrapper.*;
import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.ByteArrayInputStream;
import java.io.PipedOutputStream;

import java.io.ByteArrayOutputStream;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ExecuteException;

import java.net.Socket;

import org.apache.logging.log4j.LogManager;

//
// NOTE: It is difficult to create any complex commands with this.
//       One possibility is to wrap those inside shell-files and execute those with exec.
//
public class ExecComponent extends BaseComponent implements IntegrationComponent {
     // no logger 

    public ExecComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            Info info = this.resolve(currentValue);
            Context ctx = currentValue.getStatement().getOperonContext();
            String command = info.command;
            
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                
                //
                // CommandLine.parse: the first element becomes the executable, the rest the arguments
                //
                CommandLine cmdLine = CommandLine.parse(command);
                
                DefaultExecutor exec = new DefaultExecutor();
                PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
                exec.setStreamHandler(streamHandler);
                int resultStatusCode = exec.execute(cmdLine);
                
                ObjectType result = new ObjectType(currentValue.getStatement());
              
                NumberType statusNum = new NumberType(currentValue.getStatement());
                statusNum.setDoubleValue((double) (resultStatusCode));
                statusNum.setPrecision((byte) 0);
                PairType statusPair = new PairType(currentValue.getStatement());
                statusPair.setPair("\"status\"", statusNum);
                result.addPair(statusPair);
                
                OperonValue resultBody = null;
                
                if (info.readAs == ReadAsType.STRING) {
                    String resultStr = outputStream.toString();
                    resultStr = RawToStringType.sanitizeForStringType(resultStr);
                    resultBody = new StringType(currentValue.getStatement());
                    ((StringType) resultBody).setFromJavaString(resultStr);
                }
                else if (info.readAs == ReadAsType.JSON) {
                    String resultStr = outputStream.toString();
                    resultBody = JsonUtil.operonValueFromString(resultStr);
                }
                else if (info.readAs == ReadAsType.RAW) {
                    String resultStr = outputStream.toString();
                    RawValue rawValue = new RawValue(currentValue.getStatement());
                    rawValue.setValue(resultStr.getBytes());
                    resultBody = rawValue;
                }
                else if (info.readAs == ReadAsType.STREAM) {
                    //
                    // Credits: https://stackoverflow.com/questions/5778658/how-to-convert-outputstream-to-inputstream
                    //
                    PipedInputStream is = new PipedInputStream();
                    final PipedOutputStream out = new PipedOutputStream(is);
                    
                    //
                    // Create thread, so stream consumer can read in parallel
                    //
                    ((Runnable) () -> {
                        try {
                            outputStream.writeTo(out);
                        } catch (IOException ioe) {
                            System.err.println("exec -producer: error while piping stream.");
                        }
                        finally {
                            // close the PipedOutputStream here because we're done writing data
                            // once this thread has completed its run
                            if (out != null) {
                                // close the PipedOutputStream cleanly
                                try {
                                    out.close();
                                } catch (IOException ioe) {
                                    System.err.println("exec -producer: error while trying to close the stream.");
                                }
                            }
                        }
                    }).run();
                    
                    StreamValue streamNode = new StreamValue(currentValue.getStatement());
                    StreamValueWrapper svw = new StreamValuePipedInputStreamWrapper(is);
                    svw.setSupportsJson(false);
                    streamNode.setValue(svw);
                    resultBody = streamNode;
                }
                
                PairType bodyPair = new PairType(currentValue.getStatement());
                bodyPair.setPair("\"body\"", resultBody);
            
                result.addPair(bodyPair);
                return result;
            } catch (ExecuteException ex) {
                throw new OperonComponentException(ex.getMessage());
            } catch (IOException ex) {
                throw new OperonComponentException(ex.getMessage());
            }
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        }
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
            pair.getStatement().setCurrentValue(currentValueCopy);
            switch (key.toLowerCase()) {
                case "\"command\"":
                    Node cmdNode = pair.getEvaluatedValue();
                    String cmdStr = ((StringType) cmdNode).getJavaStringValue();
                    //System.out.println("COMMAND: " + cmdStr);
                    info.command = cmdStr;
                    break;
                case "\"readas\"":
                    String sRa = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    try {
                        info.readAs = ReadAsType.valueOf(sRa.toUpperCase());
                    } catch(Exception e) {
                        System.err.println("ERROR SIGNAL: invalid readAs-property in exec-component");
                    }
                    break;
                default:
                    //:OFF:log.debug("exec -producer: no mapping for configuration key: " + key);
                    System.err.println("exec -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "EXEC", "ERROR", "exec -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private String command = "ls";
        private ReadAsType readAs = ReadAsType.STRING;
    }

    private enum ReadAsType {
        STRING("string"), JSON("json"), RAW("raw"), STREAM("stream");
        private String readAsType = "string";
        ReadAsType(String type) {
            this.readAsType = type;
        }
        public String getReadAsType() { return this.readAsType; }
    }

}