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

package io.operon.runner.node;

import io.operon.runner.BaseContext;
import io.operon.runner.Context;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonTestsContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.statement.SelectStatement;
import io.operon.runner.statement.ExceptionStatement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class Assign extends AbstractNode implements Node {
     // no logger 
    private String valueRef;
    private List<String> namespaces;
    private boolean valueBoundToOperator = false;
    private Node assignExpr;
    
    public Assign(Statement stmnt) {
        super(stmnt);
        this.namespaces = new ArrayList<String>();
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER Assign.evaluate()");
        // Value resolution strategy:
        
        // TODO: check from modules, which have the matching namespace
        
        // 1. Check value from local scope
        // 2. Check value from parent scope(s) (e.g. if lambda-function).
        // 3. Check value from let-statements.
        // 4. Check value from module with same namespace
        
        //System.out.println("ValueRef :: evaluate, stmtClass=" + this.getStatement().getClass().getName());
        //System.out.println("VR >> stmt id=" + stmtId + ", stmt = " + this.getStatement() + ", prototype: " + this.getStatement().isPrototype());
        //:OFF:log.debug("    >> Check value [" + getValueRef() + "] from stmt: " + this.getStatement().getId());
        //System.out.println("    >> Check value [" + getValueRef() + "] from stmt: " + stmtId);
        
        // **************************************** DEBUG
        //System.out.println("ValueRef :: CV :: " + this.getStatement().getCurrentValue());
        // **************************************** DEBUG
        
        // add the namespace if present
        String getKey = this.buildKey(this.getNamespaces());
        
        boolean updated = false;
        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        Node assignExpr = this.getAssignExpr();
        assignExpr.getStatement().setCurrentValue(currentValue);
        OperonValue newValue = assignExpr.evaluate();
        
        if (getKey.equals("$")) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "ASSIGN", "ERROR", "Cannot override root-value.");
        }
        
        else {
            //
            // Get from statement's own runtimeValues (statement may be e.g. Select, Let, Function)
            //
            //System.out.println("ValueRef :: statement :: " + this.getStatement().getId() + ", getKey=" + getKey);
            //System.out.println(" >> runtimeValues :: " + this.getStatement().getRuntimeValues());
            if (this.getStatement().getRuntimeValues().containsKey(getKey)) {
                this.getStatement().getRuntimeValues().put(getKey, newValue);
                updated = true;
            }
            
            //
            // If we are in a FunctionStatement, then check from function's LetStatements
            //
            if (updated == false && this.getStatement() instanceof FunctionStatement) {
                LetStatement letStatement = (LetStatement) ((FunctionStatement) this.getStatement()).getLetStatements().get(getKey);
                if (letStatement != null) {
                    letStatement.setEvaluatedValue(newValue);
                    
                    updated = true;
                }
            }

            else if (updated == false && this.getStatement() instanceof SelectStatement) {
                LetStatement letStatement = (LetStatement) ((SelectStatement) this.getStatement()).getLetStatements().get(getKey);
                if (letStatement != null) {
                    letStatement.setEvaluatedValue(newValue);
                    
                    updated = true;
                }
            }

            else if (updated == false && this.getStatement() instanceof ExceptionStatement) {
                LetStatement letStatement = (LetStatement) ((ExceptionStatement) this.getStatement()).getLetStatements().get(getKey);
                if (letStatement != null) {
                    letStatement.setEvaluatedValue(newValue);
                    
                    updated = true;
                }
            }
            
            else if (updated == false && this.getStatement() instanceof DefaultStatement) {
                LetStatement letStatement = (LetStatement) ((DefaultStatement) this.getStatement()).getLetStatements().get(getKey);
                if (letStatement != null) {
                    letStatement.setEvaluatedValue(newValue);
                    
                    updated = true;
                }
            }
            
            //
            // Key didn't exist, check from parent-scopes:
            //
            if (updated == false) {
                //:OFF:log.debug("    >> Value not resolved, checking from parent-scopes.");
                Statement parent = this.getStatement().getPreviousStatement();
                while (parent != null && updated == false) {
                    if (parent != null) {
                        if (parent.getRuntimeValues().containsKey(getKey)) {
                            parent.getRuntimeValues().put(getKey, newValue);
                            updated = true;
                        }
                        
                        if (updated == false) {
                            // Check from parent't LetStatement, if parent is FunctionStatement
                            if (parent instanceof FunctionStatement){ 
                                LetStatement letStatement = (LetStatement) ((FunctionStatement) parent).getLetStatements().get(getKey);
                                if (letStatement != null) {
                                    letStatement.setEvaluatedValue(newValue);
                                    
                                    updated = true;
                                }
                            }

                            else if (parent instanceof SelectStatement){ 
                                LetStatement letStatement = (LetStatement) ((SelectStatement) parent).getLetStatements().get(getKey);
                                if (letStatement != null) {
                                    letStatement.setEvaluatedValue(newValue);
                                    
                                    updated = true;
                                }
                            }

                            else if (parent instanceof ExceptionStatement){ 
                                LetStatement letStatement = (LetStatement) ((ExceptionStatement) parent).getLetStatements().get(getKey);
                                if (letStatement != null) {
                                    letStatement.setEvaluatedValue(newValue);
                                    
                                    updated = true;
                                }
                            }
                            
                            else if (parent instanceof DefaultStatement){ 
                                LetStatement letStatement = (LetStatement) ((DefaultStatement) parent).getLetStatements().get(getKey);
                                if (letStatement != null) {
                                    letStatement.setEvaluatedValue(newValue);
                                    
                                    updated = true;
                                }
                            }
                
                            // If not found, then try from parent's parent...
                            parent = parent.getPreviousStatement();
                        }
                    }
                }
            }
            
            //
            // Key didn't exist, check from letStatements:
            //
            if (updated == false) {
                //
                // NOTE: here we might be in a module-context, or in the main-context
                //
                //:OFF:log.debug("    >> Value not resolved, checking from Context's Let-statements.");
                LetStatement letStatement = (LetStatement) this.getStatement().getOperonContext().getLetStatements().get(getKey);
                if (letStatement != null) {
                    letStatement.setEvaluatedValue(newValue);
                    
                    updated = true;
                }
            }
            
            //
            // Check from submodules:
            //
            //  TODO: This requires recursive approach, because we need to check with same steps as above!
            //
            if (updated == false && this.getNamespaces().size() > 0) {
                String ns1 = this.getNamespaces().get(0);
                //:OFF:log.debug("Checking from module :: " + ns1);
                Context module = this.getStatement().getOperonContext().getModules().get(ns1);
                if (module != null) {
                    //:OFF:log.debug("ValueRef :: resolved module");
                    
                    Map<String, LetStatement> letStatements = module.getLetStatements();
                    
                    if (letStatements.size() > 0) {
                        // NOTE: not using getKey, because it has also the namespace included.
                        LetStatement letStatement = (LetStatement) letStatements.get(this.getValueRef());
                        
                        if (letStatement != null) {
                            //:OFF:log.debug("ValueRef :: found LetStatement from Module, evaluating it 1");
                            letStatement.setEvaluatedValue(newValue);
                            
                            updated = true;
                        }
                        else {
                            // Try from sub-namespace:
                            String subGetKey = this.buildKey(this.getNamespaces().subList(1, this.getNamespaces().size()));
                            letStatement = (LetStatement) letStatements.get(subGetKey);
                            
                            if (letStatement != null) {
                                //:OFF:log.debug("ValueRef :: found LetStatement from Module, evaluating it 2");
                                letStatement.setEvaluatedValue(newValue);
                                
                                updated = true;
                            }
                        }
                    }
                }
            }

            if (updated == false) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "ASSIGN", "ERROR", "Cannot resolve value: " + getKey);
            }
        }

        this.getStatement().setCurrentValue(currentValue); // currentValue is left unmodified.
        return currentValue;
    }
    
    private String buildKey(List<String> nsList) {
        StringBuilder getKey = new StringBuilder();
        if (nsList.size() > 0) {
            for (int i = 0; i < nsList.size(); i++) {
                if (i == 0) {
                    getKey.append(nsList.get(i));
                }
                else {
                    getKey.append(":" + nsList.get(i));
                }
            }
            getKey.append(":");
        }
        getKey.append(this.getValueRef());
        return getKey.toString();
    }

    public void setValueRef(String valueRef) {
        this.valueRef = valueRef;
    }

    public String getValueRef() {
        return this.valueRef;
    }
    
    public List<String> getNamespaces() {
        return this.namespaces;
    }
    
    public void setAssignExpr(Node ae) {
        this.assignExpr = ae;
    }
    
    public Node getAssignExpr() {
        return this.assignExpr;
    }
    
    public String toString() {
        //return this.getEvaluatedValue().toString();
        return this.getValueRef();
    }

}