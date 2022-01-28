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
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ValueRef extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(ValueRef.class);
    private Node computedValueRef; // $(expr), which after evaluated will set the this.valueRef
    private String valueRef; // this is the symbol we are looking for (@, $, _$, $foo, etc.)
    private List<String> namespaces;
    private boolean valueBoundToOperator = false;
    private ValueLocationType valueLocation = ValueLocationType.UNKNOWN;
    
    public ValueRef(Statement stmnt) {
        super(stmnt);
        this.namespaces = new ArrayList<String>();
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER ValueRef.evaluate()");
        // Value resolution strategy:
        
        // TODO: check from modules, which have the matching namespace
        
        // 1. Check value from local scope
        // 2. Check value from parent scope(s) (e.g. if lambda-function).
        // 3. Check value from let-statements.
        // 4. Check value from module with same namespace
        
        //System.out.println("ValueRef :: evaluate, stmtClass=" + this.getStatement().getClass().getName());
        //System.out.println("VR >> stmt id=" + stmtId + ", stmt = " + this.getStatement() + ", prototype: " + this.getStatement().isPrototype());
        log.debug("    >> Check value [" + getValueRef() + "] from stmt: " + this.getStatement().getId());
        //System.out.println("    >> Check value [" + getValueRef() + "] from stmt: " + this.getStatement().getId());
        
        // **************************************** DEBUG
        //System.out.println("ValueRef :: CV :: " + this.getStatement().getCurrentValue());
        //System.out.println("ValueRef :: statement :: " + this.getStatement());
        // **************************************** DEBUG
        
        // add the namespace if present
        String getKey = this.buildKey(this.getNamespaces());
        
        OperonValue value = null;
        
        if (getKey.equals("@")) {
            //System.out.println("ValueRef :: CV");
            value = this.getStatement().getCurrentValue();
            //System.out.println("ValueRef :: CV :: " + value);
        }
        
        else if (getKey.equals("$")) {
            // If used from module, then resolve the rootContext first:
            //System.out.println("ValueRef :: resolve $");
            Context ctx = BaseContext.getRootContextByStatement(this.getStatement());
            if (ctx instanceof OperonContext) {
                value = ((OperonContext) ctx).getFromStatement().getRuntimeValues().get("$");
            }
            else if (ctx instanceof OperonTestsContext) {
                value = ((OperonTestsContext) ctx).getFromStatement().getRuntimeValues().get("$");
            }
        }
        
        // Use the cached route-information to value-location:
        else if (this.valueLocation != ValueLocationType.UNKNOWN) {
            if (this.valueLocation == ValueLocationType.LET_STAMENT) {
                value = this.resolveFromLetStatement(getKey);
            }
            
            else if (this.valueLocation == ValueLocationType.STAMENT_OWN) {
                value = this.resolveFromStatementOwnRuntimeValues(getKey);
            }
            
            else if (this.valueLocation == ValueLocationType.FUNCTION_STATEMENT) {
                value = this.resolveFromFunctionStatement(getKey);
            }
            
            else if (this.valueLocation == ValueLocationType.SELECT_STATEMENT) {
                value = this.resolveFromSelectStatement(getKey);
            }
            
            else if (this.valueLocation == ValueLocationType.EXCEPTION_STATEMENT) {
                value = this.resolveFromExceptionStatement(getKey);
            }
            
            else if (this.valueLocation == ValueLocationType.DEFAULT_STATEMENT) {
                value = this.resolveFromDefaultStatement(getKey);
            }
            
            else if (this.valueLocation == ValueLocationType.PARENT_SCOPE) {
                value = this.resolveFromParentScope(getKey);
            }
            
            else if (this.valueLocation == ValueLocationType.SUB_MODULE) {
                value = this.resolveFromParentScope(getKey);
            }
        }

        else {
            //
            // Get from statement's own runtimeValues (statement may be e.g. Select, Let, Function)
            //
            //System.out.println("ValueRef :: statement :: " + this.getStatement().getId() + ", getKey=" + getKey);
            //System.out.println(" >> runtimeValues :: " + this.getStatement().getRuntimeValues());
            value = this.resolveFromStatementOwnRuntimeValues(getKey);
            
            //
            // If we are in a FunctionStatement, then check from function's LetStatements
            //
            if (value == null && this.getStatement() instanceof FunctionStatement) {
                value = this.resolveFromFunctionStatement(getKey);
            }

            else if (value == null && this.getStatement() instanceof SelectStatement) {
                value = this.resolveFromSelectStatement(getKey);
            }

            else if (value == null && this.getStatement() instanceof ExceptionStatement) {
                value = this.resolveFromExceptionStatement(getKey);
            }
            
            else if (value == null && this.getStatement() instanceof DefaultStatement) {
                value = this.resolveFromDefaultStatement(getKey);
            }
            
            //
            // Key didn't exist, check from parent-scopes:
            //
            if (value == null) {
                log.debug("    >> Value not resolved, checking from parent-scopes.");
                value = this.resolveFromParentScope(getKey);
            }
            
            //
            // Key didn't exist, check from letStatements:
            //
            if (value == null) {
                //
                // NOTE: here we might be in a module-context, or in the main-context
                //
                value = this.resolveFromLetStatement(getKey);
            }
            
            //
            // Check from submodules:
            //
            //  TODO: This requires recursive approach, because we need to check with same steps as above!
            //
            if (value == null && this.getNamespaces().size() > 0) {
                value = this.resolveFromSubmodules(getKey);
            }
            
            if (value == null) {
                log.debug("ValueRef :: cannot resolve value :: " + getKey);
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "VALUE_REF", "ERROR", "Cannot resolve value: " + getKey);
            }
        }

        log.debug("Value resolved.");
        log.debug(">> Value :: " + value);
        
        if (valueBoundToOperator == false) {
            this.doGlobalBindValue(value);
        }
        
        log.debug(">> Value [" + getKey + "] bindings size now :: " + value.getBindings().size() + ", do bindings :: " + value.getDoBindings());
        
        // update the currentValue from the statement
        //System.out.println("ValueRef evaluation done");
        //System.out.println("VALUE REF, SET CV FOR STMT :: " + this.getStatement().getId() + " :: " + value);
        //System.out.println("VR done. stmt prototype: " + this.getStatement().isPrototype());
        //System.out.println("VR done. value stmt prototype: " + value.getStatement().isPrototype());
        this.getStatement().setCurrentValue(value);
        return value;
    }


    //
    // Strategies:
    //   These are used to optimize the reoccurring fetch of the value
    //   Each strategy sets the location if value was found.
    //   
    private OperonValue resolveFromStatementOwnRuntimeValues(String getKey) throws OperonGenericException {
        OperonValue result = this.getStatement().getRuntimeValues().get(getKey);
        if (result != null) {
            this.valueLocation = ValueLocationType.STAMENT_OWN;
        }
        return result;
    }
    
    private OperonValue resolveFromFunctionStatement(String getKey) throws OperonGenericException {
        LetStatement letStatement = (LetStatement) ((FunctionStatement) this.getStatement()).getLetStatements().get(getKey);
        OperonValue result = getValueFromLetStatement(letStatement, getKey);
        if (result != null) {
            this.valueLocation = ValueLocationType.FUNCTION_STATEMENT;
        }
        return result;
    }
    
    private OperonValue resolveFromSelectStatement(String getKey) throws OperonGenericException {
        LetStatement letStatement = (LetStatement) ((SelectStatement) this.getStatement()).getLetStatements().get(getKey);
        OperonValue result = getValueFromLetStatement(letStatement, getKey);
        if (result != null) {
            this.valueLocation = ValueLocationType.SELECT_STATEMENT;
        }
        return result;
    }
    
    private OperonValue resolveFromExceptionStatement(String getKey) throws OperonGenericException {
        LetStatement letStatement = (LetStatement) ((ExceptionStatement) this.getStatement()).getLetStatements().get(getKey);
        OperonValue result = getValueFromLetStatement(letStatement, getKey);
        if (result != null) {
            this.valueLocation = ValueLocationType.EXCEPTION_STATEMENT;
        }
        return result;
    }
    
    private OperonValue resolveFromDefaultStatement(String getKey) throws OperonGenericException {
        LetStatement letStatement = (LetStatement) ((DefaultStatement) this.getStatement()).getLetStatements().get(getKey);
        OperonValue result = getValueFromLetStatement(letStatement, getKey);
        if (result != null) {
            this.valueLocation = ValueLocationType.DEFAULT_STATEMENT;
        }
        return result;
    }
    
    private OperonValue resolveFromLetStatement(String getKey) throws OperonGenericException {
        log.debug("    >> Value not resolved, checking from Context's Let-statements.");
        LetStatement letStatement = (LetStatement) this.getStatement().getOperonContext().getLetStatements().get(getKey);
        OperonValue result = getValueFromLetStatement(letStatement, getKey);
        if (result != null) {
            this.valueLocation = ValueLocationType.LET_STAMENT;
        }
        return result;
    }
    
    private OperonValue resolveFromParentScope(String getKey) throws OperonGenericException {
        //System.out.println("ValueRef :: resolveFromParentScope :: " + this.getStatement());
        OperonValue value = null;
        Statement parent = this.getStatement().getPreviousStatement();
        
        while (parent != null && value == null) {
            if (parent != null) {
                value = parent.getRuntimeValues().get(getKey);
                if (value == null) {
                    // Check from parent't LetStatement, if parent is FunctionStatement
                    if (parent instanceof FunctionStatement){ 
                        LetStatement letStatement = (LetStatement) ((FunctionStatement) parent).getLetStatements().get(getKey);
                        value = getValueFromLetStatement(letStatement, getKey);
                    }

                    else if (parent instanceof SelectStatement){ 
                        LetStatement letStatement = (LetStatement) ((SelectStatement) parent).getLetStatements().get(getKey);
                        value = getValueFromLetStatement(letStatement, getKey);
                    }

                    else if (parent instanceof ExceptionStatement){ 
                        LetStatement letStatement = (LetStatement) ((ExceptionStatement) parent).getLetStatements().get(getKey);
                        value = getValueFromLetStatement(letStatement, getKey);
                    }
                    
                    else if (parent instanceof DefaultStatement){ 
                        LetStatement letStatement = (LetStatement) ((DefaultStatement) parent).getLetStatements().get(getKey);
                        value = getValueFromLetStatement(letStatement, getKey);
                    }
        
                    // If not found, then try from parent's parent...
                    parent = parent.getPreviousStatement();
                }
            }
        }
        
        if (value != null) {
            this.valueLocation = ValueLocationType.PARENT_SCOPE;
        }
        
        return value;
    }
    
    private OperonValue resolveFromSubmodules(String getKey) throws OperonGenericException {
        OperonValue value = null;
        String ns1 = this.getNamespaces().get(0);
        log.debug("Checking from module :: " + ns1);
        Context module = this.getStatement().getOperonContext().getModules().get(ns1);
        if (module != null) {
            log.debug("ValueRef :: resolved module");
            
            Map<String, LetStatement> letStatements = module.getLetStatements();
            
            if (letStatements.size() > 0) {
                // NOTE: not using getKey, because it has also the namespace included.
                LetStatement letStatement = (LetStatement) letStatements.get(this.getValueRef());
                
                if (letStatement != null) {
                    log.debug("ValueRef :: found LetStatement from Module, evaluating it 1");
                    OperonValue currentValue = this.getStatement().getCurrentValue(); // FIXME: might be null: ImportTests#import14Test
                    OperonValue currentValueCopy = currentValue.copy();
                    letStatement.setCurrentValue(currentValueCopy);
                    value = (OperonValue) letStatement.evaluate();
                }
                else {
                    // Try from sub-namespace:
                    String subGetKey = this.buildKey(this.getNamespaces().subList(1, this.getNamespaces().size()));
                    letStatement = (LetStatement) letStatements.get(subGetKey);
                    
                    if (letStatement != null) {
                        log.debug("ValueRef :: found LetStatement from Module, evaluating it 2");
                        OperonValue currentValue = this.getStatement().getCurrentValue();
                        OperonValue currentValueCopy = currentValue.copy();
                        letStatement.setCurrentValue(currentValueCopy);
                        value = (OperonValue) letStatement.evaluate();
                    }
                }
            }
        }
        
        if (value != null) {
            this.valueLocation = ValueLocationType.SUB_MODULE;
        }
        return value;
    }
    
    // Helpers:




    private OperonValue getValueFromLetStatement(LetStatement letStatement, String getKey) throws OperonGenericException {
        if (letStatement != null) {
            OperonValue value = (OperonValue) letStatement.evaluate();
            this.doLetBindValue(value, letStatement);
            //System.out.println("VALUE REF :: resolved from Let-statement :: " + getKey);
            // Put the evaluated value in the runtimeValues of the previous-statement
            
            // Never reset:
            if (letStatement.getResetType() == LetStatement.ResetType.NEVER) {
                this.getStatement().getPreviousStatement().getRuntimeValues().put(getKey, value);
            }
            return value;
        }
        return null;
    }
    
    private String buildKey(List<String> nsList) throws OperonGenericException {
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
        if (this.getComputedValueRef() == null) {
            getKey.append(this.getValueRef());
        }
        else {
            StringType strVal = (StringType) this.getComputedValueRef().evaluate();
            getKey.append("$" + strVal.getJavaStringValue());
        }
        return getKey.toString();
    }
    
    //
    // Accesses the global-bindings from OperonContext, by matching the ValueRef'ed key.
    // If found, then sets the Operator for the ValueRef'ed value, which is later executed
    // when the value is used in the corresponding operator.
    // 
    private void doGlobalBindValue(OperonValue value) throws OperonGenericException {
        if (this.getDoBindings()) {
            Map<String, List<Operator>> bindValues = this.getStatement().getOperonContext().getBindValues();
            
            if (bindValues.size() > 0 && bindValues.get(this.getValueRef()) != null) {
                
                String getKey = this.buildKey(this.getNamespaces());
                
                log.debug(" >> putting bindings: " + getKey);
                List<Operator> operators = bindValues.get(getKey);
                for (Operator op : operators) {
                    String operatorStr = op.getOperator();
                    log.debug("  >> binding :: " + operatorStr);
                    //System.out.println("PUT from ValueRef: " + getKey);
                    value.getBindings().put(operatorStr, op); // could also put just FunctionRef from Operator
                }
            }
        }
        valueBoundToOperator = true;
    }
    
    private void doLetBindValue(OperonValue value, LetStatement letStatement) throws OperonGenericException {
        if (this.getDoBindings()) {
            Map<String, List<Operator>> bindValues = letStatement.getBindValues();
            
            if (bindValues.size() > 0 && bindValues.get(this.getValueRef()) != null) {
                
                String getKey = this.buildKey(this.getNamespaces());
                
                log.debug(" >> putting bindings: " + getKey);
                List<Operator> operators = bindValues.get(getKey);
                for (Operator op : operators) {
                    String operatorStr = op.getOperator();
                    log.debug("  >> binding :: " + operatorStr);
                    //System.out.println("PUT from ValueRef: " + getKey);
                    value.getBindings().put(operatorStr, op); // could also put just FunctionRef from Operator
                }
                valueBoundToOperator = true;
            }
        }
    }

    public void setValueRef(String valueRef) {
        this.valueRef = valueRef;
    }

    public String getValueRef() {
        return this.valueRef;
    }
    
    public void setComputedValueRef(Node expr) {
        this.computedValueRef = expr;
    }
    
    public Node getComputedValueRef() {
        return this.computedValueRef;
    }
    
    public List<String> getNamespaces() {
        return this.namespaces;
    }

    private enum ValueLocationType {
        UNKNOWN, LET_STAMENT, STAMENT_OWN,
        FUNCTION_STATEMENT, SELECT_STATEMENT,
        EXCEPTION_STATEMENT, DEFAULT_STATEMENT,
        PARENT_SCOPE, SUB_MODULE;
    }

    public String toString() {
        //return this.getEvaluatedValue().toString();
        return this.getValueRef();
    }

}