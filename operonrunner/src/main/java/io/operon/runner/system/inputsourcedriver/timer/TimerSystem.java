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

package io.operon.runner.system.inputsourcedriver.timer;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;
import java.util.TimeZone;
import java.util.Calendar;

import io.operon.runner.OperonContext;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.BaseContext;
import io.operon.runner.Context;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.system.BaseSystem;
import io.operon.runner.statement.FromStatement;
import io.operon.runner.processor.function.core.date.DateNow;
import io.operon.runner.processor.function.core.DurationToMillis;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class TimerSystem implements InputSourceDriver {
    private static Logger log = LogManager.getLogger(TimerSystem.class);

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private long pollCounter = 0L;
    private ObjectType initialValue;
    private OperonContextManager ocm;
    private Timer timer;
    
    private short errorCount = 0;
    private static Calendar cal = null;
    
    public TimerSystem() {}
    
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
            if (info.debug) {
                System.out.println("Timer");
                System.out.println("- period: " + info.period);
                System.out.println("- initialDelay: " + info.initialDelay);
            }
            this.isRunning = true;
            
            if (this.getOperonContextManager() == null && o != null) {
                ocm = o;
                ctx = ocm.resolveContext("correlationId");
            }
            else if (o == null) {
                ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, info.contextManagement);
            }
            
            // 
            // Resolve timezone, if such was set when Operon was started (with --timezone / -tz option)
            // 
            TimeZone timeZone = null;
            if (ctx.getConfigs() != null) {
                OperonConfigs configs = ctx.getConfigs();
                if (configs.getTimezone() != null) {
                    TimeZone tz = TimeZone.getTimeZone(configs.getTimezone());
                    timeZone = tz;
                }
            }
            
            if (TimerSystem.cal == null) {
                TimerSystem.cal = DateNow.getCalendar(timeZone, 0);
            }
            
            TimerTask repeatedTask = new TimerTask() {
                public void run() {
                    try {
                        handleFrame(ocm, info);
                    } catch (Exception e) {
                        System.err.println("ERROR :: " + e.getMessage());
                    }
                }
            };
            
            this.timer = new Timer("Timer");
             
            long delay = info.initialDelay;
            Long period = info.period; //1000L (1 second); // 1 day = 1000L * 60L * 60L * 24L;
            if (period == null) {
                System.err.println("TIMER :: Missing mandatory \"period\" -option.");
                return;
            }
            this.timer.scheduleAtFixedRate(repeatedTask, delay, period);
            
            // Prevent ISD from quitting
            while (this.isRunning) {
                Thread.sleep(100);
            }
            this.timer.cancel(); // Stop the timer
        } catch (OperonGenericException e) {
            log.error("Exception :: " + e.toString());
            System.err.println("ERROR :: " + e.getMessage());
            ctx.setException(e);
            this.errorCount += 1;
            if (this.errorCount >= 10) {
                this.stop();
            }
        } catch (Exception ex) {
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
            this.errorCount += 1;
            if (this.errorCount >= 10) {
                this.stop();
            }
        }
    }
    
    //
    // Implement the handling logic here.
    //
    public void handleFrame(OperonContextManager ocm, Info info) throws OperonGenericException, IOException {
        this.pollCounter += 1;
        try {
            // Acquire OperonContext
            OperonContext ctx = ocm.resolveContext("correlationId");
            Statement stmt = ctx.getSelectStatement();
            
            TimerSystem.cal.setTime(new Date());
            ObjectType dateValue = DateNow.getDateNowObjectType(stmt, TimerSystem.cal);
            
            PairType bodyPair = new PairType(stmt);
            bodyPair.setPair("\"body\"", dateValue);
            
            PairType counterPair = new PairType(stmt);
            NumberType counterValue = new NumberType(stmt);
            counterValue.setDoubleValue((double) this.pollCounter);
            counterPair.setPair("\"counter\"", counterValue);
            
            if (info.timerId != null) {
                PairType timerIdPair = new PairType(stmt);
                StringType timerIdValue = new StringType(stmt);
                timerIdValue.setValue(info.timerId);
                timerIdPair.setPair("\"id\"", timerIdValue);
            }
            
            ObjectType initialValue = new ObjectType(stmt);
            
            initialValue.addPair(bodyPair);
            initialValue.addPair(counterPair);
            
            // Set the initial value into OperonContext:
            ctx.setInitialValue(initialValue);
            
            // Evaluate the query against the intial value:
            OperonValue result = ctx.evaluateSelectStatement();
            
            ctx.outputResult(result);
            if (info.times > 0 && this.pollCounter >= info.times) {
                this.stop();
            }
        } catch (Exception e) {
            this.errorCount += 1;
            if (this.errorCount >= 10) {
                System.err.println("ERROR :: " + e.getMessage());
                System.err.println("timer: too many errors. Stopping.");
                this.stop();
            }
        }
    }
    
    public void requestNext() {}
    
    public void stop() {
        this.timer.cancel(); // Stop the timer
        this.isRunning = false;
        log.info("Stopped");
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
        boolean iBoolean = true;
        
        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            //System.out.println("core-timer: key :: " + pair.getKey() + ", value :: " + pair.getValue());
            switch (key.toLowerCase()) {
                case "\"timerid\"":
                    String iTiD = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.timerId = iTiD;
                    break;
                case "\"initialdelay\"":
                    OperonValue initialDelayValue = pair.getValue().evaluate();
                    if (initialDelayValue instanceof NumberType) {
                        double initialDelay = ((NumberType) initialDelayValue).getDoubleValue();
                        info.initialDelay = (long) initialDelay;
                    }
                    else if (initialDelayValue instanceof ObjectType) {
                        ObjectType initialDelayObj = (ObjectType) initialDelayValue;
                        ObjectType normalized = DurationToMillis.normalize(initialDelayObj);
                        long cumulativeMilliseconds = DurationToMillis.toMillis(normalized);
                        info.initialDelay = cumulativeMilliseconds;
                    }
                    break;
                case "\"period\"":
                    OperonValue periodValue = pair.getValue().evaluate();
                    if (periodValue instanceof NumberType) {
                        double fixedRatePeriod = ((NumberType) periodValue).getDoubleValue();
                        info.period = (long) fixedRatePeriod;
                    }
                    else if (periodValue instanceof ObjectType) {
                        ObjectType periodObj = (ObjectType) periodValue;
                        ObjectType normalized = DurationToMillis.normalize(periodObj);
                        long cumulativeMilliseconds = DurationToMillis.toMillis(normalized);
                        info.period = cumulativeMilliseconds;
                    }
                    break;
                case "\"times\"":
                    double times = ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                    info.times = (long) times;
                    break;
                case "\"timestamp\"":
                    Node timestampValue = pair.getValue().evaluate();
                    if (timestampValue instanceof FalseType) {
                        info.timestamp = false;
                    }
                    else {
                        info.timestamp = true;
                    }
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
                    System.err.println("Timer -isd: no mapping for configuration key: " + key);
            }
        }
        return info;
    }
    
    private class Info {
        private String timerId;
        private long initialDelay = 0L;
        private long period = 1000L;
        private long times = 0L; // how many times to execute
        private boolean timestamp = true;
        private boolean debug = false;
        // contextManagement is preferred option for ISD
        private OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.SINGLETON;
    }
}