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

package io.operon.runner.system.integration.queue;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.time.Duration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.Context;
import io.operon.runner.processor.function.core.string.StringToRaw;

import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.system.integration.BaseComponent;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import java.io.ByteArrayOutputStream;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

import org.apache.logging.log4j.LogManager;


public class QueueComponent extends BaseComponent implements IntegrationComponent {
     // no logger 

    private JedisPool jedisPool = null;

    public QueueComponent() {}
    
    public OperonValue produce(OperonValue currentValue) throws OperonComponentException {
        try {
            Info info = this.resolve(currentValue);
            
            if (this.jedisPool == null) {
                final JedisPoolConfig poolConfig = buildPoolConfig(info);
                this.jedisPool = null; 
                
                if (info.password == null) {
                    jedisPool = new JedisPool(poolConfig, info.host, info.port);
                }
                else {
                    jedisPool = new JedisPool(poolConfig, info.host, info.port, info.user, info.password);
                }
            }
            boolean connected = false;
            long reConnectionAttempt = 1;
            
            while (connected == false) {
                try (Jedis jedis = jedisPool.getResource()) {
                    connected = true;
                    // do operations with jedis resource
                    Statement stmt = currentValue.getStatement();
                    ObjectType requestObj = new ObjectType(stmt);
                    PairType bodyPair = new PairType(stmt);
                    bodyPair.setPair("\"body\"", currentValue);
                    
                    ObjectType headersObj = new ObjectType(stmt);
                    
                    //
                    // This is used to generate the responseChannel -name
                    //
                    String autoResponseChannelStr = null;
                    
                    if (info.responseChannel != null) {
                        StringType responseChannelJStr = StringType.create(stmt, info.responseChannel);
                        PairType responseChannelPair = new PairType(stmt);
                        responseChannelPair.setPair("\"responseChannel\"", responseChannelJStr);
                        headersObj.addPair(responseChannelPair);
                    }
                    
                    else if (info.autoResponseChannel == true) {
                        java.util.Random rg = new java.util.Random();
                        autoResponseChannelStr = "QUEUE_RESPONSE_" + rg.nextInt();
                    }
                    
                    PairType headersPair = new PairType(stmt);
                    headersPair.setPair("\"headers\"", headersObj);
                    
                    requestObj.addPair(bodyPair);
                    requestObj.addPair(headersPair);
                    
                    jedis.rpush(info.key, requestObj.toString());
                    
                    if (info.responseChannel != null) {
                        //System.out.println("start reading responseChannel");
                        List<String> data = jedis.blpop(info.timeout, info.responseChannel);
                        OperonValue parsedResponse = JsonUtil.lwOperonValueFromString(data.get(1)); // index 0 is the key-name
                        //System.out.println("RESPONSE :: " + parsedResponse);
                        return parsedResponse;
                    }
                    
                    else if (info.autoResponseChannel == true) {
                        //System.out.println("start reading responseChannel");
                        List<String> data = jedis.blpop(info.timeout, autoResponseChannelStr);
                        OperonValue parsedResponse = JsonUtil.lwOperonValueFromString(data.get(1)); // index 0 is the key-name
                        //System.out.println("RESPONSE :: " + parsedResponse);
                        jedis.del(autoResponseChannelStr);
                        return parsedResponse;
                    }
                    
                    jedis.close();
                    
                } catch (JedisConnectionException jce) {
                    if (reConnectionAttempt == 1 || reConnectionAttempt % 10 == 0) {
                        System.err.println("Subscribe: could not connect to Redis. Trying to reconnect: " + reConnectionAttempt);
                    }
                    reConnectionAttempt += 1;
                    connected = false;
                    Thread.sleep(1000);
                }
            }
            
            return currentValue;
        } catch (OperonGenericException e) {
            throw new OperonComponentException(e.getErrorJson());
        } catch (InterruptedException ex) {
            System.err.println("Interrupted");
            throw new OperonComponentException(ex.getMessage());
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
                case "\"host\"":
                    StringType hostNode = (StringType) pair.getEvaluatedValue();
                    String hostStr = ((StringType) hostNode).getJavaStringValue();
                    info.host = hostStr;
                    break;
                case "\"port\"":
                    NumberType portNode = (NumberType) pair.getEvaluatedValue();
                    int portInt = (int) ((NumberType) portNode).getDoubleValue();
                    if (portInt < -1) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "port must be >= 0");
                    }
                    else if (portInt > 65535) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "port must be <= 65535");
                    }
                    info.port = portInt;
                    break;
                case "\"user\"":
                    String user = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.user = user;
                    break;
                case "\"password\"":
                    String password = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.password = password;
                    break;
                case "\"timeout\"":
                    NumberType timeoutNode = (NumberType) pair.getEvaluatedValue();
                    int timeoutInt = (int) ((NumberType) timeoutNode).getDoubleValue();
                    if (timeoutInt < -1) {
                        ErrorUtil.createErrorValueAndThrow(null, "SYSTEM", "CONFIGURATION", "timeout must be >= 0");
                    }
                    info.timeout = timeoutInt;
                    break;
                case "\"key\"":
                    String rkey = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.key = rkey;
                    break;
                case "\"responsechannel\"":
                    String rckey = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.responseChannel = rckey;
                    break;
                case "\"autoresponsechannel\"":
                    OperonValue autoResponseChannelValue = pair.getEvaluatedValue();
                    if (autoResponseChannelValue instanceof FalseType) {
                        info.autoResponseChannel = false;
                    }
                    else {
                        info.autoResponseChannel = true;
                    }
                    break;
                case "\"debug\"":
                    OperonValue debugValue = pair.getEvaluatedValue();
                    if (debugValue instanceof FalseType) {
                        info.debug = false;
                    }
                    else {
                        info.debug = true;
                    }
                    break;
                default:
                    //:OFF:log.debug("queue -producer: no mapping for configuration key: " + key);
                    System.err.println("queue -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "QUEUE", "ERROR", "queue -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private String key = "";
        private String responseChannel = null;
        private boolean debug = false;
        private boolean autoResponseChannel = false;
        private String host = "localhost";
        private int port = 6379;
        private String user = null;
        private String password = null;
        private int timeout = 0; // 0 = block indefinitely
    }

}