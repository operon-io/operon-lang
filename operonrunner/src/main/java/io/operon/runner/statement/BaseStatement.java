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

package io.operon.runner.statement;

import java.util.Map;
import java.util.HashMap;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.OperonValueConstraint;
import io.operon.runner.node.type.Path;
import io.operon.runner.ExceptionHandler;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

import com.google.gson.annotations.Expose;

public abstract class BaseStatement {
     // no logger 
    @Expose private String id;
    private Map<String, OperonValue> runtimeValues;
    private Statement previousStatement;
    @Expose private Map<String, LetStatement> letStatements;
    private ExceptionHandler exceptionHandler;
    private boolean errorHandled;
    private OperonValue evaluatedValue;
    private OperonValueConstraint constraint;
    
    /**
     * Allow access to root context, allows 
     * e.g. accessing other value-references.
     *
     */
    private Context operonContext;
    
    // The expr for the statement
    @Expose private Node node;
    
    //
    // This is required for specified new way how attributes should work:
    //
    //  - keep track of the currentPath (in each scope (statement))
    //  - currentPath ('~@') allows to get pos(), next(), previous(), parent and root,
    //    i.e. the attributes that we currently save into each Node,
    //    straight by inspecting the '~@' and the root-value it was linked with.
    // 
    private Path currentPath;
    
    public BaseStatement(Context ctx) {
        this.operonContext = ctx;
        this.runtimeValues = new HashMap<String, OperonValue>();
        this.letStatements = new HashMap<String, LetStatement>();
        this.setErrorHandled(false);
    }

    public void setPreviousStatement(Statement stmt) {
        this.previousStatement = stmt;
    }
    
    public Statement getPreviousStatement() {
        return this.previousStatement;
    }
    
    public static Statement getRootStatement(Statement stmt) {
        Statement previous = stmt.getPreviousStatement();
        Statement root = stmt;
        while (previous != null) {
            root = previous;
            previous = previous.getPreviousStatement();
        }
        return root;
    }
    
    public OperonValue getCurrentValue() {
        OperonValue currentValue = this.getRuntimeValues().get("@");
        return currentValue;
    }
    
    public void setCurrentValue(OperonValue currentValue) {
        this.getRuntimeValues().put("@", currentValue);
    }
    
    public Map<String, OperonValue> getRuntimeValues() {
        return runtimeValues;
    }
    
    protected void setRuntimeValues(Map<String, OperonValue> rtv) {
        this.runtimeValues = rtv;
    }
    
    public Context getOperonContext() {
        return this.operonContext;
    }
    
    public Node getNode() {
        return this.node;
    }
    
    public void setNode(Node node) {
        this.node = node;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getId() {
        return this.id;
    }
    
    public void setEvaluatedValue(OperonValue ev) {
        this.evaluatedValue = ev;
    }
    
    public OperonValue getEvaluatedValue() {
        return this.evaluatedValue;
    }
    
    public void setOperonValueConstraint(OperonValueConstraint c) {
        this.constraint = c;
    }

    public OperonValueConstraint getOperonValueConstraint() {
        return this.constraint;
    }

    public void setErrorHandled(boolean h) {
        this.errorHandled = h;
    }
    
    public boolean isErrorHandled() {
        return this.errorHandled;
    }

    public void setExceptionHandler(ExceptionHandler eh) {
        this.exceptionHandler = eh;
    }

    public ExceptionHandler getExceptionHandler() {
        return this.exceptionHandler;
    }

    public Map<String, LetStatement> getLetStatements() {
        return this.letStatements;
    }

    // used in opercones:
    public void setLetStatements(Map<String, LetStatement> ls) {
        this.letStatements = ls;
    }

    public Path getCurrentPath() {
        if (this.currentPath == null) {
            Statement prevStmt = this.getPreviousStatement();
            while (prevStmt != null) {
                if (prevStmt.getCurrentPath() != null) {
                    //System.out.println("BaseStatement :: resolved currentPath from previousStatement");
                    return prevStmt.getCurrentPath();
                }
                prevStmt = this.getPreviousStatement();
            }
            //
            // No Path resolved from previous statements: create new empty-path:
            //
            return new Path(new DefaultStatement(OperonContext.emptyContext));
        }
        return this.currentPath;
    }
    
    public void setCurrentPath(Path newPath) {
        this.currentPath = newPath;
    }

    @Override
    public String toString() {
        return this.getId();
    }
}