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

package io.operon.runner.processor.function;

import java.util.List;
import java.util.Collections;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionNamedArgument;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

import io.operon.runner.IrTypes;
import com.google.gson.annotations.Expose;

public abstract class BaseArity2 extends AbstractNode implements Arity2 {
    @Expose private byte t = IrTypes.FUNCTION_2;
    
    @Expose private Node param1;
    @Expose private Node param2;
    @Expose private boolean param2Optional = false;
    @Expose private String fn;
    @Expose private byte ns;
    @Expose private String param1Name;
    @Expose private String param2Name;
    
    public BaseArity2(Statement statement) {
        super(statement);
    }
    
    public void setParam1(Node param1) {this.param1 = param1;}
    public void setParam2(Node param2) {this.param2 = param2;}
    public Node getParam1() throws OperonGenericException {
        if (this.param1 == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "ARITY2_RESOLVE_PARAM_1", "Cannot resolve param $" + param1Name);
        }
        return this.param1;
    }
    public Node getParam2() throws OperonGenericException {
        if (this.param2Optional == false && this.param2 == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "ARITY2_RESOLVE_PARAM_2", "Cannot resolve param $" + param2Name);
        }
        return this.param2;
    }
    public void setFunctionName(String fn) {this.fn = fn;}
    public void setParam1Name(String p1n) {this.param1Name = p1n;}
    public void setParam2Name(String p2n) {this.param2Name = p2n;}
    public String getFunctionName() {return this.fn;}
    public String getParam1Name() {return this.param1Name;}
    public String getParam2Name() {return this.param2Name;}
    public void setParam2AsOptional(boolean opt) {this.param2Optional = opt;}
    public boolean isParam2Optional() {return this.param2Optional;}

    public void setNs(byte fns) {this.ns = fns;}
    public byte getNs() {return this.ns;}

    public void setParams(List<Node> args, String funcName, String p1, String p2) throws OperonGenericException {
        this.setFunctionName(funcName);
        this.setParam1Name(p1);
        this.setParam2Name(p2);
        
        if (args.size() < 2 && this.isParam2Optional() == false) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Insufficient amount of arguments. Expected 2, got " + args.size());
        }
        
        Collections.reverse(args);
        
        // IMPORTANT: do not _evaluate_ args here (for any function)!
        //            Reason: param could be e.g. ValueRef, and in that case
        //            there's no value registered in the runtime-values.
        //            Check this pattern!
        if (args.get(0) instanceof FunctionRegularArgument) {
            if (args.get(0) != null) {
                this.setParam1( ((FunctionRegularArgument) args.get(0)).getArgument() );
            }
            else {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Param1 required: " + this.getParam1Name());
            }
            if (args.size() == 2) {
                if (args.get(1) instanceof FunctionRegularArgument && ((FunctionRegularArgument) args.get(1)).getArgument() != null) {
                    this.setParam2( ((FunctionRegularArgument) args.get(1)).getArgument() );
                }
                else if (args.get(1) instanceof FunctionNamedArgument) {
                    FunctionNamedArgument fna = (FunctionNamedArgument) args.get(1);
                    this.setParam2(fna.getArgumentValue());
                }
            }
        }
        else if (args.get(0) instanceof FunctionNamedArgument) {
            FunctionNamedArgument fna1 = (FunctionNamedArgument) args.get(0);
            FunctionNamedArgument fna2 = null; // we don't know yet if param2 is fna or fra
            FunctionRegularArgument fra2 = null; // so we setup both with null
            if (args.size() == 2) {
                if (args.get(1) instanceof FunctionNamedArgument) {
                    fna2 = (FunctionNamedArgument) args.get(1);
                }
                else if (args.get(1) instanceof FunctionRegularArgument) {
                    fra2 = (FunctionRegularArgument) args.get(1);
                }
            }

            if (fna1.getArgumentName().equals("$" + this.getParam1Name())) {
                if (fna1 != null) {
                    this.setParam1(fna1.getArgumentValue());
                }
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Param1 required: " + this.getParam1Name());
                }

                if (fna2 != null && fna2.getArgumentName().equals("$" + this.getParam2Name())) {
                    this.setParam2(fna2.getArgumentValue());
                }
                
                else if (fra2 != null) {
                    this.setParam2(fra2.getArgument());
                }
                
                else if (this.isParam2Optional() == false) {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Unknown parameter: " + fna2.getArgumentName());
                }
            }
            
            else if (fna1.getArgumentName().equals("$" + this.getParam2Name())) {
                if (fna1 != null) {
                    this.setParam2(fna1.getArgumentValue());
                }
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Param1 required: " + this.getParam1Name());
                }
                
                if (fna2 != null && fna2.getArgumentName().equals("$" + this.getParam1Name())) {
                    this.setParam1(fna2.getArgumentValue());
                }
                
                else if (fra2 != null) {
                    this.setParam1(fra2.getArgument());
                }
                
                else {
                    // do nothing
                }
            }
            
            else {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Unknown parameter: " + fna1.getArgumentName());
            }
        }
        else {
            this.setParam1(args.get(0));
            if (args.get(1) != null) {
                this.setParam2(args.get(1)); // TODO: resolve param2 -type
            }
        }
    }

}