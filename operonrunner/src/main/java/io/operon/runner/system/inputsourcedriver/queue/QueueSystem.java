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

package io.operon.runner.system.inputsourcedriver.queue;

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
import java.time.Duration;

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

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

// 
// To test:
// 127.0.0.1:6379> RPUSH foo 111 222 333
// RPUSH '"bar"'
// 
// Note: when using the responseChannel, the value must be serializable into Java-String.
//
public class QueueSystem implements InputSourceDriver {
     // no logger 

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private long pollCounter = 0L;
    private OperonContextManager ocm;
    
    public QueueSystem() {}
    
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
            String key = info.key;
            if (this.getOperonContextManager() == null && o != null) {
                //System.out.println("assign contextManager");
                o.setContextStrategy(info.contextManagement);
                ocm = o;
                ctx = ocm.resolveContext(key);
            }
            else if (o == null) {
                ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, info.contextManagement);
            }
            
            final JedisPoolConfig poolConfig = buildPoolConfig(info);
            JedisPool jedisPool = null; 
            
            if (info.password == null) {
                jedisPool = new JedisPool(poolConfig, info.host, info.port);
            }
            else {
                jedisPool = new JedisPool(poolConfig, info.host, info.port, info.user, info.password);
            }

            boolean connected = false;
            long reConnectionAttempt = 1;
            
            while (connected == false) {
                try (Jedis jedis = jedisPool.getResource()) {
                    System.out.println("Connected.");
                    connected = true;
                    // do operations with jedis resource
                    while (this.isRunning) {
                        //System.out.println("BLPOP");
                        List<String> data = jedis.blpop(info.timeout, key);
                        //
                        // null if timeout has been triggered.
                        //
                        if (data == null) {
                            continue;
                        }
                        //System.out.println("  GOT data: " + data);
                        if (info.batch == false) {
                            //System.out.println("1: NO Batch");
                            this.handleFrame(ocm, info, jedisPool, key, data);
                        }
                        else {
                            if (info.maxBatchSize == null) {
                                System.out.println("Batch && batchSize == null");
                                this.handleFrame(ocm, info, jedisPool, key, data);
                            }
                            else {
                                //System.out.println("Batch and batchsize = " + info.maxBatchSize);
                                int dataIndex = 0;
                                List<String> frame = new ArrayList<String>();
                                for (int i = 0; i <= data.size(); i ++) {
                                    frame.add(data.get(i));
                                    dataIndex += 1;
                                    if (dataIndex >= info.maxBatchSize) {
                                        this.handleFrame(ocm, info, jedisPool, key, frame);
                                        frame.clear();
                                        frame.add(key);
                                        dataIndex = 0;
                                    }
                                }
                            }
                        }
                        
                        if (info.timeBetween != null) {
                            Thread.sleep(info.timeBetween);
                        }
                    }
                } catch (JedisConnectionException jce) {
                    if (reConnectionAttempt == 1 || reConnectionAttempt % 10 == 0) {
                        System.err.println("Subscribe: could not connect to Redis. Trying to reconnect: " + reConnectionAttempt);
                    }
                    reConnectionAttempt += 1;
                    connected = false;
                    Thread.sleep(1000);
                }
            }
        } catch (OperonGenericException e) {
            //System.err.println("2 ERROR :: " + e.getMessage());
            //:OFF:log.error("Exception :: " + e.toString());
            ctx.setException(e);
        } catch (IOException ex) {
            //System.err.println("3 ERROR :: " + ex.getMessage());
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
        } catch (InterruptedException ex) {
            System.err.println("Interrupted");
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
        }
    }
    
    private JedisPoolConfig buildPoolConfig(Info info) {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        return poolConfig;
    }
    
    public static void sendResponse(JedisPool jedisPool, Info info, String responseChannel, String value) throws InterruptedException {
        boolean connected = false;
        long reConnectionAttempt = 1;
        
        while (connected == false) {
            try (Jedis jedis = jedisPool.getResource()) {
                connected = true;
                jedis.rpush(responseChannel, value);
            } catch (JedisConnectionException jce) {
                if (reConnectionAttempt == 1 || reConnectionAttempt % 10 == 0) {
                    System.err.println("Queue-response: could not connect to Redis. Trying to reconnect: " + reConnectionAttempt);
                }
                reConnectionAttempt += 1;
                connected = false;
                Thread.sleep(100);
            }
        }
    }
    
    //
    // @Param queueKey is the channel where the data came
    //
    private static void handleFrame(OperonContextManager ocm, Info info, JedisPool jedisPool, String queueKey, List<String> data)
        throws OperonGenericException, IOException, InterruptedException {
        //System.out.println("2. MESSAGE :: " + data);
        String responseChannel = info.responseChannel;
        String deadletterChannel = info.deadletterChannel;
        
        //
        // Index 0 = key-name. The rest are the values.
        //
        // TODO: check batch-flag and handle the maxBatchSize -amount at a time
        if (info.batch == false) {
            //System.out.println("NO Batch");
            for (int i = 1; i < data.size(); i ++) {
                OperonContext ctx = ocm.resolveContext(queueKey);
                Statement stmt = new DefaultStatement(ctx);
                ObjectType initialValue = new ObjectType(stmt);
                
                //System.out.println("Handle data: " + data.get(i));
                String dataItem = data.get(i);
                OperonValue parsedData = JsonUtil.operonValueFromString(dataItem, null, ctx).evaluate();
                //System.out.println("parsedData: " + parsedData);
                if (parsedData instanceof ObjectType) {
                    //System.out.println("parsedData was ObjectType");
                    ObjectType parsedObj = (ObjectType) parsedData;
                    if (parsedObj.hasKey("\"headers\"")) {
                        //System.out.println("parsedData has key headers");
                        ObjectType headersObj = (ObjectType) parsedObj.getByKey("headers").evaluate();
                        //System.out.println("parsedData headers :: " + headersObj);
                        if (headersObj.hasKey("\"responseChannel\"")) {
                            responseChannel = ((StringType) headersObj.getByKey("responseChannel").evaluate()).getJavaStringValue();
                            //System.out.println("FOUND: responseChannel :: " + responseChannel);
                        }
                    }
                }
                
                PairType bodyPair = new PairType(stmt);
                bodyPair.setPair("\"body\"", parsedData);
                initialValue.addPair(bodyPair);
        
                try {
                    // Set the initial value into OperonContext:
                    ctx.setInitialValue(initialValue);
                    
                    // Evaluate the query against the intial value:
                    OperonValue result = ctx.evaluateSelectStatement();
                    
                    if (result instanceof ErrorValue && info.sendErrorValueToDeadletterChannel && deadletterChannel != null) {
                        sendResponse(jedisPool, info, deadletterChannel, dataItem);
                    }
                    
                    else if (responseChannel != null) {
                        sendResponse(jedisPool, info, responseChannel, result.toString());
                    }
                    
                    else {
                        ctx.outputResult(result);
                    }
                } catch (Exception e) {
                    sendResponse(jedisPool, info, deadletterChannel, dataItem);
                }
            }
        }
        
        else {
            //System.out.println("Batch");
            OperonContext ctx = ocm.resolveContext(queueKey);
            Statement stmt = new DefaultStatement(ctx);
            ObjectType initialValue = new ObjectType(stmt);
            
            ArrayType bodyArray = new ArrayType(stmt);
            for (int i = 1; i < data.size(); i ++) {
                //System.out.println("Handle data: " + data.get(i));
                OperonValue parsedData = JsonUtil.operonValueFromString(data.get(i), null, ctx);
                bodyArray.addValue(parsedData);
            }
            PairType bodyPair = new PairType(stmt);
            bodyPair.setPair("\"body\"", bodyArray);
            initialValue.addPair(bodyPair);
    
            // Set the initial value into OperonContext:
            ctx.setInitialValue(initialValue);
            
            // TODO: deadletter-handling
            
            // Evaluate the query against the intial value:
            OperonValue result = ctx.evaluateSelectStatement();
            
            if (responseChannel != null) {
                sendResponse(jedisPool, info, responseChannel, result.toString());
            }
            
            else {
                ctx.outputResult(result);
            }
        }
    }
    
    private void debug(String value) {
        Date now = new Date();
        long millis = now.getTime();
        System.out.println(millis + " :: " + value);
    }
    
    public void requestNext() {}
    
    public void stop() {
        this.isRunning = false;
        //:OFF:log.info("Stopped");
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
                    int portInt = (int)((NumberType) portNode).getDoubleValue();
                    if (portInt < -1) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "port must be >= 0");
                    }
                    else if (portInt > 65535) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "port must be <= 65535");
                    }
                    info.port = portInt;
                    break;
                case "\"user\"":
                    String user = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.user = user;
                    break;
                case "\"password\"":
                    String password = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.password = password;
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
                case "\"maxbatchsize\"":
                    Node maxBatchSizeNode = pair.getValue().evaluate();
                    int maxBatchSizeInt = (int)((NumberType) maxBatchSizeNode).getDoubleValue();
                    if (maxBatchSizeInt < 1) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "maxBatchSize must be >= 1");
                    }
                    info.maxBatchSize = maxBatchSizeInt;
                    break;
                case "\"timebetween\"":
                    Node timeBetweenNode = pair.getValue().evaluate();
                    int timeBetweenInt = (int)((NumberType) timeBetweenNode).getDoubleValue();
                    if (timeBetweenInt < 1) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "timeBetween must be >= 0");
                    }
                    info.timeBetween = timeBetweenInt;
                    break;
                case "\"key\"":
                    String qkey = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.key = qkey;
                    break;
                case "\"responsechannel\"":
                    String rkey = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.responseChannel = rkey;
                    break;
                case "\"deadletterchannel\"":
                    String dlkey = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.deadletterChannel = dlkey;
                    break;
                case "\"timeout\"":
                    Node timeoutNode = pair.getValue().evaluate();
                    int timeoutInt = (int)((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    info.timeout = timeoutInt;
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
                case "\"senderrorvaluetodeadletterchannel\"":
                    Node errorToDeadletterChannelValue = pair.getValue().evaluate();
                    if (errorToDeadletterChannelValue instanceof FalseType) {
                        info.sendErrorValueToDeadletterChannel = false;
                    }
                    else {
                        info.sendErrorValueToDeadletterChannel = true;
                    }
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
        private int timeout = 10000;
        private String key = null;
        private String responseChannel = null;
        private String deadletterChannel = "deadletter";
        private boolean sendErrorValueToDeadletterChannel = true;
        private boolean debug = false;
        private String host = "localhost";
        private int port = 6379;
        private String user = null;
        private String password = null;
        private boolean batch = false;
        private Integer maxBatchSize = null;
        private Integer timeBetween = null;
        private OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID;
    }

}