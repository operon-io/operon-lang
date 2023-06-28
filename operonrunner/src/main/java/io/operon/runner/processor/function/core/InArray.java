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

package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.processor.function.core.array.ArrayGet;
import io.operon.runner.processor.binary.logical.Eq;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// Tests if the current-value is in the array.
// 
public class InArray extends BaseArity1 implements Node, Arity1, SupportsAttributes {
     // no logger 
    
    public InArray(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "in", "array");
        this.setNs(Namespaces.ARRAY);
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType arrayToTest = (ArrayType) this.getParam1().evaluate();
            
            if (currentValue instanceof EmptyType) {
                TrueType resultTrue = new TrueType(this.getStatement());
                return resultTrue;
            }
            
            if (arrayToTest.getValues().size() == 0) {
                FalseType resultFalse = new FalseType(this.getStatement());
                return resultFalse;
            }

            for (int i = 0; i < arrayToTest.getValues().size(); i ++) {
                //:OFF:log.debug("loop, i == " + i);
                OperonValue valueToTest = ArrayGet.baseGet(this.getStatement(), arrayToTest, i + 1);
                
                OperonValue testValueResult = null;
                
                if (currentValue instanceof FunctionRef) {
                    FunctionRef testFnRef = (FunctionRef) currentValue;
                    
                    testFnRef.getParams().clear();
                    testFnRef.getParams().add(valueToTest);
                    testFnRef.setCurrentValueForFunction(valueToTest); // ops. took out currentValue
                    testValueResult = (OperonValue) testFnRef.invoke();
                }
                
                else if (currentValue instanceof LambdaFunctionRef) {
                    LambdaFunctionRef testLfnRef = (LambdaFunctionRef) currentValue;

                    Map<String, Node> lfrParams = testLfnRef.getParams();
                    
                    // Take the first param to find the param name
                    String paramName = null;
                    for (Map.Entry<String, Node> lfrParam : lfrParams.entrySet()) {
                        paramName = lfrParam.getKey();
                        break;
                    }
                    
                    // Clear previous params that were set
                    testLfnRef.getParams().clear();
                    
                    // Set the new param
                    lfrParams.put(paramName, valueToTest);
                    testLfnRef.setCurrentValueForFunction(valueToTest);
                    testValueResult = (OperonValue) testLfnRef.invoke();
                }

                // Check if values are EQ
                Eq eq = new Eq();
                try {
                    OperonValue eqResult = null;
                    
                    if (testValueResult != null) {
                        //System.out.println(">>> testValueResult :: " + testValueResult + ", valueToTest=" + valueToTest);
                        eqResult = eq.process(this.getStatement(), testValueResult, valueToTest);
                    }
                    else {
                        eqResult = eq.process(this.getStatement(), valueToTest, currentValue);
                    }
                    if (eqResult instanceof TrueType) {
                        TrueType resultTrue = new TrueType(this.getStatement());
                        return resultTrue;
                    }
                } catch (OperonGenericException oge) {
                    // continue.
                }
            }
            
            FalseType resultFalse = new FalseType(this.getStatement());
            return resultFalse;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

}