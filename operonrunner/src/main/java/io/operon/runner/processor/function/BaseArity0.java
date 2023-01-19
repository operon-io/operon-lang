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

public abstract class BaseArity0 extends AbstractNode implements Arity0 {
    @Expose private byte t = IrTypes.FUNCTION_0;
    
    @Expose private String fn;
    @Expose private byte ns;
    
    public BaseArity0(Statement statement) {
        super(statement);
    }
    
    public void setFunctionName(String fn) {this.fn = fn;}
    public String getFunctionName() {return this.fn;}
    
    public void setNs(byte fns) {this.ns = fns;}
    public byte getNs() {return this.ns;}
   
}