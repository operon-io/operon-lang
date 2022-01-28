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
import java.util.Deque;
import java.util.Iterator;
import java.util.ArrayDeque;
import java.io.IOException;
import java.io.PrintStream;

import io.operon.runner.Context;
import io.operon.runner.util.RandomUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.statement.*;
import io.operon.runner.node.Node;
import io.operon.runner.node.UnaryNode;
import io.operon.runner.node.BinaryNode;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.Operator;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * Context -class acts as a container for the query elements.
 * 
 */
public class BaseContext {
    private static Logger log = LogManager.getLogger(BaseContext.class);
    
    private Context parentContext;
    private String ownNamespace;
    private Map<String, Context> modules;
    
    private Map<String, OperonValue> runtimeValues;
    private OperonConfigs configs;
        
    // Key (String): the valueRef, which is the same as the Key (String) in the runtimeValues.
    // NOTE: this controls only the top-level valueRefs. Should be refactored!
    private Map<String, List<Operator>> bindValues;
    private Map<String, FunctionStatement> functionStatements;
    private Map<String, LetStatement> letStatements;
    
    private Statement exceptionStatement;
    private ErrorValue errorValue;
    private OperonGenericException exception;
    private transient PrintStream logger;
    private transient PrintStream errorLogger;
    private boolean isReady = true;
    private Deque<Node> stackTrace;
    
    public BaseContext() throws IOException {
        this.modules = new HashMap<String, Context>();
        this.runtimeValues = new HashMap<String, OperonValue>();
        this.bindValues = new HashMap<String, List<Operator>>();
        this.functionStatements = new HashMap<String, FunctionStatement>();
        this.letStatements = new HashMap<String, LetStatement>();
        this.configs = new OperonConfigs();
        this.stackTrace = new ArrayDeque<Node>();
    }

    //
    // After evaluating the Select -statement, the state must be synchronized,
    // i.e. the Let -statements must be released from stored values, if their
    // reset-strategy requires this.
    //
    public void synchronizeState() {
        //System.out.println("Synchronize state");
        // Work through the context's own Let -statements:
        for (Map.Entry<String, LetStatement> stmntEntry : this.getLetStatements().entrySet()) {
            //System.out.println("LET :: reset value");
            LetStatement lstmnt = stmntEntry.getValue();
            lstmnt.reset();
        }
        
        //
        // Work through the Function's own Let -statements:
        //
        for (Map.Entry<String, FunctionStatement> funcStmntEntry : this.getFunctionStatements().entrySet()) {
            FunctionStatement fstmnt = funcStmntEntry.getValue();
            for (Map.Entry<String, LetStatement> stmntEntry : fstmnt.getLetStatements().entrySet()) {
                LetStatement lstmnt = (LetStatement) stmntEntry.getValue();
                lstmnt.reset();                
            }
        }
    }

    public Map<String, OperonValue> getRuntimeValues() {
        return runtimeValues;
    }

    public Map<String, List<Operator>> getBindValues() {
        return bindValues;
    }

    public Map<String, FunctionStatement> getFunctionStatements() {
        return this.functionStatements;
    }
    
    public Map<String, LetStatement> getLetStatements() {
        return this.letStatements;
    }

    public Context getParentContext() {
        return this.parentContext;
    }

    public void setParentContext(Context parent) {
        this.parentContext = parent;
    }
    
    //
    // NOTE: we should not find the root-context from Context, because
    //       the check could start from wrong statement.
    //
    public static Context getRootContextByStatement(Statement stmt) {
        Statement rootStmt = BaseStatement.getRootStatement(stmt);
        Context rootContext = (Context) rootStmt.getOperonContext();
        while (rootContext.getParentContext() != null) {
            rootContext = rootContext.getParentContext();
        }
        return rootContext;
    }
    
    //
    // NOTE / WARNING: this is not best way, if we start checking the ctx from the
    // wrong statement!
    //
    // DEPRECATED
    //
    /*public static Context getRootContext(Context ctx) {
        Context rootContext = ctx;
        while (rootContext.getParentContext() != null) {
            rootContext = rootContext.getParentContext();
        }
        return rootContext;
    }*/
    
    public Map<String, Context> getModules() {
        return this.modules;
    }

    public void setOwnNamespace(String ons) {
        this.ownNamespace = ons;
    }
    
    public String getOwnNamespace() {
        return this.ownNamespace;
    }

    // For debugging purposes only
    public synchronized void setIsReady(boolean rdy, String message) {
        this.isReady = rdy;
    }

    public void setException(OperonGenericException e) {
        this.exception = e;
    }
    
    public OperonGenericException getException() {
        return this.exception;
    }

    public void setErrorValue(ErrorValue err) {
        this.errorValue = err;
    }
    
    public ErrorValue getErrorValue() {
        return this.errorValue;
    }

    public Statement getExceptionStatement() {
        return this.exceptionStatement;
    }
    
    public void setExceptionStatement(Statement exStmt) {
        this.exceptionStatement = exStmt;
    }

    public void setContextLogger(PrintStream logger) {
        this.logger = logger;
    }
    
    public PrintStream getContextLogger() {
        return this.logger;
    }

    public void setContextErrorLogger(PrintStream logger) {
        this.errorLogger = logger;
    }
    
    public PrintStream getContextErrorLogger() {
        return this.errorLogger;
    }

    public void setConfigs(OperonConfigs conf) {
        this.configs = conf;
    }
    
    public OperonConfigs getConfigs() {
        return this.configs;
    }
    
    public Deque<Node> getStackTrace() {
        return this.stackTrace;
    }
    
    public void addStackTraceElement(Node n) {
        this.getStackTrace().push(n);
        if (this.getStackTrace().size() > 10) {
            this.getStackTrace().remove();
        }
    }
    
    public void printStackTrace() {
        System.err.println("--- Start stacktrace ---");
        Iterator<Node> it = this.getStackTrace().iterator();
        while (it.hasNext()) {
            Node node = it.next();
            if (node instanceof UnaryNode) {
                Node n = ((UnaryNode) node).getNode();
                System.err.println("  " + n);
            }
            else if (node instanceof BinaryNode) {
                Node lhs = ((BinaryNode) node).getLhs();
                Node rhs = ((BinaryNode) node).getRhs();
                System.err.println("  " + lhs + "OP" + rhs);
            }
            else {
                System.err.println("  " + node);
            }
            
        }
        System.err.println("--- End stacktrace ---");
    }
}