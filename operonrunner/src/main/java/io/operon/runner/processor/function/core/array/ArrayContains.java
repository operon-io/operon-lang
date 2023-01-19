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
import io.operon.runner.processor.binary.logical.Eq;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// Tests predicate-expression (given as functionRef) for each value in an array
// This is same as array:forAtLeast(1, $test)
// 
public class ArrayContains extends BaseArity1 implements Node, Arity1, SupportsAttributes {
     // no logger 
    
    public ArrayContains(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "contains", "test");
        this.setNs(Namespaces.ARRAY);
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType arrayToTest = (ArrayType) currentValue.evaluate();
            if (arrayToTest.getValues().size() == 0) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Empty array is not supported.");
            }

            Path currentPath = this.getStatement().getCurrentPath();
            OperonValue objLink = currentPath.getObjLink();
            if (objLink == null) {
                objLink = arrayToTest;
            }

            Path resetPath = currentPath.copy();
            resetPath.setObjLink(objLink);

            for (int i = 0; i < arrayToTest.getValues().size(); i ++) {
                //:OFF:log.debug("loop, i == " + i);
                
                Path currentPathCopy = currentPath.copy();
                PathPart pp = new PosPathPart(i + 1);
                currentPathCopy.getPathParts().add(pp);
                currentPathCopy.setObjLink(objLink);
                this.getStatement().setCurrentPath(currentPathCopy);
                
                OperonValue valueToTest = ArrayGet.baseGet(this.getStatement(), arrayToTest, i + 1);
                
                Node paramNode = this.getParam1();
                paramNode.getStatement().setCurrentValue(valueToTest);
                Node forAtLeastFunctionRefNode = paramNode.evaluate();
                
                OperonValue testValueResult = null;
                
                if (forAtLeastFunctionRefNode instanceof FunctionRef) {
                    FunctionRef testFnRef = (FunctionRef) forAtLeastFunctionRefNode;
                    
                    testFnRef.getParams().clear();
                    testFnRef.getParams().add(valueToTest);
                    testFnRef.setCurrentValueForFunction(arrayToTest); // ops. took out currentValue
                    testValueResult = (OperonValue) testFnRef.invoke();
                }
                
                else if (forAtLeastFunctionRefNode instanceof LambdaFunctionRef) {
                    LambdaFunctionRef testLfnRef = (LambdaFunctionRef) forAtLeastFunctionRefNode;

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
                    testLfnRef.setCurrentValueForFunction(arrayToTest);
                    testValueResult = (OperonValue) testLfnRef.invoke();
                }
                
                else {
                    //Node node = this.getParam2(); // we require unevaluated node
                    //node.getStatement().setCurrentValue(valueToTest);
                    //testValueResult = (OperonValue) node.evaluate();
                    testValueResult = (OperonValue) forAtLeastFunctionRefNode;
                }
                
                if (testValueResult instanceof TrueType) {
                    TrueType resultTrue = new TrueType(this.getStatement());
                    this.getStatement().setCurrentPath(resetPath);
                    return resultTrue;
                }
                else if (testValueResult instanceof FalseType) {
                    continue;
                }
                else {
                    // Check if values are EQ
                    Eq eq = new Eq();
                    try {
                        OperonValue eqResult = eq.process(this.getStatement(), valueToTest, testValueResult);
                        if (eqResult instanceof TrueType) {
                            TrueType resultTrue = new TrueType(this.getStatement());
                            this.getStatement().setCurrentPath(resetPath);
                            return resultTrue;
                        }
                    } catch (OperonGenericException oge) {
                        // continue.
                    }
                }
            }
            
            this.getStatement().setCurrentPath(resetPath);
            FalseType resultFalse = new FalseType(this.getStatement());
            return resultFalse;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

}