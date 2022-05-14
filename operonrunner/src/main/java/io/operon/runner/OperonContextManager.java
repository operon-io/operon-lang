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

package io.operon.runner;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.Math;

import org.apache.logging.log4j.Logger;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.model.State;
import io.operon.runner.node.type.*;
import io.operon.runner.model.exception.OperonGenericException;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.Isolates;
import org.graalvm.nativeimage.Isolates.CreateIsolateParameters;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * Manages different context-creation / reuse strategies.
 * Supported strategies are:
 *  ALWAYS_CREATE_NEW : Operon-query is invoked with new context each time
 *  REUSE_BY_CORRELATION_ID : Existing context's are used and they are selected by given correlation-id
 *  SINGLETON : Only one context exists
 * 
 */
public class OperonContextManager {
     // no logger 
    
    private OperonContextManager.ContextStrategy contextStrategy;
    private OperonContext operonContext;
    private String query;
    private byte[] operonContextCache;
    private PrintStream contextLogger;
    private Map<String, OperonContext> sessionAffinateContextMap;
    private Map<String, State> sessionAffinateStateMap;
    private boolean removeUnusedStates = false;
    private boolean removeUnusedStatesInThread = false; // This is not implemented yet.
    private long removeUnusedStatesAfterMillis = 60000L;
    
    public static enum ContextStrategy {
        ALWAYS_CREATE_NEW,
        REUSE_BY_CORRELATION_ID,
        SINGLETON
    }
    
    public OperonContextManager(OperonContext ctx, OperonContextManager.ContextStrategy strategy) throws IOException {
        this.setOperonContext(ctx);
        this.contextLogger = ctx.getContextLogger();
        this.sessionAffinateContextMap = new HashMap<String, OperonContext>();
        this.sessionAffinateStateMap = new HashMap<String, State>();
        this.setContextStrategy(strategy);
        this.setQuery(ctx.getQuery());
    }

    public void setQuery(String q) {
        this.query = q;
    }
    
    public String getQuery() {
        return this.query;
    }
    
    // Create a new Isolate (Substrate VM) where to evaluate the query.
    // Extract result and destroy the Isolate.
    // This is done only when "ALWAYS_CREATE_NEW" strategy is used.
    // Avoids GC.
    //
    // TODO: create OperonContext in this isolate!
    //
    public static OperonValue evaluateSelectStatementInNewIsolate(OperonContextManager ocm, String correlationId, OperonValue initialValue) throws OperonGenericException {
        try {
        //System.out.println("Resolve OperonContext");
        //System.out.println("Resolve OperonContext by correlationId: " + correlationId + "initialValue=" + initialValue);
        //System.out.println("ContextStrategy=" + ocm.getContextStrategy());
        
        //
        // create OperonContext in this Isolate
        //
        if (ocm.getContextStrategy() == OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW) {
            //System.out.println(">> ALWAYS_CREATE_NEW");
            IsolateThread mainContext = CurrentIsolate.getCurrentThread();
            IsolateThread queryContext = Isolates.createIsolate(CreateIsolateParameters.getDefault());
            
            OperonContext ctx = ocm.resolveContext(correlationId);
            
            ObjectHandle ctxHandle = ObjectHandles.create().create(ctx);
            ObjectHandle resultHandle = OperonContextManager.evaluateQuery(queryContext, mainContext, ctxHandle);
            
            OperonValue result = ObjectHandles.getGlobal().get(resultHandle);
            ObjectHandles.getGlobal().destroy(resultHandle);
            Isolates.tearDownIsolate(queryContext);
            return result;
        }
        //
        // reuse OperonContext
        //
        // TODO: set the state and let-statements!
        //
        else if (ocm.getContextStrategy() == OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID) {
            //System.out.println("Resolve OperonContext by correlationId: " + correlationId);
            IsolateThread mainContext = CurrentIsolate.getCurrentThread();
            IsolateThread queryContext = Isolates.createIsolate(CreateIsolateParameters.getDefault());

            OperonContext ctx = ocm.resolveContext(correlationId);

            if (initialValue != null) {
                ctx.setInitialValue(initialValue);
            }


            //
            // TODO: this static method requires sessionAffinateContextMap and sessionAffinateStateMap,
            //       which are use here.
            //
            /*
            OperonContext ctx = null;
            try {
                ctx = OperonRunner.createNewOperonContext(this.getQuery(), "query", this.getOperonContext().getConfigs());
                
                if (correlationId != null) {
                    //System.out.println("Fetch oldContext");
                    OperonContext oldContext = this.sessionAffinateContextMap.get(correlationId);
                    
                    if (oldContext == null) {
                        //System.out.println("oldContext did not exist, create new state");
                        oldContext = this.getOperonContext();
                        State s = new State();
                        oldContext.setState(s);
                        this.sessionAffinateStateMap.put(correlationId, s);
                    }
                    
                    //System.out.println("Set state");
                    State s = this.sessionAffinateStateMap.get(correlationId);
                    ctx.setState(s);
                    
                    //System.out.println("Set Let-statements");
                    for (LetStatement oldLstmnt : oldContext.getAllLetStatements()) {
                        if (oldLstmnt.getResetType() == LetStatement.ResetType.NEVER && oldLstmnt.getEvaluatedValue() != null) {
                            for (LetStatement ctxLstmnt : ctx.getAllLetStatements()) {
                                if (oldLstmnt.getValueRefStr().equals(ctxLstmnt.getValueRefStr())) {
                                    OperonValue evaluatedValueCopy = oldLstmnt.getEvaluatedValue().copy();
                                    ctxLstmnt.setEvaluatedValue(evaluatedValueCopy);
                                    break;
                                }
                            }
                        }
                    }
    
                    this.sessionAffinateContextMap.put(correlationId, ctx);
                }
            } catch (Exception e) {
                throw new OperonGenericException(e.getMessage());
            }
            ctx.setContextLogger(this.contextLogger);

            */

            ObjectHandle ctxHandle = ObjectHandles.create().create(ctx);
            ObjectHandle resultHandle = OperonContextManager.evaluateQuery(queryContext, mainContext, ctxHandle);
            
            OperonValue result = ObjectHandles.getGlobal().get(resultHandle);
            ObjectHandles.getGlobal().destroy(resultHandle);
            Isolates.tearDownIsolate(queryContext);
            return result;
        }
        else {
            // SINGLETON:
            //System.out.println(">> SINGLETON");
            OperonContext ctx = ocm.resolveContext(correlationId);
            return ctx.evaluateSelectStatement();
        }
        } catch (IOException ioe) {
            return ErrorUtil.createErrorValueAndThrow(initialValue.getStatement(), "CONTEXT_MANAGER", "ERROR", ioe.getMessage());
        }
    }
    
    //
    // Substrate Isolates (substrate forked VM) are a way to optimize Operon.
    // This entry-point executes the query inside an isolate.
    // TODO: do the actual performance tests (pmap).
    //
    @CEntryPoint
    public static ObjectHandle evaluateQuery(@CEntryPoint.IsolateThreadContext IsolateThread queryContext,
                                             IsolateThread mainContext, ObjectHandle inputOperonContext) 
                                             throws OperonGenericException {
        OperonContext ctx = ObjectHandles.getGlobal().get(inputOperonContext);
        OperonValue resultValue = ctx.evaluateSelectStatement();
        ObjectHandle result = ObjectHandles.create().create(resultValue);
        return result;
    }

    //
    // correlationId may be null if REUSE_BY_CORRELATION_ID is not used.
    // NOTE: it is important that this method is syncronized, otherwise parallel access
    //       (e.g. by ISD would cause that incorrect context could be returned).
    //       Error can be confirmed with StateTests#stateTest4, when sequence ISD is run with ALWAYS_CREATE_NEW.
    //       The error would happen because this sets the new OperonContext, but parallel access would
    //       access the wrong context. TestHelper, which reads the result of seq would then get the wrong result.
    //
    public synchronized OperonContext resolveContext(String correlationId) throws OperonGenericException, IOException {
        OperonContext ctx = null;
        
        //System.out.println("ContextManager :: " + this.getContextStrategy() + ", correlationId=" + correlationId);
        
        if (this.getContextStrategy() == OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW) {
            try {
              //System.out.println("ContextManager :: create new context");
              String contextId = String.valueOf(Math.random());
              ctx = OperonRunner.createNewOperonContext(this.getQuery(), contextId, this.getOperonContext().getConfigs());
            } catch (Exception e) {
                ErrorUtil.createErrorValueAndThrow(null, "CONTEXT_MANAGER", "RESOLVE_ERROR", e.getMessage());
            }
            ctx.setContextLogger(this.contextLogger);
        }
        
        else if (this.getContextStrategy() == OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID) {
            try {
                ctx = OperonRunner.createNewOperonContext(this.getQuery(), "query", this.getOperonContext().getConfigs());
                
                if (correlationId != null) {
                    //System.out.println("Fetch oldContext");
                    OperonContext oldContext = this.sessionAffinateContextMap.get(correlationId);
                    
                    if (oldContext == null) {
                        //System.out.println("oldContext did not exist, create new state");
                        oldContext = this.getOperonContext();
                        State s = new State(oldContext);
                        oldContext.setState(s);
                        this.sessionAffinateStateMap.put(correlationId, s);
                    }
                    
                    //System.out.println("Set state");
                    State s = this.sessionAffinateStateMap.get(correlationId);
                    ctx.setState(s);
                    
                    //System.out.println("Set Let-statements");
                    
                    //
                    // Copy named-values (let-stmts) from old-context to new-context,
                    // where the ResetType is NEVER (i.e. they should be retained).
                    //
                    // NOTE: if OperonConfigs has "namedValues", then the check that they are set when
                    //       the ResetType is other than NEVER is done in OperonContext's evaluateSelectStatement.
                    //
                    for (LetStatement oldLstmnt : oldContext.getAllLetStatements()) {
                        if (oldLstmnt.getResetType() == LetStatement.ResetType.NEVER && oldLstmnt.getEvaluatedValue() != null) {
                            for (LetStatement ctxLstmnt : ctx.getAllLetStatements()) {
                                if (oldLstmnt.getValueRefStr().equals(ctxLstmnt.getValueRefStr())) {
                                    OperonValue evaluatedValueCopy = oldLstmnt.getEvaluatedValue().copy();
                                    ctxLstmnt.setEvaluatedValue(evaluatedValueCopy);
                                    break;
                                }
                            }
                        }
                    }
    
                    this.sessionAffinateContextMap.put(correlationId, ctx);
                }
            } catch (Exception e) {
                ErrorUtil.createErrorValueAndThrow(null, "CONTEXT_MANAGER", "RESOLVE_ERROR", e.getMessage());
            }
            ctx.setContextLogger(this.contextLogger);
        }
        
        else if (this.getContextStrategy() == OperonContextManager.ContextStrategy.SINGLETON) {
            ctx = this.getOperonContext();
        }
        
        if (this.getContextStrategy() == OperonContextManager.ContextStrategy.SINGLETON || 
                this.getContextStrategy() == OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID) {
            Date nowDate = new Date();
            ctx.getState().setLastAccessedMillis(nowDate.getTime());
            
            if (this.removeUnusedStates && this.removeUnusedStatesAfterMillis >= 0) {
                this.removeUnusedStates(this.removeUnusedStatesAfterMillis);
            }
            
            this.setOperonContext(ctx);
        }

        //
        // This setting of context is required by the TestHelper to read the result.
        //
        this.setOperonContext(ctx);
        return ctx;
    }
    
    //
    // Ask to remove the State -objects from the map
    // that are older than the periodInMillis
    //
    public void removeUnusedStates(long periodInMillis) {
        Date now = new Date();
        long nowMillis = now.getTime();
        List<String> toBeRemoved = new ArrayList<String>();
        for (Map.Entry<String, State> entry : this.sessionAffinateStateMap.entrySet()) {
            if (nowMillis - periodInMillis > entry.getValue().getLastAccessedMillis()) {
                // mark to be removed
                toBeRemoved.add(entry.getKey());
            }
        }
        for (String rm : toBeRemoved) {
            this.sessionAffinateStateMap.remove(rm);
        }
        
    }
    
    public void setRemoveUnusedStates(boolean removeUnusedStates, long removeUnusedStatesAfterMillis, boolean removeUnusedStatesInThread) {
        this.removeUnusedStates = removeUnusedStates;
        this.removeUnusedStatesAfterMillis = removeUnusedStatesAfterMillis;
        this.removeUnusedStatesInThread = removeUnusedStatesInThread;
    }
    
    public void setContextStrategy(OperonContextManager.ContextStrategy s) {
        this.contextStrategy = s;
    }
    
    public OperonContextManager.ContextStrategy getContextStrategy() {
        return this.contextStrategy;
    }
    
    public void setOperonContext(OperonContext ctx) {
        this.operonContext = ctx;
    }

    public OperonContext getOperonContext() {
        return this.operonContext;
    }

    public void setOperonContextCache(byte[] cache) {
        this.operonContextCache = cache;
    }
    
    public byte[] getOperonContextCache() {
        return this.operonContextCache;
    }

}