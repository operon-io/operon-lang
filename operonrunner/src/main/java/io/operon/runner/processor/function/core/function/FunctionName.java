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
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Get function name, without namespace.
 *
 */
public class FunctionName extends BaseArity0 implements Node, Arity0 {
    
    public FunctionName(Statement statement) {
        super(statement);
        this.setFunctionName("name");
    }

    public StringType evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        StringType result = new StringType(this.getStatement());
        
        if (currentValue instanceof FunctionRef) {
            FunctionRef fRef = (FunctionRef) currentValue;
            String functionName = fRef.getFunctionName();
            result.setFromJavaString(functionName);
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "function:" + this.getFunctionName(), "Expected FunctionRef, but got: " + currentValue.getClass().getName());
            return null;
        }
        
        return result;
    }

    // This returns the function-name from fully-qualified name:
    public static String getName(String functionFQName) {
        int endIndex = functionFQName.lastIndexOf(':');
        int startIndex = functionFQName.substring(0, endIndex).lastIndexOf(':');
        String functionName = null;
        if (functionFQName.charAt(startIndex) == ':') {
            functionName = functionFQName.substring(startIndex + 1, endIndex);
        }
        else {
            functionName = functionFQName.substring(startIndex, endIndex);
        }
        return functionName;
    }

}