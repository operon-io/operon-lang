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
import io.operon.runner.model.path.*;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// Tests predicate-expression (given as functionRef) for each value in an array
// 
public class ArraySearch extends BaseArity1 implements Node, Arity1, SupportsAttributes {
     // no logger 
    
    public ArraySearch(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "search", "test");
    }

    private void setCurrentPathWithPos(Statement stmt, int pos, OperonValue objLink) {
        Path newPath = new Path(stmt);
        PathPart pp = new PosPathPart(pos);
        newPath.getPathParts().add(pp);
        newPath.setObjLink(objLink);
        stmt.setCurrentPath(newPath);
    }

    public ArrayType evaluate() throws OperonGenericException {
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ArrayType arrayToTest = (ArrayType) currentValue.evaluate();
        ArrayType result = new ArrayType(this.getStatement());
        
        Path currentPath = this.getStatement().getCurrentPath();
        OperonValue objLink = currentPath.getObjLink();
        
        Path resetPath = currentPath.copy();
        resetPath.setObjLink(objLink);
        
        try {
            for (int i = 0; i < arrayToTest.getValues().size(); i ++) {
                //:OFF:log.debug("loop, i == " + i);
                OperonValue valueToTest = ArrayGet.baseGet(this.getStatement(), arrayToTest, i + 1);
                
                this.setCurrentPathWithPos(this.getStatement(), i + 1, arrayToTest);
                
                Node paramNode = this.getParam1();
                
                paramNode.getStatement().setCurrentValue(valueToTest);
                Node testFunctionRefNode = paramNode.evaluate();
                
                OperonValue testValueResult = null;
                
                if (testFunctionRefNode instanceof FunctionRef) {
                    FunctionRef testFnRef = (FunctionRef) testFunctionRefNode;
                    
                    testFnRef.getParams().clear();
                    testFnRef.getParams().add(valueToTest);
                    testFnRef.setCurrentValueForFunction(valueToTest); // ops. took out currentValue
                    testValueResult = testFnRef.invoke();
                }
                
                else if (testFunctionRefNode instanceof LambdaFunctionRef) {
                    LambdaFunctionRef testFnRef = (LambdaFunctionRef) testFunctionRefNode;
                    
                    Map<String, Node> lfrParams = testFnRef.getParams();
                    
                    // Take the first param to find the param name
                    String paramName = null;
                    for (Map.Entry<String, Node> lfrParam : lfrParams.entrySet()) {
                        paramName = lfrParam.getKey();
                        break;
                    }
                    
                    testFnRef.getParams().clear();
                    testFnRef.getParams().put(paramName, valueToTest);
                    testFnRef.setCurrentValueForFunction(valueToTest);
                    testValueResult = testFnRef.invoke();
                }
                
                else {
                    // This was already evaluated
                    testValueResult = (OperonValue) testFunctionRefNode;
                }
                
                if (testValueResult instanceof TrueType) {
                    NumberType index = new NumberType(this.getStatement());
                    index.setDoubleValue((double) (i + 1));
                    index.setPrecision((byte) 0);
                    result.getValues().add(index);
                }
                else if (testValueResult instanceof FalseType) {
                    continue;
                }
                else {
                    // Directly test if the value matches
                    if (JsonUtil.isIdentical(valueToTest, testValueResult)) {
                        NumberType index = new NumberType(this.getStatement());
                        index.setDoubleValue((double) (i + 1));
                        index.setPrecision((byte) 0);
                        result.getValues().add(index);
                    }
                }
            }
            
            this.getStatement().setCurrentPath(resetPath);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}