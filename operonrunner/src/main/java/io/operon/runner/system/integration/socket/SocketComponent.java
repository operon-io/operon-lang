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

package io.operon.runner.system.integration.socket;

import io.operon.runner.OperonContext;
import io.operon.runner.util.RandomUtil;

import java.util.Date;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
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
import io.operon.runner.util.GzipUtil;
import io.operon.runner.Context;
import io.operon.runner.processor.function.core.string.StringToRaw;

import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import java.net.Socket;
import java.net.SocketException;

import org.apache.logging.log4j.LogManager;

//
// Also known as "operon" -component
//
public class SocketComponent extends BaseComponent implements IntegrationComponent {
    private static Logger log = LogManager.getLogger(SocketComponent.class);

    public SocketComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            Info info = this.resolve(currentValue);
            
            try {
                OperonValue result = currentValue;

                Socket s = new Socket(info.host, info.port); // NOTE: this throws if connection cannot be established
                if (info.soTimeout > 0) {
                    s.setSoTimeout(info.soTimeout);
                }
                if (info.keepAlive != null) {
                    s.setKeepAlive(info.keepAlive);
                }
                if (info.reuseAddress != null) {
                    s.setReuseAddress(info.reuseAddress);
                }
                
                s.setTcpNoDelay(!info.useTcpNoDelay);
                
                
                DataOutputStream dout = new DataOutputStream(s.getOutputStream());
                String currentValueStr = currentValue.evaluate().toString();
                
                boolean gzipped = false;
                
                if (info.gzip != null && info.gzip == true) {
                    byte [] bytesToCompress = currentValueStr.getBytes(StandardCharsets.UTF_8);
                    
                    // Reinterpret the bytes as UTF-8:
                    //String stringToCompress = new String(bytesToCompress, StandardCharsets.UTF_8);
                    
                    //System.out.println("Compressing: " + stringToCompress);
                    
                    byte[] compressed = GzipUtil.compress(bytesToCompress);
                    
                    //
                    // NOTE: uses Base64-encoder to get it correctly encoded/decoded.
                    //       This will make it much longer than necessary.
                    //       Try to find a fix. Problem root-cause probably in the DataOutputStream's writeUTF, which modifies the encoding.
                    //
                    currentValueStr = RawValue.bytesToBase64(compressed, false); //  new String(compressed, StandardCharsets.UTF_8);
                    
                    //
                    // See if gzip works... TODO: add unit-tests!
                    //
                    //System.out.println("Compressed="+currentValueStr);
                    //byte[] b = RawValue.base64ToBytes(currentValueStr.getBytes());
                    
                    //System.out.println("is compressed: " + GzipUtil.isCompressed(b));
                    
                    //System.out.println("Decompressed="+new String(GzipUtil.decompress(b)));
                    
                    gzipped = true;
                }
                
                if (info.headers != null && info.sendHeaders) {
                    // FIXME: info.headers.evaluate().toString() --> 
                    String headersStr = info.headers.evaluate().toString();
                    
                    StringBuilder dataOut = new StringBuilder();
                    dataOut.append(headersStr.length());
                    if (gzipped) {
                        dataOut.append("H1"); // H=headers' length terminator. 1=gzipped.
                    }
                    else {
                        dataOut.append("H0"); // H=headers' length terminator. 0=not gzipped.
                    }
                    
                    if (info.writeAs == WriteAsType.JSON) {
                        dataOut.append("0");
                    }
                    
                    else if (info.writeAs == WriteAsType.RAW) {
                        dataOut.append("1");
                    }
                    
                    dataOut.append(headersStr); // headers
                    dataOut.append(currentValueStr); // The actual value that is sent (not configs);
                    
                    if (info.debug) {
                        System.out.println("DATA_OUT=" + dataOut.toString());
                    }
                    //dout.write(dataOut.getBytes(), 0, dataOut.getBytes().length);
                    //dout.writeBytes(dataOut.toString());
                    dout.writeUTF(dataOut.toString());
                }
                else {
                    StringBuilder dataOut = new StringBuilder();
                    dataOut.append("H");
                    if (gzipped) {
                        dataOut.append("1");
                    }
                    else {
                        dataOut.append("0");
                    }
                    
                    if (info.writeAs == WriteAsType.JSON) {
                        dataOut.append("0");
                    }
                    
                    else if (info.writeAs == WriteAsType.RAW) {
                        dataOut.append("1");
                    }
                    
                    dataOut.append(currentValueStr);
                    if (info.debug) {
                        System.out.println("DATA_OUT=" + dataOut.toString());
                    }
                    //dout.writeBytes(dataOut.toString());
                    //dout.write(dataOut.toString().getBytes(), 0, dataOut.toString().getBytes().length);
                    dout.writeUTF(dataOut.toString());
                }

                //
                // Read the response, unless option "outOnly" was set to true.
                //
                if (info.outOnly == false) {
                    DataInputStream ois = new DataInputStream(s.getInputStream());
                    // We need to read as String because object-references are not fully intact.
                    //String response = (String) ois.readObject(); // read response
                    String response = (String) ois.readUTF(); // read response
                    ois.close();
                    
                    //System.out.println("READING RESPONSE :: " + response);
                    // Check if FAULT:
                    if (response.charAt(0) == 'F') {
                        //System.out.println("FAULT-RESPONSE");
                        throw new Exception("Component has set the fault-flag.");
                    }
                    
                    else {
                        result = JsonUtil.lwOperonValueFromString(response);
                    }
                }
                
                dout.flush();
                dout.close();
                
                s.close();
                Thread.sleep(100);
                
                return result;
            } catch (SocketException e) {
                return ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "SOCKET_ERROR", e.getMessage());
            } catch (IOException e) {
                return ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "ERROR", e.getMessage());
            } catch (InterruptedException e) {
                return ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "INTERRUPTED", e.getMessage());
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "ERROR", e.getMessage());
            }
        } catch (OperonGenericException oge) {
            throw new OperonComponentException(oge.getErrorMessage());
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
            pair.getStatement().setCurrentValue(currentValueCopy2);
            switch (key.toLowerCase()) {
                case "\"debug\"":
                    OperonValue debugNode = pair.getEvaluatedValue();
                    if (debugNode instanceof TrueType) {
                        info.debug = true;
                    }
                    else {
                        info.debug = false;
                    }
                    break;
                case "\"host\"":
                    OperonValue socketHostNode = pair.getEvaluatedValue();
                    String socketHostStr = ((StringType) socketHostNode).getJavaStringValue();
                    info.host = socketHostStr;
                    break;
                case "\"port\"":
                    OperonValue socketPortNode = pair.getEvaluatedValue();
                    int socketPortInt = (int) ((NumberType) socketPortNode).getDoubleValue();
                    if (socketPortInt < -1) {
                        ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "CONFIGURATION", "port must be >= 0");
                    }
                    else if (socketPortInt > 65535) {
                        ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "CONFIGURATION", "port must be <= 65535");
                    }
                    info.port = socketPortInt;
                    break;
                case "\"writeas\"":
                    StringType writeAsTypeNode = (StringType) pair.getEvaluatedValue();
                    String writeAsTypeStr = ((StringType) writeAsTypeNode).getJavaStringValue();
                    info.writeAs = WriteAsType.valueOf(writeAsTypeStr.toUpperCase());
                    break;
                case "\"sotimeout\"":
                    NumberType soTimeoutNode = (NumberType) pair.getEvaluatedValue();
                    int soTimeoutInt = (int) ((NumberType) soTimeoutNode).getDoubleValue();
                    info.soTimeout = soTimeoutInt;
                    break;
                case "\"binaryencoding\"":
                    String sBinaryEncoding = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.binaryEncoding = sBinaryEncoding;
                    break;
                case "\"keepalive\"":
                    OperonValue keepAliveNode = pair.getEvaluatedValue();
                    if (keepAliveNode instanceof TrueType) {
                        info.keepAlive = true;
                    }
                    else {
                        info.keepAlive = false;
                    }
                    break;
                case "\"reuseaddress\"":
                    OperonValue reuseAddressNode = pair.getEvaluatedValue();
                    if (reuseAddressNode instanceof TrueType) {
                        info.reuseAddress = true;
                    }
                    else {
                        info.reuseAddress = false;
                    }
                    break;
                case "\"outonly\"":
                    OperonValue outOnlyNode = pair.getEvaluatedValue();
                    if (outOnlyNode instanceof TrueType) {
                        info.outOnly = true;
                    }
                    else {
                        info.outOnly = false;
                    }
                    break;
                case "\"headers\"":
                    //System.out.println("info headers resolve");
                    OperonValue headersNode = pair.getEvaluatedValue();
                    ObjectType headersObj = (ObjectType) headersNode.evaluate();
                    //Address ra = new Address();
                    //
                    // Just validate that the types are correct and document what are possible to send.
                    //
                    for (PairType headersPair : headersObj.getPairs()) {
                        String hKey = headersPair.getKey();
                        currentValueCopy = currentValue;
                        headersPair.getStatement().setCurrentValue(currentValueCopy);
                        switch (hKey.toLowerCase()) {
                            case "\"replyhost\"":
                                StringType raSocketHostNode = (StringType) headersPair.getEvaluatedValue();
                                String raSocketHostStr = ((StringType) raSocketHostNode).getJavaStringValue();
                                info.outOnly = true;
                                break;
                            case "\"replyport\"":
                                NumberType raSocketPortNode = (NumberType) headersPair.getEvaluatedValue();
                                int raSocketPortInt = (int) ((NumberType) raSocketPortNode).getDoubleValue();
                                if (raSocketPortInt < -1) {
                                    ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "CONFIGURATION", "replyPort must be >= 0");
                                }
                                else if (raSocketPortInt > 65535) {
                                    ErrorUtil.createErrorValueAndThrow(null, "COMPONENT", "CONFIGURATION", "replyPort must be <= 65535");
                                }
                                info.outOnly = true;
                                break;
                            case "\"correlationid\"":
                                //System.out.println("Resolve correlationid");
                                StringType correlationIdNode = (StringType) headersPair.getEvaluatedValue();
                                //System.out.println(">> Resolve correlationid 1");
                                //System.out.println(">> Resolve correlationid 1 :: " + correlationIdNode);
                                String correlationIdStr = ((StringType) correlationIdNode).getJavaStringValue();
                                //System.out.println(">> Resolve correlationid 2");
                                break;
                            default:
                                System.err.println("WARNING: unrecognized option: " + hKey);
                                break;
                        }
                    }
                    //System.out.println("info headers resolve DONE");
                    info.headers = headersObj;
                    break;
                case "\"sendheaders\"":
                    OperonValue sendHeadersNode = pair.getEvaluatedValue();
                    if (sendHeadersNode instanceof TrueType) {
                        info.sendHeaders = true;
                    }
                    else {
                        info.sendHeaders = false;
                    }
                    break;
                case "\"usetcpnodelay\"":
                    OperonValue useTcpNoDelayNode = pair.getEvaluatedValue();
                    if (useTcpNoDelayNode instanceof TrueType) {
                        info.useTcpNoDelay = true;
                    }
                    else {
                        info.useTcpNoDelay = false;
                    }
                    break;
                case "\"gzip\"":
                    OperonValue gzipNode = pair.getEvaluatedValue();
                    if (gzipNode instanceof TrueType) {
                        info.gzip = true;
                    }
                    else {
                        info.gzip = false;
                    }
                    break;
                default:
                    log.debug("operon -producer: no mapping for configuration key: " + key);
                    System.err.println("operon -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "OPERON", "ERROR", "operon -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private boolean debug = false;
        private String host = "localhost";
        private int port = 8081;
        private int soTimeout = 0; // socketTimeout
        private WriteAsType writeAs = WriteAsType.JSON;

        private Boolean keepAlive = null;
        private Boolean reuseAddress = null;
        private boolean outOnly = false; // NOTE: if headers.replyHost or replyPort has been set, then outOnly will be automatically set to true.
        private boolean sendHeaders = true;
        private boolean useTcpNoDelay = true; // set if use Naggle algorithm (causes small delays when optimizing bandwith) --> NOTE: TRUE=don't use, FALSE=use.
        private Boolean gzip = null;
        
        private ObjectType headers = null;
        
        // If this option is true, then other options are ignored.
        // Tries to cast the current-value as RawValue.
        
        private String binaryEncoding = "utf-8"; // null = none, print as raw. UTF-8
    }

    private enum WriteAsType {
        JSON("json"), RAW("raw");
        private String writeAsType = "json";
        WriteAsType(String type) {
            this.writeAsType = type;
        }
        public String getWriteAsType() { return this.writeAsType; }
    }

}