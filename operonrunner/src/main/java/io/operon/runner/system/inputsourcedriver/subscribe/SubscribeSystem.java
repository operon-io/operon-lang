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

package io.operon.runner.system.inputsourcedriver.subscribe;

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
// Redis-cli examples:
//   127.0.0.1:6379> publish bar '{"name": "fobar"}'
//   127.0.0.1:6379> publish bar '"foobar"'
//
public class SubscribeSystem implements InputSourceDriver {
     // no logger 

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private long pollCounter = 0L;
    private OperonContextManager ocm;
    //private Jedis jedis;
    
    public SubscribeSystem() {}
    
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
            String channel = info.channel;
            if (info.channelPattern != null) {
                channel = info.channelPattern;
            }
            if (this.getOperonContextManager() == null && o != null) {
                //System.out.println("assign contextManager");
                o.setContextStrategy(info.contextManagement);
                ocm = o;
                ctx = ocm.resolveContext(channel);
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
                    System.out.println("Connected. Subscribing to channel " + channel);
                    connected = true;
                    // do operations with jedis resource
                    if (info.channelPattern == null) {
                        jedis.subscribe(new JedisPubSub() {
                            @Override
                            public void onMessage(String channelName, String message) {
                                // handle message
                                //System.out.println("1. MESSAGE :: " + message);
                                try {
                                    SubscribeSystem.handleFrame(ocm, info, channelName, message);
                                } catch (OperonGenericException | IOException e) {
                                    // NoOp
                                    System.err.println("ERROR :: " + e.getMessage());
                                }
                            }
                        }, info.channel);
                    }
                    else {
                        jedis.psubscribe(new JedisPubSub() {
                            @Override
                            public void onPMessage(String pattern, String channelName, String message) {
                                // handle message
                                //System.out.println("1. MESSAGE :: " + message);
                                try {
                                    SubscribeSystem.handleFrame(ocm, info, channelName, message); // TODO: add pattern
                                } catch (OperonGenericException | IOException e) {
                                    // NoOp
                                    System.err.println("ERROR :: " + e.getMessage());
                                }
                            }
                        }, info.channelPattern);
                    }
                } catch (JedisConnectionException jce) {
                    System.err.println("Subscribe: could not connect to Redis. Trying to reconnect: " + reConnectionAttempt);
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
    
    //
    // @Param channelName is the channel where the message came
    //
    private static void handleFrame(OperonContextManager ocm, Info info, String channelName, String message) throws OperonGenericException, IOException {
        //System.out.println("2. MESSAGE :: " + message);
        OperonContext ctx = ocm.resolveContext(channelName);
        Statement stmt = new DefaultStatement(ctx);
        ObjectType initialValue = new ObjectType(stmt);
        
        OperonValue parsedMsg = JsonUtil.lwOperonValueFromString(message);
        PairType bodyPair = new PairType(stmt);
        bodyPair.setPair("\"body\"", parsedMsg);
        initialValue.addPair(bodyPair);

        // Set the initial value into OperonContext:
        ctx.setInitialValue(initialValue);
        
        // Evaluate the query against the intial value:
        OperonValue result = ctx.evaluateSelectStatement();
        
        ctx.outputResult(result);
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
                    int portInt = (int) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
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
                case "\"channel\"":
                    String channel = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.channel = channel;
                    break;
                case "\"channelpattern\"":
                    String channelPattern = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.channelPattern = channelPattern;
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
        private String channel = "";
        private String channelPattern = null;
        private boolean debug = false;
        private String host = "localhost";
        private int port = 6379;
        private String user = null;
        private String password = null;
        private OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID;
    }

}