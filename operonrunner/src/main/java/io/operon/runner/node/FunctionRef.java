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

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.function.core.resolver.CoreFunctionResolver;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.Arity3;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.node.FunctionStatementParam;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// FunctionRef is used to reference function e.g. in the assignments.
//  functionName()
//
public class FunctionRef extends OperonValue /*AbstractNode*/ implements Node {
     // no logger 
    private String functionName; // name without namespace
    private String functionFQName;

    private FunctionStatement functionStatement; // function execution context
    private Node coreFunction; // core-function
    private List<Node> coreFunctionArgs; // this contains the args that are set in the setArgument for the coreFunction
    // 
    // NOTE: params are either:
    //       FunctionNamedArgument, FunctionRegularArgument, FunctionRefArgumentPlaceholder
    //
    // NOTE: These params are populated with the actual values,
    //       and set as the arguments for the function to be called.
    //
    private List<Node> params;
    private OperonValue currentValueForFunction;
    
    public FunctionRef(Statement stmnt) {
        super(stmnt);
        this.params = new ArrayList<Node>();
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER FunctionRef.evaluate() :: " + this.getFunctionFQName());
        this.getStatement().setCurrentValue(this);
        this.setUnboxed(false);
        return this;
    }
    
    public OperonValue invoke() throws OperonGenericException {
        //:OFF:log.debug("ENTER FunctionRef.invoke() :: " + this.getFunctionFQName());
        //System.out.println("FunctionRef invoke");
        //System.out.println("  >> RuntimeValues=" + this.getStatement().getRuntimeValues() + ", STMT=" + this.getStatement().getId());
        ////:OFF:log.debug(" REMOVE :: currentValue :: " + this.getCurrentValueForFunction());
        
        //
        // User-defined function:
        //
        if (this.getCoreFunction() == null) {
            FunctionCall fnCall = new FunctionCall(this.getStatement(), this.getFunctionFQName());
            if (this.getFunctionStatement() == null) {
                //
                // Fetch by functionFQ-name
                //
                FunctionStatement fnStatement = FunctionRef.resolveUserFunction(this.getStatement(), this.getFunctionFQName() /*, this.getStatement().isPrototype()*/);
                
                if (fnStatement == null) {
                    //System.err.println("FunctionRef :: fnStatement was null");
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_REF", "SIGNATURE_MISMATCH", this.getFunctionFQName());
                }
                this.setFunctionStatement(fnStatement);
                
                //
                // Link the runtimeValues that were assigned for the statement associated with this FunctionRef.
                // E.g. ObjectAccess assigns "_" and "_$" which must be linked here.
                //
                fnStatement.getRuntimeValues().putAll(this.getStatement().getRuntimeValues());
                fnCall.setFunctionStatement(fnStatement);
            }
            
            else {
                FunctionStatement fnStatement = this.getFunctionStatement();
                fnStatement.getRuntimeValues().putAll(this.getStatement().getRuntimeValues());
                fnCall.setFunctionStatement(fnStatement);
            }
            
            //System.out.println("Invoke. CV for function=" + this.getCurrentValueForFunction());
            
            //
            // FIXME: setCurrentValue did not have effect here, set for Node?
            //
            //fnCall.getFunctionStatement().setCurrentValue(this.getCurrentValueForFunction());
            
            // TODO: check if this would not be needed. Added for FunctionRefTests#functionRefArgsTest
            if (this.getCurrentValueForFunction() != null) {
                fnCall.getStatement().setCurrentValue(this.getCurrentValueForFunction());
            }
            
            fnCall.setFunctionFQName(this.getFunctionFQName());
            
            fnCall.getArguments().addAll(this.getParams());
            OperonValue result = fnCall.evaluate();
            result.setDoBindings(this.getDoBindings());
            result.getBindings().putAll(this.getBindings());
            return result;
        }
        
        //
        // Core-function
        //
        else {
            //
            // TODO: set the arguments!
            //
            this.getStatement().setCurrentValue(this.getCurrentValueForFunction());
            return (OperonValue) this.getCoreFunction().evaluate();
        }
    }
    
    //
    // @param prototype: true: creates a copy of the resolved function-statement
    //
    public static FunctionStatement resolveUserFunction(Statement stmt, String functionFQName/*, Boolean prototype*/) throws OperonGenericException {
        FunctionStatement fnStatement = stmt.getOperonContext()
                            .getFunctionStatements().get(functionFQName);
        if (fnStatement != null) {
            return fnStatement;
        }
        //
        // Try to load function from Module
        //
        //:OFF:log.debug("Try load function from module :: " + functionFQName);
        //:OFF:log.debug("Modules :: " + stmt.getOperonContext().getModules());
        java.util.Map<String, Context> modules = stmt.getOperonContext().getModules();
        String [] namespaces = functionFQName.split(":");
        if (namespaces.length > 0) {
            String ns1 = namespaces[0];
            //System.out.println("NS1 :: " + ns1);
            Context module = modules.get(ns1);
            if (module != null) {
                //:OFF:log.debug("Function found from module");
                //:OFF:log.debug("function-stmts :: " + module.getFunctionStatements());
                String ns2 = functionFQName.substring(ns1.length(), functionFQName.length());
                //:OFF:log.debug("Getting function-stmt :: " + ns2);
                // Remove the ":" prefix only if there are 3 or more ":"
                // Therefore case like ":local:foo:0" is not valid, it should be "local:foo:0"
                char nsSeparator = ':';
                int nsSeparatorCount = 0;
                  
                for (int i = 0; i < ns2.length(); i ++) {
                    if (ns2.charAt(i) == nsSeparator) {
                        nsSeparatorCount ++;
                    }
                }
                if (nsSeparatorCount >= 3) {
                    ns2 = ns2.substring(1, ns2.length());
                }
                fnStatement = module.getFunctionStatements().get(ns2);
            }
            else {
                ErrorUtil.createErrorValueAndThrow(stmt, "FUNCTION_REF", "ERROR", "Could not find function " + functionFQName);
            }
        }
        return fnStatement;
    }
    
    public void setFunctionName(String fn) {
        this.functionName = fn;
    }
    
    public String getFunctionName() {
        return this.functionName;
    }
    
    public void setFunctionFQName(String fqn) {
        this.functionFQName = fqn;
    }
    
    public String getFunctionFQName() {
        return this.functionFQName;
    }

    public void setFunctionStatement(FunctionStatement funcStmnt) {
        this.functionStatement = funcStmnt;
    }

    public FunctionStatement getFunctionStatement() {
        return this.functionStatement;
    }

    public List<Node> getParams() {
        return this.params;
    }

    public void setArgument(Node arg) throws OperonGenericException {
        //:OFF:log.debug("SetArgument :: " + this.getParams());
        //System.out.println("SetArgument :: " + this.getParams());
        
        FunctionStatement fnStatement = null;
        boolean isCoreFunction = CoreFunctionResolver.isCoreFunction(this.getFunctionFQName());
        
        if (isCoreFunction == false) {
            if (this.getFunctionStatement() == null) {
                fnStatement = this.resolveUserFunction(this.getStatement(), this.getFunctionFQName());
            }
            else {
                fnStatement = this.getFunctionStatement();
            }
            
            this.matchUserFunctionArguments(fnStatement, arg);
        }
        else {
            if (this.getFunctionStatement() == null) {
                fnStatement = new FunctionStatement(this.getStatement().getOperonContext());
            }
            else {
                fnStatement = this.getFunctionStatement();
            }
            Node cf = this.getCoreFunction();
            
            if (cf instanceof Arity0) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_REF", "ERROR", "Function " + this.getFunctionFQName() + " does not take arguments.");
                return;
            }
            else if (cf instanceof Arity1) {
                Arity1 a1 = (Arity1) cf;
                String a1Param1Name = a1.getParam1Name();
                String a1FunctionName = a1.getFunctionName();

                if (this.coreFunctionArgs == null) {
                    this.coreFunctionArgs = new ArrayList<Node>();
                }

                if (this.coreFunctionArgs.size() < 1) {
                    coreFunctionArgs.add(arg);
                }
                if (this.coreFunctionArgs.size() == 1) {
                    Collections.reverse(coreFunctionArgs);
                    a1.setParams(coreFunctionArgs, a1FunctionName, a1Param1Name);
                }
                return;
            }
            else if (cf instanceof Arity2) {
                //System.out.println(">> arity2");
                Arity2 a2 = (Arity2) cf;
                String a2Param1Name = a2.getParam1Name();
                String a2Param2Name = a2.getParam2Name();
                String a2FunctionName = a2.getFunctionName();
                
                //System.out.println("  - function-name: " + a2FunctionName);
                //System.out.println("  - param1: " + a2Param1Name);
                //System.out.println("  - param2: " + a2Param2Name);
                
                //
                // This could be used to match the arg with either param1 or param2:
                //
                FunctionStatementParam param = new FunctionStatementParam(fnStatement);
                param.setParam("todo"); // TODO: match the arg with correct param name?
                
                if (this.coreFunctionArgs == null) {
                    this.coreFunctionArgs = new ArrayList<Node>();
                }

                if (this.coreFunctionArgs.size() < 2) {
                    //System.out.println("  - adding arg");
                    coreFunctionArgs.add(arg);
                }
                if (this.coreFunctionArgs.size() == 2) {
                    //System.out.println("  - setting args");
                    Collections.reverse(coreFunctionArgs);
                    a2.setParams(coreFunctionArgs, a2FunctionName, a2Param1Name, a2Param2Name);
                    //System.out.println("  - setting args: OK");
                }
                return;
            }
            else if (cf instanceof Arity3) {
                //
                // TODO: Arity3 lacks BaseArity3, which would have the method setParams(...),
                //       which would sort the params into correct order (named / regular).
                //
                //System.out.println(">> arity3");
                Arity3 a3 = (Arity3) cf;
                if (a3.getParam3() == null) {
                    a3.setParam3(arg);
                }
                else if (a3.getParam2() == null) {
                    a3.setParam2(arg);
                }
                else if (a3.getParam1() == null) {
                    a3.setParam1(arg);
                }
                return;
            }
            
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_REF", "ERROR", "Cannot use FunctionRef for the core-function. Please wrap it inside user-defined function.");
        }
        return;
    }

    private void matchUserFunctionArguments(FunctionStatement fnStatement, Node arg) {
        //
        // Match the Function's arguments:
        //
        List<FunctionStatementParam> functionStatementParamsList = fnStatement.getParams();
        
        for (int i = 0; i < this.getParams().size(); i ++) {
            if (this.getParams().get(i) instanceof FunctionRefArgumentPlaceholder) {
                //:OFF:log.debug(">>Replace FunctionRefArgumentPlaceholder");
                //System.out.println("    >>Replace FunctionRefArgumentPlaceholder");
                
                String matchingFunctionStatementParam = functionStatementParamsList.get(i).getParam();
                //:OFF:log.debug(">> Matching argument name :: " + matchingFunctionStatementParam);
                FunctionNamedArgument functionNamedArgument = new FunctionNamedArgument(this.getStatement());
                functionNamedArgument.setArgumentName(matchingFunctionStatementParam);
                if (arg instanceof FunctionRegularArgument) {
                    arg = ((FunctionRegularArgument) arg).getArgument();
                }
                functionNamedArgument.setArgumentValue(arg);
                //:OFF:log.debug("  >> do bindings :: " + arg.getDoBindings());
                //:OFF:log.debug("  ARG debug :: " + arg.getClass().getName()); // afraid, that this is still ValueRef
                this.getParams().set(i, functionNamedArgument);
                return;
            }
            
            else if (this.getParams().get(i) instanceof FunctionRefNamedArgument) {
                //System.out.println("    >>FunctionRefNamedArgument");
                FunctionRefNamedArgument frna = (FunctionRefNamedArgument) this.getParams().get(i);
                
                //
                // syntax:
                //   Function foo($a: ?): $a End:Function
                //     --> '?' is the placeholder
                //
                boolean hasPlaceholder = frna.getHasPlaceholder();
                if (hasPlaceholder) {
                    //String matchingFunctionStatementParam = functionStatementParamsList.get(i).getArgument();
                    ////:OFF:log.debug(">> Matching argument name :: " + matchingFunctionStatementParam);
                    //System.out.println(">> Matching argument name: " + frna.getArgumentName());
                    FunctionNamedArgument functionNamedArgument = new FunctionNamedArgument(this.getStatement());
                    functionNamedArgument.setArgumentName(frna.getArgumentName());
                    if (arg instanceof FunctionRegularArgument) {
                        //System.out.println(" >>>>> ARG WAS FunctionRegularArgument");
                        arg = ((FunctionRegularArgument) arg).getArgument(); // This gets the value.
                        functionNamedArgument.setArgumentValue(arg);
                    }
                    else if (arg instanceof FunctionNamedArgument) {
                        //System.out.println(" >>>>> ARG WAS FunctionNamedArgument");
                        if ( ((FunctionNamedArgument) arg).getArgumentName().equals(frna.getArgumentName())) {
                            functionNamedArgument.setArgumentValue(arg); // CHECK: is this necessary to set the arg as such, or could we get the value only?
                        }
                        else {
                            continue;
                        }
                    }
                    else {
                        // arg can be e.g. a "StringType"
                        //System.out.println(" >>>>> ARG WAS :: " + arg.getClass().getName());
                        functionNamedArgument.setArgumentValue(arg);
                    }
                    
                    //:OFF:log.debug("  >> do bindings :: " + arg.getDoBindings());
                    //:OFF:log.debug("  ARG debug :: " + arg.getClass().getName()); // afraid, that this is still ValueRef
                    this.getParams().set(i, functionNamedArgument);
                    return;
                }
                else {
                    //System.out.println("    >> No placeholder found");
                }
            }
        }
        return;
    }

    protected static OperonValue setParamsAndInvokeFunctionRef(Node ref, OperonValue currentValueCopy, FunctionArguments fArgs) throws OperonGenericException {
        FunctionRef fr = (FunctionRef) ref;
        //System.out.println("setParamsAndInvokeFunctionRef. CV=" + currentValueCopy);
        // Set the current value for FunctionRef:
        fr.setCurrentValueForFunction(currentValueCopy);
        
        if (fArgs != null) {
            //:OFF:log.debug("  FunctionRefInvoke :: setting the function arguments. Amount :: " + fArgs.getArguments().size());
            //System.out.println("  FunctionRefInvoke :: setting the function arguments. Amount :: " + fArgs.getArguments().size());
            //
            // Set the function arguments before invoking it
            // First find the FunctionNamedArguments and set them.
            // After this, set the FunctionRegularArguments / other.
            // 
            for (int i = 0; i < fArgs.getArguments().size(); i ++) {
                //System.out.println("  Setting arg: " + ((FunctionNamedArgument) fArgs.getArguments().get(i)).getArgumentName()  );
                Node arg = fArgs.getArguments().get(i);
                if (arg instanceof FunctionNamedArgument) {
                    fr.setArgument(fArgs.getArguments().get(i));
                }
            }
            
            for (int i = 0; i < fArgs.getArguments().size(); i ++) {
                //System.out.println("  Setting arg: " + ((FunctionNamedArgument) fArgs.getArguments().get(i)).getArgumentName()  );
                Node arg = fArgs.getArguments().get(i);
                if (arg instanceof FunctionNamedArgument == false) {
                    fr.setArgument(fArgs.getArguments().get(i));
                }
            }
            
            // Check that all placeholders were set:
            for (int i = 0; i < fr.getParams().size() ; i ++) {
                if (fr.getParams().get(i) instanceof FunctionRefArgumentPlaceholder) {
                    ErrorUtil.createErrorValueAndThrow(currentValueCopy.getStatement(), "FUNCTION_REF", "INVOKE", "Cannot resolve placeholder: not enough arguments supplied.");
                }
            }
        }
        //:OFF:log.debug("  FunctionRefInvoke :: invoking function");
        OperonValue result = fr.invoke();
        return result;
    }
 
    public void setCoreFunction(Node coreFn) {
        this.coreFunction = coreFn;
    }
    
    public Node getCoreFunction() {
        return this.coreFunction;
    }
    
    public void setCurrentValueForFunction(OperonValue cv) {
        this.currentValueForFunction = cv;
    }
    
    public OperonValue getCurrentValueForFunction() {
        return this.currentValueForFunction;
    }
    
    //
    // creates a deep-copy
    //
    public FunctionRef deepCopy(Statement stmt) throws OperonGenericException {
        FunctionRef frCopy = new FunctionRef(stmt);
        //frCopy.setFunctionStatement(fnStmt);
        if (this.getCoreFunction() != null) {
            int indexEnd = this.getFunctionFQName().lastIndexOf(':');
            String functionNamespace = this.getFunctionFQName().substring(0, indexEnd);
            indexEnd = functionNamespace.lastIndexOf(':');
            functionNamespace = this.getFunctionFQName().substring(0, indexEnd);
            //System.out.println("functionFQName: " + this.getFunctionFQName() + ", functionNamespace=[" + functionNamespace + "]");
            Node coreFunctionCopy = CoreFunctionResolver.getCoreFunction(functionNamespace, this.getFunctionName(), this.getParams(), stmt);
            frCopy.setCoreFunction(coreFunctionCopy);
        }
        frCopy.setFunctionFQName(this.getFunctionFQName());
        frCopy.setFunctionName(this.getFunctionName());
        return frCopy;
    }
    
    public String toString() {
        return "\"function:" + this.getFunctionFQName() + "\"";
    }
}