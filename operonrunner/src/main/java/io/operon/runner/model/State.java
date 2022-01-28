/** OPERON-LICENSE **/
package io.operon.runner.model;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Date;
import java.time.Duration;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisConnectionException;

import io.operon.runner.statement.BaseStatement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.node.type.*;
import io.operon.runner.OperonContext;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.model.exception.OperonGenericException;

import io.operon.runner.node.type.OperonValue;

// FIXME: do not serialize to str when using Map
public class State {

    private String linkedStateFile; // NOT USED YET (i.e. state-information could be read from a file)
    //private Map<String, OperonValue> state;
    
    //
    // OperonValue is serialized into String
    // When value is retrieved, it is parsed back into OperonValue,
    // in the state:get -function.
    //
    private Map<String, String> state;
    private Long createdMillis;
    private Long lastAccessedMillis;
    private String ctxId;
    private OperonContext operonContext;
    private OperonConfigs configs;
    
    public State(OperonContext ctx) {
        this.operonContext = ctx;
        this.configs = ctx.getConfigs();
        this.ctxId = ctx.getContextId();
        if (this.ctxId == null) {
            this.ctxId = "";
        }
        Date d = new Date();
        this.createdMillis = d.getTime();
        if (this.configs.getRedisHost() == null) {
            this.state = Collections.synchronizedMap(new HashMap<String, String>());
        }
    }

    // TODO: jedis.close();

    private JedisPoolConfig buildPoolConfig() {
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

    public void setStateKeyAndValue(String key, OperonValue value) throws OperonGenericException, InterruptedException {
        final JedisPoolConfig poolConfig = buildPoolConfig();
        if (this.configs.getRedisHost() != null) {
            JedisPool jedisPool = null;
            
            if (configs.getRedisPassword() == null) {
                jedisPool = new JedisPool(poolConfig, configs.getRedisHost(), configs.getRedisPort());
            }
            else {
                jedisPool = new JedisPool(poolConfig, configs.getRedisHost(), configs.getRedisPort(), configs.getRedisUser(), configs.getRedisPassword());
            }
            
            boolean connected = false;
            long reConnectionAttempt = 1;
            
            while (connected == false) {
                try (Jedis jedis = jedisPool.getResource()) {
                    connected = true;
                    OperonValue evaluatedValue = value.evaluate();
                    String valueToSet = null;
                    if (evaluatedValue instanceof RawValue) {
                        RawValue raw = (RawValue) evaluatedValue;
                        valueToSet = "R" + raw.toBase64(); // "R" encodes that this is a RawValue
                    }
                    else {
                        valueToSet = value.toString();
                    }
                    if (this.configs.getRedisPrefix() == null) {
                        jedis.set(key, valueToSet);
                    }
                    else {
                        jedis.set(this.configs.getRedisPrefix() + key, valueToSet);
                    }
                } catch (JedisConnectionException jce) {
                    if (reConnectionAttempt == 1 || reConnectionAttempt % 10 == 0) {
                        System.err.println("State: could not connect to Redis. Trying to reconnect: " + reConnectionAttempt);
                    }
                    reConnectionAttempt += 1;
                    connected = false;
                    Thread.sleep(1000);
                }
            }
        }
        
        else {
            this.state.put(key, value.toString());
        }
    }

    public OperonValue getStateValueByKey(String key, OperonValue param2Value) throws OperonGenericException, InterruptedException {
        String resultStr = null;
        OperonValue result = null;
        
        final JedisPoolConfig poolConfig = buildPoolConfig();
        if (this.configs.getRedisHost() != null) {
            JedisPool jedisPool = new JedisPool(poolConfig, configs.getRedisHost(), configs.getRedisPort());
            
            boolean connected = false;
            long reConnectionAttempt = 1;
            
            while (connected == false) {
                //System.out.println("try connect");
                try (Jedis jedis = jedisPool.getResource()) {
                    //System.out.println("State: connected.");
                    connected = true;
                    if (this.configs.getRedisPrefix() == null) {
                        resultStr = jedis.get(key);
                    }
                    else {
                        resultStr = jedis.get(this.configs.getRedisPrefix() + key);
                    }
                    
                    if (resultStr == null) {
                        //System.out.println("get resultStr null ");
                        //System.out.println("state:get :: cache miss :: " + key);
                        if (param2Value == null) {
                            //System.out.println("get resultStr null --> 1");
                            result = new EmptyType(new DefaultStatement(this.operonContext));
                            //System.out.println("get resultStr null --> 1.1");
                        }
                        else {
                            String valueToSet = param2Value.toString();
                            if (this.configs.getRedisPrefix() == null) {
                                jedis.set(key, valueToSet);
                            }
                            else {
                                jedis.set(this.configs.getRedisPrefix() + key, valueToSet);
                            }
                            //System.out.println("get resultStr null --> 2.1");
                            result = param2Value;
                        }
                    }
                    else {
                        //System.out.println("get resultStr exists ");
                        if (resultStr.startsWith("R") == false) {
                            result = JsonUtil.lwOperonValueFromString(resultStr);
                        }
                        else {
                            RawValue rawResult = new RawValue(param2Value.getStatement());
                            byte[] resultBytes = RawValue.base64ToBytes(resultStr.substring(1, resultStr.length()).getBytes());
                            rawResult.setValue(resultBytes);
                            result = rawResult;
                        }
                        //System.out.println("get resultStr exists 2: " + result);
                    }
                    
                } catch (JedisConnectionException jce) {
                    if (reConnectionAttempt == 1 || reConnectionAttempt % 10 == 0) {
                        System.err.println("State: could not connect to Redis. Trying to reconnect: " + reConnectionAttempt);
                    }
                    reConnectionAttempt += 1;
                    connected = false;
                    Thread.sleep(1000);
                }
            }
        }
        
        else {
            resultStr = this.state.get(key);
            
            if (resultStr == null) {
                //System.out.println("get resultStr null ");
                //System.out.println("state:get :: cache miss :: " + key);
                if (param2Value == null) {
                    //System.out.println("get resultStr null --> 1");
                    result = new EmptyType(new DefaultStatement(this.operonContext));
                    //System.out.println("get resultStr null --> 1.1");
                }
                else {
                    this.state.put(key, param2Value.toString());
                    //System.out.println("get resultStr null --> 2.1");
                    result = param2Value;
                }
            }
            else {
                //System.out.println("get resultStr exists ");
                result = JsonUtil.lwOperonValueFromString(resultStr);
                //System.out.println("get resultStr exists 2: " + result);
            }
        }
        
        return result;
    }

    public void setLinkedStateFile(String sf) {
        this.linkedStateFile = sf;
    }
    
    public String getLinkedStateFile() {
        return this.linkedStateFile;
    }
    
    public Long getCreatedMillis() {
        return this.createdMillis;
    }
    
    public Long getLastAccessedMillis() {
        return this.lastAccessedMillis;
    }

    public void setLastAccessedMillis(Long lam) {
        this.lastAccessedMillis = lam;
    }
}