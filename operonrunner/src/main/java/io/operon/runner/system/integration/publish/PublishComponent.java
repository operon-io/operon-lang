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

package io.operon.runner.system.integration.publish;

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


public class PublishComponent extends BaseComponent implements IntegrationComponent {
     // no logger 

    private JedisPool jedisPool = null;

    public PublishComponent() {}
    
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
                    jedis.publish(info.channel, currentValue.toString());
                    jedis.close();
                } catch (JedisConnectionException jce) {
                    System.err.println("Publish: could not connect to Redis. Trying to reconnect: " + reConnectionAttempt);
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
                case "\"channel\"":
                    String channel = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.channel = channel;
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
                    //:OFF:log.debug("publish -producer: no mapping for configuration key: " + key);
                    System.err.println("publish -producer: no mapping for configuration key: " + key);
                    ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "PUBLISH", "ERROR", "publish -producer: no mapping for configuration key: " + key);
            }
        }
        
        currentValue.getStatement().setCurrentValue(currentValueCopy);
        return info;
    }

    private class Info {
        private String channel = "";
        private boolean debug = false;
        private String host = "localhost";
        private int port = 6379;
        private String user = null;
        private String password = null;
    }

}