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

package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NullType;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Cast value to Boolean
 *
 */
public class CastNull extends BaseArity0 implements Node, Arity0 {
    
    public CastNull(Statement statement) {
        super(statement);
        this.setFunctionName("null");
    }

    public NullType evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        
        if (currentValue instanceof NullType) {
            return (NullType) currentValue;
        }
        
        else if (currentValue instanceof StringType) {
            String value = ((StringType) currentValue).getStringValue();
            if (value.toLowerCase().equals("\"null\"")) {
                NullType result = new NullType(this.getStatement());
                return result;
            }
        }
        
        else if (currentValue instanceof NumberType) {
            double value = ((NumberType) currentValue).getDoubleValue();
            if (value == 0) {
                NullType result = new NullType(this.getStatement());
                return result;
            }
        }
        
        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), "Could not cast value to null.");
        return null;
    }

}