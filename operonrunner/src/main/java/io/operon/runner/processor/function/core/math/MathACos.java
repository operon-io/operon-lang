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

package io.operon.runner.processor.function.core.math;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.Random;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class MathACos extends BaseArity0 implements Node, Arity0 {

    public MathACos(Statement statement) {
        super(statement);
        this.setFunctionName("arccos");
    }

    public NumberType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            if (currentValue instanceof NumberType == false) {
                currentValue = (OperonValue) currentValue.evaluate();
            }
            double acosValue = Math.acos( ((NumberType) currentValue).getDoubleValue() );
            NumberType result = new NumberType(this.getStatement());
            
            // Begin Set precision
            byte precision = ((NumberType) currentValue).getPrecision();
            if (precision == -1) {
                precision = NumberType.getPrecisionFromStr(String.valueOf( ((NumberType) currentValue).getDoubleValue() ));
            }
            result.setPrecision(precision);
            // End Set precision
            
            result.setDoubleValue(acosValue);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "math:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}