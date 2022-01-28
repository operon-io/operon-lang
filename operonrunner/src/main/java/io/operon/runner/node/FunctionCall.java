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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import io.operon.runner.Context;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.OperonValueConstraint;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.node.FunctionStatementParam;
import io.operon.runner.processor.function.core.resolver.CoreFunctionResolver;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// => functionName()
//
public class FunctionCall extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FunctionCall.class);
    private String functionFQName;

    private FunctionStatement functionStatement; // function execution context
    
    private List<Node> arguments;
    
    public FunctionCall(Statement stmnt, String funcFqName) throws OperonGenericException {
        super(stmnt);
        this.arguments = new ArrayList<Node>();
        this.setFunctionFQName(funcFqName);
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER FunctionCall.evaluate() :: " + this.getFunctionFQName());
        
        FunctionStatement fnStatement = FunctionRef.resolveUserFunction(this.getStatement(),
            this.getFunctionFQName()/*, this.getStatement().isPrototype()*/);
        
        if (fnStatement == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_CALL", "SIGNATURE_MISMATCH", this.getFunctionFQName());
        }

        this.setFunctionStatement(fnStatement);
        
        List<FunctionStatementParam> paramsOriginal = this.getFunctionStatement().getParams();
        // Create a copy-list, otherwise when removing the arguments from the original list,
        // they would get permanently deleted, prohibiting evaluating this function (on the second round)
        // TODO: it could be better to copy the whole structure (i.e. also the constraints), not just the string-value.
        List<String> paramsCopy = new ArrayList<String>();
        for (int i = 0; i < paramsOriginal.size(); i ++) {
            String copyArg = new String(paramsOriginal.get(i).getParam());
            paramsCopy.add(copyArg);
        }
        log.debug("  function statement paramsCopy: " + paramsCopy);

        OperonValue currentValue = this.getStatement().getCurrentValue();
        OperonValue currentValueCopy = null; // TODO: remove this variable
        log.debug("  currentValue resolved: " + currentValue);
        //System.out.println("FunctionCall cv=" + currentValue);

        // FunctionRef.invoke() also sets the currentValue for FunctionStatement,
        // therefore it must be checked if FunctionStatement's currentValue has
        // already been set, to prevent overwriting it.
        if (currentValue == null) { // ADDED THIS
            if (this.getFunctionStatement().getCurrentValue() == null) {
                // http-server isd: cookies. evaluate jsonValue, caused NullPointerException here
                log.debug("FunctionCall :: resolve currentValueCopy from previousStatement (currentValue was null");
                currentValueCopy = this.getStatement().getPreviousStatement().getCurrentValue();
                //System.out.println("SET cv for functionStatement: " + currentValueCopy);
            }
            else {
                log.debug("FunctionCall :: resolve currentValueCopy from functionStatement's currentValue");
                currentValueCopy = this.getFunctionStatement().getCurrentValue(); // this has value FunctionTests#functionValueRefTest
                log.debug("  resolved cv=" + currentValueCopy);
                //currentValueCopy = this.getStatement().getCurrentValue();
                //this.getStatement().setCurrentValue(currentValueCopy);
            }
        }
        else {
            currentValueCopy = currentValue;
        }
        this.getFunctionStatement().setCurrentValue(currentValueCopy); // ADDED THIS
        // Set the fnStatement's runtimeValues with the given parameters, by matching the correct order
        
        // Check that paramsCopy and given arguments size matches:
        if (arguments.size() != paramsCopy.size()) {
            log.debug("ERR: paramsCopy :: " + paramsCopy);
            log.debug("ERR: params :: " + arguments);
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_CALL", "ARGUMENTS", "incorrect amount of arguments. Required: " + arguments.size() + ", but was " + paramsCopy.size());
        }
        
        //
        // Set the arguments
        //
        // First round: set the named args
        // Second round: set the reguler args
        //
        //System.out.println("=== Set arguments ===");
        List<Integer> namedValueParamIndexes = new ArrayList<Integer>();
        for (int i = 0; i < arguments.size(); i ++) {
            Node param = this.getArguments().get(i);

            if (param instanceof UnaryNode) {
                log.debug("FunctionCall :: UnaryNode");
                param = param.evaluate();
            }
            
            if (param instanceof FunctionNamedArgument) {
                log.debug("FunctionCall :: named argument :: counter :: " + i);
                //System.out.println("FunctionCall :: named argument :: counter :: " + i);
                FunctionNamedArgument fna = (FunctionNamedArgument) param;
                String argName = fna.getArgumentName();
                log.debug("  >> argName :: " + argName);
                Node argValue = fna.getArgumentValue();
                //log.debug("  REMOVE THIS >> FunctionCall :: before evaluating argValue :: " + argValue);
                
                // If boxed as FunctionNamedArgument, then unbox it:
                // This kind of boxing might happen, when passing functionRef through multiple Let-statements.
                // TODO: while loop, or better: own recursive unboxing function.
                //  This version only unboxes one level, but multiple levels might still occur.
                // TODO: does the next "else if" also require this check?
                
                argValue = this.unboxArgumentToOperonValue(argValue);
                log.debug("NAMED ARG :: PUT :: " + argName);
                
                this.getFunctionStatement().getRuntimeValues()
                    .put(argName, (OperonValue) argValue);
                
                // Remove that named argument:
                int removePos = 0;
                for (int j = 0; j < paramsCopy.size(); j ++) {
                    if (paramsCopy.get(j).equals(argName)) {
                        removePos = j;
                        break;
                    }
                }
                paramsCopy.remove(removePos);
                
                // Find the matching position for argument from params:
                int originalPosition = -1;
                for (int j = 0; j < paramsOriginal.size(); j ++) {
                    FunctionStatementParam fsParam =  paramsOriginal.get(j);
                    if (fsParam.getParam().equals(argName)) {
                        originalPosition = j;
                        break;
                    }
                }
                
                namedValueParamIndexes.add(originalPosition);
                
                if (originalPosition > -1) {
                    //System.out.println("FunctionCall :: named argument :: matchValueToArgumentConstraintAndEvaluate :: " + originalPosition);
                    this.matchValueToArgumentConstraintAndEvaluate(paramsOriginal, originalPosition, (OperonValue) argValue);
                }
            }

            else if (param instanceof FunctionRefNamedArgument) {
                // NOTE: this is same as above. Check if own method could be created.
                log.debug("FunctionCall :: function ref named argument :: counter :: " + i);
                //System.out.println("FunctionCall :: named ref argument :: counter :: " + i);
                FunctionRefNamedArgument frna = (FunctionRefNamedArgument) param;
                String argName = frna.getArgumentName();
                log.debug("  >> argName :: " + argName);
                //System.out.println("  >> argName :: " + argName);
                Node argValue = frna.getExprNode();
                
                argValue = this.unboxArgumentToOperonValue(argValue);
                
                log.debug("NAMED ARG :: PUT :: " + argName);
                
                this.getFunctionStatement().getRuntimeValues()
                    .put(argName, (OperonValue) argValue);
                // Remove that named argument:
                int removePos = 0;
                for (int j = 0; j < paramsCopy.size(); j ++) {
                    if (paramsCopy.get(j).equals(argName)) {
                        removePos = j;
                        break;
                    }
                }
                paramsCopy.remove(removePos);
                
                // Find position from originalArguments:
                int originalPosition = -1;
                for (int j = 0; j < paramsOriginal.size(); j ++) {
                    FunctionStatementParam fsParam =  paramsOriginal.get(j);
                    if (fsParam.getParam().equals(argName)) {
                        originalPosition = j;
                        break;
                    }
                }
                
                namedValueParamIndexes.add(originalPosition);
                
                if (originalPosition > -1) {
                    //System.out.println("FunctionCall :: named ref argument :: matchValueToArgumentConstraintAndEvaluate :: " + i);
                    this.matchValueToArgumentConstraintAndEvaluate(paramsOriginal, originalPosition, (OperonValue) argValue);
                }
            }
            
            this.getStatement().setCurrentValue(currentValueCopy); // Set back the current-value, so previous evaluation won't change it for next argument.
        }
        
        // System.out.println(">> paramsCopy: " + paramsCopy);
        
        if (paramsCopy.size() > 0) {
            for (int i = 0; i < arguments.size(); i ++) {
                Node param = this.getArguments().get(i);
    
                if (param instanceof UnaryNode) {
                    log.debug("FunctionCall :: UnaryNode");
                    param = param.evaluate();
                }
                
                if (param instanceof FunctionNamedArgument) {
                    continue;
                }
                
                else if (param instanceof FunctionRefNamedArgument) {
                    continue;
                }
                
                else if (param instanceof FunctionRegularArgument) {
                    log.debug("FunctionCall :: regular argument :: counter :: " + i);
                    Node argValue = ((FunctionRegularArgument) param).getArgument();
                    if (argValue == null) {
                        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_CALL", "ARGUMENTS", "Argument was null.");
                    }
                    if (argValue instanceof OperonValue == false) {
                        log.debug("  FunctionCall :: unboxing arg");
                        argValue = argValue.evaluate();
                    }
                    
                    String functionStatementArgument = paramsCopy.get(0);
                    log.debug("  FunctionCall :: setting arg :: " + functionStatementArgument);
                    
                    
                    //
                    // We need to check against those that have been already matched out in the previous phase 
                    // (when we matched against the named values), and not select the same index, but select the next index
                    // that was not already used.
                    //
                    int testIndex = i;
                    while (namedValueParamIndexes.contains(testIndex)) {
                        testIndex += 1;
                    }
                    if (testIndex >= paramsOriginal.size()) {
                        testIndex -= 1;
                    }
                    //System.out.println(">>> regular argf: matchValueToArgumentConstraintAndEvaluate: " + testIndex);
                    this.matchValueToArgumentConstraintAndEvaluate(paramsOriginal, testIndex, (OperonValue) argValue);
                    
                    log.debug("REGULAR ARG :: PUT :: " + functionStatementArgument);
                    this.getFunctionStatement().getRuntimeValues()
                        .put(functionStatementArgument, (OperonValue) argValue);
                    
                    paramsCopy.remove(0);
                }
    
                else {
                    //
                    // TODO: should this be matched with Constraint as well?
                    //
                    log.debug("FunctionCall :: untyped argument");
                    String functionStatementArgument = paramsCopy.get(0);
                    this.getFunctionStatement().getRuntimeValues()
                        .put(functionStatementArgument, (OperonValue) param);
                    paramsCopy.remove(0);
                }
                
                this.getStatement().setCurrentValue(currentValueCopy); // Set back the current-value, so previous evaluation won't change it for next argument.
            }
        }
        
        log.debug("FunctionCall :: ARGS SET: " + this.getFunctionStatement().getRuntimeValues().keySet().toString());

        OperonValue result = this.getFunctionStatement().evaluate();
        this.setEvaluatedValue(result);
        this.getStatement().setCurrentValue(result);
        this.getFunctionStatement().setCurrentValue(null);
        //this.synchronizeState(); // ADDED THIS FOR UNIT TEST: UpdateValueTests
        return result;
    }

    // If argument has OperonValueConstraint, then evaluate the constraint against the
    // assigned value.
    // 
    // Throws Exception if the constraint is violated.
    private void matchValueToArgumentConstraintAndEvaluate(List<FunctionStatementParam> paramsOriginal, int index, OperonValue value) throws OperonGenericException {
        //System.out.println("matchValueToArgumentConstraintAndEvaluate");
        //System.out.println(" :: paramsOriginal :: ");
        
        //for (int i = 0; i < paramsOriginal.size(); i ++) {
        //    String param = new String(paramsOriginal.get(i).getParam());
        //    System.out.println(" :: param :: " + param);
        //}
        
        //System.out.println(" :: index :: " + index);
        //System.out.println(" :: jsonValue :: " + value);
        
        if (paramsOriginal.get(index).getOperonValueConstraint() != null) {
            OperonValueConstraint jvc = paramsOriginal.get(index).getOperonValueConstraint();
            //System.out.println(" :: OperonValueConstraint found for the index: " + jvc);
            OperonValueConstraint.evaluateConstraintAgainstOperonValue(value, jvc);
        }

        return;
    }

    private OperonValue unboxArgumentToOperonValue(Node argValue) throws OperonGenericException {
        if (argValue instanceof FunctionNamedArgument) {
            log.debug("  >> FunctionCall :: unbox FunctionNamedArgument");
            argValue = ((FunctionNamedArgument) argValue).getArgumentValue();
        }
        
        if (!(argValue instanceof OperonValue)) {
            log.debug("  >> FunctionCall :: evaluate argValue");
            argValue = argValue.evaluate();
            log.debug("  >> FunctionCall :: done evaluating argValue");
            return this.unboxArgumentToOperonValue(argValue);
        }
        
        else {
            return (OperonValue) argValue;
        }
    }

    public void setFunctionFQName(String fqn) {
        this.functionFQName = fqn;
    }
    
    public String getFunctionFQName() {
        return this.functionFQName;
    }
    
    public FunctionStatement getFunctionStatement() {
        return this.functionStatement;
    }
    
    public void setFunctionStatement(FunctionStatement funcStmnt) {
        this.functionStatement = funcStmnt;
    }
    
    public List<Node> getArguments() {
        return this.arguments;
    }
    
    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}