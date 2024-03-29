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

public abstract class BaseArity1 extends AbstractNode implements Arity1 {
    @Expose private byte t = IrTypes.FUNCTION_1;
    
    @Expose private Node param1;
    @Expose private String fn;
    @Expose private byte ns;
    @Expose private String param1Name;
    @Expose private boolean param1Optional = false;
    
    public BaseArity1(Statement statement) {
        super(statement);
    }
    
    public void setParam1(Node param1) {this.param1 = param1;}
    public Node getParam1() throws OperonGenericException {
        if (this.param1Optional == false && this.param1 == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "ARITY1_RESOLVE_PARAM_1", "Cannot resolve param $" + param1Name);
        }
        return this.param1;
    }
    public void setFunctionName(String fn) {this.fn = fn;}
    public void setParam1Name(String p1n) {this.param1Name = p1n;}
    public String getFunctionName() {return this.fn;}
    public String getParam1Name() {return this.param1Name;}
    
    public void setNs(byte fns) {this.ns = fns;}
    public byte getNs() {return this.ns;}
    
    //
    // optionality must be set before setting params
    //
    public void setParam1AsOptional(boolean opt) {this.param1Optional = opt;}
    public boolean isParam1Optional() {return this.param1Optional;}

    public void setParams(List<Node> args, String funcName, String p1) throws OperonGenericException {
        this.setFunctionName(funcName);
        this.setParam1Name(p1);
        
        if (args.size() < 1 && this.isParam1Optional() == false) {
            //System.out.println("Param1 optional :: " + this.isParam1Optional());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Insufficient amount of arguments. Expected 1, got 0");
        }
        //
        // Skip setting params
        //
        else if (args.size() < 1 && this.isParam1Optional() == true) {
            return;
        }
        
        // IMPORTANT: do not _evaluate_ args here (for any function)!
        //            Reason: param could be e.g. ValueRef, and in that case
        //            there's no value registered in the runtime-values.
        //            Check this pattern!
        if (args.get(0) instanceof FunctionRegularArgument) {
            // Note that params are set in reverse order:
            this.setParam1( ((FunctionRegularArgument) args.get(0)).getArgument() );
        }
        else if (args.get(0) instanceof FunctionNamedArgument) {
            // Note that params are set in reverse order:
            FunctionNamedArgument fna1 = (FunctionNamedArgument) args.get(0);
            if (fna1.getArgumentName().equals("$" + this.getParam1Name())) {
                this.setParam1(fna1.getArgumentValue());
            }
            else {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Unknown parameter: " + fna1.getArgumentName());
            }
       }
       else {
           this.setParam1(args.get(0));
       }
    }
    
}