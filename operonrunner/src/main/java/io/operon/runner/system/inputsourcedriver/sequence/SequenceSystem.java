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

package io.operon.runner.system.inputsourcedriver.sequence;

import io.operon.runner.OperonRunner;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.system.BaseSystem;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.statement.FromStatement;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// This ISD is used for internal testing.
//
public class SequenceSystem extends BaseSystem implements InputSourceDriver {
    private static Logger log = LogManager.getLogger(SequenceSystem.class);

    private OperonValue initialValue;
    private ObjectType jsonConfiguration; // optional: json-configuration for the component. Not available for SequenceSystem.
    private boolean isRunning;
    private long pollCounter = 0L;
    private List<String> resultList;
    private OperonContextManager ocm;
    
    public SequenceSystem() {
        this.resultList = new ArrayList<String>();
    }
    
    public OperonContextManager getOperonContextManager() {
        return this.ocm;
    }
    
    public void setOperonContextManager(OperonContextManager o) {
        this.ocm = o;
    }
    
    public boolean isRunning() {
        return this.isRunning;
    }
    
    public void start(OperonContextManager o) {
        OperonContext ctx = null;
        try {
            Info info = this.resolve();
            if (this.getOperonContextManager() == null && o != null) {
                ocm = o;
                ctx = ocm.resolveContext("correlationId");
            }
            else if (o == null) {
                ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, info.contextManagement);
            }
            this.isRunning = true;
            
            final ArrayType initialValueArray = (ArrayType) this.getInitialValue();
            
            if (info.contextManagement == ContextStrategy.SINGLETON) {
                AtomicInteger i = new AtomicInteger(1);
                //System.out.println("initialValueArray :: " + initialValueArray);
                initialValueArray.getValues()
                    .stream() // NOTE: if this was parallel, then the result-list could get corrupted by multiple threads trying to set values there
                    .forEach(jv -> {
                        if (this.isRunning()) {
                            try {
                                OperonContext ctxSub = ocm.resolveContext("correlationId");
                                
                                // This creates also a new Statement, therefore not affecting the state
                                // of the original array.
                                OperonValue jvDeepCopy = JsonUtil.lwOperonValueFromString(jv.toString());
                                handleFrame(ctxSub, (OperonValue) jvDeepCopy);
                                int counter = i.getAndIncrement();
                                
                                //boolean sendToProcess = false;
                                //while (sendToProcess == false) {
                                    //
                                    // Context might be blocking, e.g. due to Aggregate.
                                    // We can send new item when context tells it is ready
                                    // to accept new items.
                                    //
                                    //if (ctxSub.isReady()) {
                                        //sendToProcess = true;

                                        //System.out.println("sequence: total sent to process: " + counter);
                                    /*}
                                    else {
                                        Thread.sleep(10);
                                    }*/
                                //}
                            } catch (OperonGenericException e) {
                                log.error("Exception :: " + e.toString());
                                //ctx.setException(e);
                            } catch (IOException ioe) {
                                System.err.println("IOException");
                            } /*catch (InterruptedException ie) {
                                System.err.println("Interrupted");
                            }*/
                        }
                        else {
                            System.err.println("sequence: ERROR: isd not running");
                        }
                    });
            }
            this.isRunning = false;
        } /*catch (OperonGenericException e) {
            log.error("Exception :: " + e.toString());
            ctx.setException(e);
        } */catch (Exception ex) {
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
        }
    }
    
    //
    // Implement the handling logic here.
    //
    public void handleFrame(OperonContext ctx, OperonValue initialValue) throws OperonGenericException {
        this.pollCounter += 1;
        
        // Set the initial value into OperonContext:
        ctx.setInitialValue(initialValue);
        
        // Evaluate the query against the initial value:
        OperonValue result = ctx.evaluateSelectStatement();
        
        this.getResultList().add(result.toString());
        ctx.outputResult(result);
    }
    
    public void requestNext() {}
    
    public void stop() {
        this.isRunning = false;
        log.info("Stopped");
    }
    
    public void setJsonConfiguration(ObjectType jsonConfig) { this.jsonConfiguration = jsonConfig; }
    public ObjectType getJsonConfiguration() { return this.jsonConfiguration; }
    public long getPollCounter() { return this.pollCounter; }

    public void setInitialValue(OperonValue initialValue) {
        this.initialValue = initialValue;
    }
    
    public OperonValue getInitialValue() {
        return this.initialValue;
    }

    public void setResultList(List<String> rl) {
        this.resultList = rl;
    }
    
    public List<String> getResultList() {
        return this.resultList;
    }
    
    private Info resolve() throws OperonGenericException {
        Info info = new Info();
        
        // sequence-system does not support configuration-object
        /*
        List<PairType> jsonPairs = this.getJsonConfiguration().getPairs();
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            //System.out.println("ISD: key :: " + pair.getKey() + ", value :: " + pair.getValue());
            switch (key.toLowerCase()) {
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
                    System.out.println("sequence: no mapping for configuration key: " + key);
            }
        }
        */
        return info;
    }
    
    private class Info {
        // contextManagement is preferred option for ISD
        private OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.SINGLETON;
    }
}