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
import java.util.HashMap;
import java.io.IOException;

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

import io.operon.runner.*;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// LambdaFunctionRef is used to reference function e.g. in the assignments.
//
public class LambdaFunctionRef extends OperonValue implements Node {
    private static Logger log = LogManager.getLogger(FunctionRef.class);

    private Node lambdaExpr;
    private Map<String, Node> params;
    private Map<String, OperonValueConstraint> paramConstraints;
    private OperonValue currentValueForFunction;
    private OperonValueConstraint constraint; // function output's constraint
    private boolean invokeOnAccess = false;
    
    public LambdaFunctionRef(Statement stmnt) {
        super(stmnt);
        this.params = new HashMap<String, Node>();
        this.paramConstraints = new HashMap<String, OperonValueConstraint>();
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER LambdaFunctionRef.evaluate(), stmt = " + this.getStatement().getPreviousStatement().getId());
        this.setUnboxed(false);
        return this;
    }
    
    public OperonValue invoke() throws OperonGenericException {
        log.debug("ENTER LambdaFunctionRef.invoke()");
        //System.out.println("  LambdaFunctionRef invoke cv=" + this.getCurrentValueForFunction() + ", stmt cv=" + this.getStatement().getCurrentValue() + ", stmt id=" + this.getStatement().getClass().getName());
        
        LambdaFunctionCall lfnCall = new LambdaFunctionCall(this.getStatement());
        this.getStatement().getPreviousStatement().setCurrentValue(this.getCurrentValueForFunction());
        
        lfnCall.setFunctionBodyExpr(this.getLambdaExpr());
        
        //System.out.println("  set params");
        //System.out.println("  >> " + this.getParams());
        lfnCall.setParams(this.getParams());
        //System.out.println("  set params done");
        
        OperonValue result = lfnCall.evaluate();
        return result;
    }
    
    protected static OperonValue setParamsAndInvokeLambdaFunctionRef(LambdaFunctionRef ref, OperonValue currentValueCopy, FunctionArguments fArgs) throws OperonGenericException {
        try {
            // The CV get this far correctly
            //System.out.println("LambdaFunctionRef.setParamsAndInvokeLambdaFunctionRef: cv=" + currentValueCopy);
            LambdaFunctionRef lfr = LambdaFunctionRef.setParams(ref, currentValueCopy, fArgs);
            log.debug("  invoking function");
            lfr.setCurrentValueForFunction(currentValueCopy);
            OperonValue result = lfr.invoke();
            // Check that the result does not violate constraint:
            if (lfr.getOperonValueConstraint() != null) {
                OperonValueConstraint.evaluateConstraintAgainstOperonValue(result, lfr.getOperonValueConstraint());
            }
            return result;
        } catch (IOException | ClassNotFoundException e) {
            return ErrorUtil.createErrorValueAndThrow(currentValueCopy.getStatement(), "LAMBDA_REF", "ERROR", e.getMessage());
        }
    }
    
    //
    // Used from FunctionRefInvoke
    //  TODO: should be used from LambdaFunctionRef (this class) also? Recursion
    //
    protected static LambdaFunctionRef setParams(LambdaFunctionRef lfr, OperonValue currentValueCopy, FunctionArguments fArgs) 
            throws OperonGenericException, IOException, ClassNotFoundException {
        //System.out.println("LFR setParams:");
        // Set the current value for FunctionRef:
        lfr.setCurrentValueForFunction(currentValueCopy);
        
        //
        // First set the known params, then resolve the placeholders (i.e. see what was left unused)
        //
        List<String> usedFunctionArgumentNames = new ArrayList<String>();
        
        if (fArgs != null) {
            log.debug("  setting the function arguments. Amount :: " + fArgs.getArguments().size());
            log.debug("  lfr params-size :: " + lfr.getParams().size());
            // Set the function arguments before invoking it
            int argPlaceholderCounter = 0;
            
            Map<String, Node> params = lfr.getParams();

            int paramsSetCounter = 0;
            boolean linkScope = true;
            
            for (Map.Entry<String, Node> param : params.entrySet()) {
                log.debug("  PARAM name :: " + param.getKey());
                
                if (param.getValue() instanceof FunctionRefNamedArgument) {
                    log.debug("  FunctionRefNamedArgument found from param");
                    FunctionRefNamedArgument arg = (FunctionRefNamedArgument) param.getValue();
                    
                    //
                    // Check that argument-value does not violate the param-constraint:
                    //
                    Node argExprCopy = null;
                    try {
                        //System.out.println("LFR DEEPCOPY 1");
                        argExprCopy = AbstractNode.deepCopyNode(arg.getStatement(), arg.getExprNode(), linkScope);
                    } catch (Exception e) {
                        ErrorUtil.createErrorValueAndThrow(arg.getStatement(), "LAMBDA_REF", "ERROR", e.getMessage());
                    }
                    OperonValueConstraint c = lfr.getParamConstraints().get(arg.getArgumentName());
                    OperonValue argExprCopyEvaluated = argExprCopy.evaluate();
                    if (c != null) {
                        OperonValueConstraint.evaluateConstraintAgainstOperonValue(argExprCopyEvaluated, c);
                    }
                    
                    // Bind param with value:
                    lfr.getParams().put(arg.getArgumentName(), arg.getExprNode());
                    usedFunctionArgumentNames.add(arg.getArgumentName());
                    log.debug("  Named argument found :: " + arg.getArgumentName());
                    paramsSetCounter += 1;
                }
                
                else if (param.getValue() instanceof FunctionRefArgumentPlaceholder) {
                    log.debug("  FunctionRefArgumentPlaceholder found from param");
                    //
                    // Resolve correct position
                    //
                    log.debug("  fArgs.getArguments().size() :: " + fArgs.getArguments().size());
                    for (int i = 0; i < fArgs.getArguments().size(); i ++) {
                        Node fArg = fArgs.getArguments().get(i);
                        
                        if (fArg instanceof FunctionNamedArgument) {
                            log.debug("  fArg matches FunctionNamedArgument");
                            String argName = ((FunctionNamedArgument) fArg).getArgumentName();
                            log.debug("  argName :: " + argName + ", param name :: " + param.getKey());
                            if (argName.equals(param.getKey())) {
                                log.debug("   FunctionNamedArgument found for :: " + argName);
                                Node argValue = ((FunctionNamedArgument) fArg).getArgumentValue();
                                
                                //
                                // Check that argument-value does not violate the param-constraint:
                                //
                                Node argExprCopy = null;
                                try {
                                    //System.out.println("LFR DEEPCOPY 2");
                                    argExprCopy = AbstractNode.deepCopyNode(argValue.getStatement(), argValue, linkScope);
                                } catch (Exception e) {
                                    ErrorUtil.createErrorValueAndThrow(argValue.getStatement(), "LAMBDA_REF", "ERROR Constraint eval deepCopy", e.getMessage());
                                }
                                OperonValueConstraint c = lfr.getParamConstraints().get(argName);
                                OperonValue argExprCopyEvaluated = argExprCopy.evaluate();
                                if (c != null) {
                                    OperonValueConstraint.evaluateConstraintAgainstOperonValue(argExprCopyEvaluated, c);
                                }
                    
                                // Bind param with value:
                                lfr.getParams().put(argName, argValue);
                                usedFunctionArgumentNames.add(argName);
                                
                                paramsSetCounter += 1;
                            }
                        }
                        else if (fArg instanceof FunctionRefNamedArgument) {
                            log.debug("  fArg matches FunctionRefNamedArgument");
                            String argName = ((FunctionRefNamedArgument) fArg).getArgumentName();
                            log.debug("  argName :: " + argName + ", param name :: " + param.getKey());
                            
                            if (argName.equals(param.getKey())) {
                                log.debug("   FunctionRefNamedArgument found for :: " + argName);
                                Node argValue = ((FunctionRefNamedArgument) fArg).getExprNode();
                                
                                //
                                // Check that argument-value does not violate the param-constraint:
                                //
                                Node argExprCopy = null;
                                try {
                                    //System.out.println("LFR DEEPCOPY 3");
                                    argExprCopy = AbstractNode.deepCopyNode(argValue.getStatement(), argValue, linkScope);
                                } catch (Exception e) {
                                    ErrorUtil.createErrorValueAndThrow(argValue.getStatement(), "LAMBDA_REF", "ERROR", e.getMessage());
                                }
                                OperonValueConstraint c = lfr.getParamConstraints().get(argName);
                                OperonValue argExprCopyEvaluated = argExprCopy.evaluate();
                                if (c != null) {
                                    OperonValueConstraint.evaluateConstraintAgainstOperonValue(argExprCopyEvaluated, c);
                                }
                                
                                // Bind param with value:
                                lfr.getParams().put(argName, argValue);
                                usedFunctionArgumentNames.add(argName);
                                
                                paramsSetCounter += 1;
                            }
                        }
                    }
                }
            }
            
            log.debug("   AMOUNT of params set :: " + paramsSetCounter);
            
            //
            // Loop again, and this time resolve the function-placeholders!
            // 
            List<Integer> usedRegularArgIndexes = new ArrayList<Integer>();
            log.debug("  Loop to determine the argument-placeholders.");
            
            for (Map.Entry<String, Node> param : params.entrySet()) {
                log.debug("  PARAM name :: " + param.getKey());
                
                if (param.getValue() instanceof FunctionRefArgumentPlaceholder) {
                    log.debug("  FunctionRefArgumentPlaceholder found from param");
                    // Check if arg-name has already been used.
                    // (If it was not used, then proceed).
                    boolean found = false;
                    for (String usedArg : usedFunctionArgumentNames) {
                        if (usedArg.equals(param.getKey())) {
                            found = true;
                            break;
                        }
                    }
                    log.debug("  Found :: " + found);
                    
                    //
                    // Not used in the previous loop, so use it now, and then add to used.
                    //
                    if (found == false) {
                        log.debug(param.getKey() + " was not used, using it now...");
                        usedFunctionArgumentNames.add(param.getKey());
                        
                        //
                        // Select correct arg-index from "functionArguments"
                        //
                        int argIndex = 0;
                        for (int i = 0; i < fArgs.getArguments().size(); i ++) {
                            Node fArg = fArgs.getArguments().get(i);
                            if (fArg instanceof FunctionRegularArgument) {
                                argIndex = i;
                                if (usedRegularArgIndexes.contains(i)) {
                                    continue;
                                }
                                else {
                                    usedRegularArgIndexes.add(i);
                                    log.debug("   FunctionRegularArgument found");
                                    Node argValue = ((FunctionRegularArgument) fArg).getArgument();
                                    
                                    //
                                    // Check that argument-value does not violate the param-constraint:
                                    //
                                    Node argExprCopy = null;
                                    try {
                                        //System.out.println("LFR DEEPCOPY 4 :: argValue stmt=" + argValue.getStatement() + ", linkScope=" + linkScope);
                                        argExprCopy = AbstractNode.deepCopyNode(argValue.getStatement(), argValue, linkScope);
                                    } catch (Exception e) {
                                        ErrorUtil.createErrorValueAndThrow(argValue.getStatement(), "LAMBDA_REF", "ERROR", e.getMessage());
                                    }
                                    OperonValueConstraint c = lfr.getParamConstraints().get(param.getKey());
                                    OperonValue argExprCopyEvaluated = argExprCopy.evaluate();
                                    if (c != null) {
                                        OperonValueConstraint.evaluateConstraintAgainstOperonValue(argExprCopyEvaluated, c);
                                    }
                                    
                                    // Bind param with value:
                                    lfr.getParams().put(param.getKey(), argValue);
                                    break;
                                }
                            }
                        }
                    }
                    else {
                        log.debug("  Nothing done for arg-placeholder (not regular argument)");
                    }
                }
            }
        }
        return lfr;

    }
    
    public Node getLambdaExpr() {
        return this.lambdaExpr;
    }
    
    public void setLambdaExpr(Node lExpr) {
        this.lambdaExpr = lExpr;
    }
    
    public void setParams(Map<String, Node> params) {
        this.params = params;
    }
    
    public Map<String, Node> getParams() {
        return this.params;
    }
    
    public void setParamConstraints(Map<String, OperonValueConstraint> constraintMap) {
        this.paramConstraints = constraintMap;
    }
    
    public Map<String, OperonValueConstraint> getParamConstraints() {
        return this.paramConstraints;
    }
    
    public void setCurrentValueForFunction(OperonValue cv) {
        this.currentValueForFunction = cv;
    }
    
    public OperonValue getCurrentValueForFunction() {
        return this.currentValueForFunction;
    }
    
    public void setOperonValueConstraint(OperonValueConstraint jvc) {
        this.constraint = jvc;
    }
    
    public OperonValueConstraint getOperonValueConstraint() {
        return this.constraint;
    }
    
    public boolean isInvokeOnAccess() {
        return this.invokeOnAccess;
    }
    
    public void setInvokeOnAccess(boolean ioa) {
        this.invokeOnAccess = ioa;
    }
    
    public String toString() {
        return "\"lambda:" + this.getParams().size() + "\"";
    }
}