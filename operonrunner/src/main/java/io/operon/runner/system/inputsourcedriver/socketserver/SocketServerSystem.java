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

package io.operon.runner.system.inputsourcedriver.socketserver;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ClassNotFoundException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import io.operon.runner.Main;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.GzipUtil;
import io.operon.runner.system.BaseSystem;
import io.operon.runner.statement.FromStatement;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class SocketServerSystem implements InputSourceDriver {
    private static Logger log = LogManager.getLogger(SocketServerSystem.class);

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private long pollCounter = 0L;
    private OperonContextManager ocm;
    private static ServerSocket server;
    
    public SocketServerSystem() {}
    
    public boolean isRunning() {
        return this.isRunning;
    }
    
    public OperonContextManager getOperonContextManager() {
        return this.ocm;
    }
    
    public void setOperonContextManager(OperonContextManager o) {
        this.ocm = o;
    }
    
    public void start(OperonContextManager o) {
        OperonContext ctx = null;
        try {
            Info info = this.resolve();
            this.isRunning = true;
            if (this.getOperonContextManager() == null && o != null) {
                //System.out.println("assign contextManager");
                o.setContextStrategy(info.contextManagement);
                ocm = o;
                ctx = ocm.resolveContext("correlationId");
            }
            else if (o == null) {
                ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, info.contextManagement);
            }
            ocm.setRemoveUnusedStates(info.removeUnusedStates, info.removeUnusedStatesAfterMillis, info.removeUnusedStatesInThread);
            InetAddress address = InetAddress.getByName(info.host);
            server = new ServerSocket(info.port, info.backlogSize, address);
            handleFrame(ocm, info);
        } catch (OperonGenericException e) {
            log.error("Exception :: " + e.toString());
            ctx.setException(e);
        } catch (Exception ex) {
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
        }
    }
    
    public void handleFrame(OperonContextManager ocm, Info info) throws OperonGenericException, IOException {
        String str = "";
        String cv = null;
        String headers = null;
        
        if (info.debug) {
            this.debug("Waiting for the client request: " + info.host + ":" + info.port);
        }
        
        do {
            try {
                //creating socket and waiting for client connection
                Socket socket = server.accept();
                DataInputStream ois = new DataInputStream(socket.getInputStream());
                
                String message = (String) ois.readUTF();
                
                if (info.debug) {
                    this.debug("Message Received: " + message);
                }

                // Message structure:
                //  <headers length in bytes> 'H' <headers> <payload>
                //  * The '<', '>' and "'" are not part of the actual message.
                //  * H tells when the headers length stops. This is required in the input.

                int headersIndex = message.indexOf("H"); // H tells when the headers length stops. This is required in the input.
                if (headersIndex == -1) {
                    ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "SOCKET_SERVER", "Invalid headers: missing length terminator.");
                }
                
                boolean gzipped = false;
                char writeAsType = message.charAt(headersIndex + 2);
                
                //System.out.println("gzipped flag :: " + message.charAt(headersIndex + 1));
                if (message.charAt(headersIndex + 1) == '1') {
                    gzipped = true;
                }
                
                if (headersIndex > 0) {
                    int headersLength = Integer.valueOf(message.substring(0, headersIndex));
                    
                    // headersIndex + 3 because first byte after H is flag for gzipped and second for data-type
                    headers = message.substring(headersIndex + 3, headersIndex + 3 + headersLength);
                    if (info.debug) {
                        this.debug("HEADERS=" + headers);
                    }
                    cv = message.substring(headersIndex + 3 + headersLength, message.length());
                }
                else {
                    cv = message.substring(3, message.length());
                }
                
                if (gzipped) {
                    if (info.debug) {
                        this.debug("Decompressing:" + cv);
                    }
                    //
                    // FIXME: we use Base64 decoder to get the correct byte-encoding,
                    //        should have better method.
                    //
                    byte[] receivedBytes = RawValue.base64ToBytes(cv.getBytes()); // cv.getBytes(StandardCharsets.UTF_8);
                    // Reinterpret the receivedBytes as UTF-8:
                    //String utf8Bytes = new String(receivedBytes, StandardCharsets.UTF_8);
                    cv = new String(GzipUtil.decompress(receivedBytes));
                }
                
                if (info.debug) {
                    this.debug("CV=" + cv);
                }

                // Evaluate:
                ObjectType jvalHeaders = null;
                if (headers != null) {
                    jvalHeaders = (ObjectType) JsonUtil.lwOperonValueFromString(headers).evaluate();
                }
                
                OperonContext tmpCtx = new OperonContext();
                Statement stmt = new DefaultStatement(tmpCtx);
                ObjectType request = new ObjectType(stmt);
                
                Address replyAddress = null;
                boolean requireReply = true;
                String correlationId = "";
                
                if (jvalHeaders != null) {
                    PairType headersPair = new PairType(jvalHeaders.getStatement());
                    headersPair.setPair("\"headers\"", jvalHeaders);
                    request.addPair(headersPair);
                    
                    //
                    // Check the replyHost and replyPort from received headers + other configs (e.g. correlationId)
                    //
                    for (PairType hdrPair: jvalHeaders.getPairs()) {
                        String hdrKey = hdrPair.getKey();
                        switch (hdrKey.toLowerCase()) {
                            case "\"replyhost\"":
                                if (replyAddress == null) {
                                    replyAddress = new Address();
                                }
                                String host = ((StringType) hdrPair.getValue().evaluate()).getJavaStringValue();
                                replyAddress.host = host;
                                break;
                            case "\"replyport\"":
                                if (replyAddress == null) {
                                    replyAddress = new Address();
                                }
                                Node portNode = hdrPair.getValue().evaluate();
                                int portInt = (int) ((NumberType) hdrPair.getValue().evaluate()).getDoubleValue();
                                if (portInt < -1) {
                                    ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "replyPort must be >= 0");
                                }
                                else if (portInt > 65535) {
                                    ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "replyPort must be <= 65535");
                                }
                                replyAddress.port = portInt;
                                break;
                            case "\"correlationid\"":
                                correlationId = ((StringType) hdrPair.getValue().evaluate()).getJavaStringValue();
                                break;
                            case "\"requirereply\"":
                                Node requireReplyValue = hdrPair.getValue().evaluate();
                                if (requireReplyValue instanceof FalseType) {
                                    requireReply = false;
                                }
                                else {
                                    requireReply = true;
                                }
                                break;
                            default:
                                System.err.println("WARNING: unrecognized header-option: " + hdrKey);
                                break;
                        }
                    }
                }
                
                OperonContext ctx = ocm.resolveContext(correlationId);
                OperonValue jvalCv = null;
                
                if (writeAsType == '0') {
                    jvalCv = JsonUtil.lwOperonValueFromString(cv);
                }
                
                else {
                    RawValue raw = new RawValue(stmt);
                    raw.setValue(cv.getBytes());
                    jvalCv = raw;
                }
                
                PairType bodyPair = new PairType(jvalCv.getStatement());
                bodyPair.setPair("\"body\"", jvalCv);
                request.addPair(bodyPair);
                
                OperonValue result = null;
                
                ctx.setInitialValue(request);
                try {
                    result = ctx.evaluateSelectStatement();                    
                } catch (OperonGenericException oge) {
                    // catch exception so we can set the Fault-flag,
                    // otherwise caller wouldn't receive anything.
                    result = new ErrorValue(jvalCv.getStatement());
                }
                
                if (info.debug) {
                    this.debug("RESULT :: " + result.toString());
                }
                
                DataOutputStream oos = null;
                if (replyAddress == null) {
                    if (requireReply == true) {
                        if (info.debug) {this.debug("sending reply");}
                        oos = new DataOutputStream(socket.getOutputStream());
                        
                        // If ErrorValue, then we must set the fault-flag.
                        if (result instanceof ErrorValue) {
                            oos.writeUTF("F");
                        }
                        else {
                            oos.writeUTF(result.toString());
                        }
                        ois.close();
                        oos.close();
                    }
                    socket.close();
                }
                else {
                    if (info.debug) {this.debug("sending reply to " + replyAddress.host + ":" + replyAddress.port);}
                    Socket socketReply = new Socket(replyAddress.host, replyAddress.port); // NOTE: this throws if connection cannot be established
                    oos = new DataOutputStream(socketReply.getOutputStream());
                    
                    // If ErrorValue, then we must set the fault-flag.
                    if (result instanceof ErrorValue) {
                        oos.writeUTF("F");
                    }
                    
                    else {
                        // remove headers so replyHost and replyPort won't be sent again.
                        // use the correct Operon-protocol to send the message (this also ensures that the headers are removed)
                        // retain the correlationId
                        
                        String headerData = "{\"correlationId\":\"" + correlationId + "\",\"requireReply\":false}";
                        String dataOut = String.valueOf(headerData.length()) + "H" + // Length of headers (contains correlationId)
                                         headerData + // Headers
                                         result.toString(); // The actual value that is sent
                        if (info.debug) {
                            this.debug("DATA_OUT=" + dataOut);
                        }
                        //oos.write(byte[] b, int off, int len);
                        oos.writeUTF(dataOut);
                    }
                    ois.close();
                    oos.close();
                    socketReply.close();
                }
            } catch (Exception e) {
                if (info.debug) {
                    this.debug("ERROR: operon: " + e.getMessage());
                }
            }
        } while(!str.equals(info.stopWord));
        this.isRunning = false;
    }
    
    private void debug(String value) {
        Date now = new Date();
        long millis = now.getTime();
        System.out.println(millis + " :: " + value);
    }
    
    public void requestNext() {}
    
    public void stop() {
        this.isRunning = false;
        log.info("Stopped");
    }
    
    public void setJsonConfiguration(ObjectType jsonConfig) { this.jsonConfiguration = jsonConfig; }
    public ObjectType getJsonConfiguration() { return this.jsonConfiguration; }
    public long getPollCounter() { return this.pollCounter; }
    
    private Info resolve() throws OperonGenericException {
        List<PairType> jsonPairs = this.getJsonConfiguration().getPairs();
        
        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"host\"":
                    String host = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.host = host;
                    break;
                case "\"port\"":
                    Node portNode = pair.getValue().evaluate();
                    int portInt = (int) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    if (portInt < -1) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "port must be >= 0");
                    }
                    else if (portInt > 65535) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "port must be <= 65535");
                    }
                    info.port = portInt;
                    break;
                case "\"backlogsize\"":
                    Node backlogSizeNode = pair.getValue().evaluate();
                    int backlogSizeInt = (int) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    if (backlogSizeInt < -1) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "backlogSize must be >= 0");
                    }
                    info.backlogSize = backlogSizeInt;
                    break;
                case "\"debug\"":
                    Node debugValue = pair.getValue().evaluate();
                    if (debugValue instanceof FalseType) {
                        info.debug = false;
                    }
                    else {
                        info.debug = true;
                    }
                    break;
                case "\"removeunusedstates\"":
                    Node removeUnusedStatesValue = pair.getValue().evaluate();
                    if (removeUnusedStatesValue instanceof FalseType) {
                        info.removeUnusedStates = false;
                    }
                    else {
                        info.removeUnusedStates = true;
                    }
                    break;
                case "\"removeunusedstatesinthread\"":
                    Node removeUnusedStatesInThreadValue = pair.getValue().evaluate();
                    if (removeUnusedStatesInThreadValue instanceof FalseType) {
                        info.removeUnusedStatesInThread = false;
                    }
                    else {
                        info.removeUnusedStatesInThread = true;
                    }
                    break;
                case "\"removeunusedstatesaftermillis\"":
                    Node removeUnusedStatesAfterMillisNode = pair.getValue().evaluate();
                    long removeUnusedStatesAfterMillisLong = (long) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    if (removeUnusedStatesAfterMillisLong < 0L) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "removeUnusedStatesAfterMillis must be >= 0");
                    }
                    info.removeUnusedStatesAfterMillis = removeUnusedStatesAfterMillisLong;
                    break;
                case "\"contextstrategy\"":
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
                    System.out.println("SocketServer: no mapping for configuration key: " + key);
            }
        }
        return info;
    }
    
    private class Info {
        private String stopWord = "EXIT";
        private boolean debug = false;
        //
        // When using REUSE_BY_CORRELATION_ID, then the State -objects may accumulate into
        // memory. We can remove those that were not accessed within given period.
        //
        private boolean removeUnusedStates = false;
        //
        // When removing old State-objects, we can use a background Thread to do this
        // job. In some systems it may be better to do the removal without threads.
        //
        private boolean removeUnusedStatesInThread = false;
        //
        // This is the period of inaccess. States that have not been accessed within
        // this period will get removed if "removeUnusedStates" is set to true.
        //
        private long removeUnusedStatesAfterMillis = 60000L;
        private String host = "localhost";
        private int port = 8081;
        
        // The backlog argument is the requested maximum number of pending connections on the socket. 
        // Its exact semantics are implementation specific. In particular, an implementation may impose a 
        // maximum length or may choose to ignore the parameter altogther. The value provided should be greater than 0. 
        // If it is less than or equal to 0, then an implementation specific default will be used.
        private int backlogSize = 0;
        private OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID;
    }
    
    private class Address {
        private String host = null;
        private Integer port = null;
    }
}