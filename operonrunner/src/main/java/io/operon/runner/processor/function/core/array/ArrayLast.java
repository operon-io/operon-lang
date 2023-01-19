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

package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Attributes: parent
//
public class ArrayLast extends BaseArity1 implements Node, Arity1, SupportsAttributes {
    
    public ArrayLast(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "last", "count");
        this.setNs(Namespaces.ARRAY);
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType currentValueArray = (ArrayType) currentValue.evaluate();
            
            int indexCount = 1;
            
            if (this.getParam1() != null) {
                NumberType indexCountNumberType = (NumberType) this.getParam1().evaluate();
                indexCount = (int) indexCountNumberType.getDoubleValue();
            }
            
            int arraySize = currentValueArray.getValues().size();

            OperonValue result = null;
            
            if (arraySize == 0) {
                result = new EmptyType(this.getStatement());
                return result;
            }
            
            // Pull from array
            if (indexCount == 1) {
                result = (OperonValue) ArrayGet.baseGet(this.getStatement(), currentValueArray, arraySize);
            }
            
            // Result is array
            else {
                ArrayType resultArray = new ArrayType(this.getStatement());
                int startIndex = arraySize - indexCount;
                if (startIndex >= 0) {
                    for (int i = startIndex; i < arraySize; i ++) {
                        OperonValue pulledFromArray = (OperonValue) ArrayGet.baseGet(this.getStatement(), currentValueArray, i + 1);
                        resultArray.addValue(pulledFromArray);
                    }
                }
                result = resultArray;
            }
            
            return result;
        } catch (Exception e) {
            System.out.println("ERROR :: " + e.getMessage());
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

}