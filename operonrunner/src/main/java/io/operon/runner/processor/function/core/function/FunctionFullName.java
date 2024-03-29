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

package io.operon.runner.processor.function.core.function;

import io.operon.runner.OperonContext;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Get function name, without namespace.
 *
 */
public class FunctionFullName extends BaseArity0 implements Node, Arity0 {
    
    public FunctionFullName(Statement statement) {
        super(statement);
        this.setFunctionName("fullName");
        this.setNs(Namespaces.FUNCTION);
    }

    public StringType evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        StringType result = new StringType(this.getStatement());
        
        if (currentValue instanceof FunctionRef) {
            FunctionRef fRef = (FunctionRef) currentValue;
            String functionFQName = fRef.getFunctionFQName();
            int lastIndex = functionFQName.lastIndexOf(":");
            result.setFromJavaString(functionFQName.substring(0, lastIndex));
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "function:" + this.getFunctionName(), "Expected FunctionRef, but got: " + currentValue.getClass().getName());
        }
        
        return result;
    }

}