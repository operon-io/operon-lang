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

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// Tests predicate-expression (given as functionRef) for each value in an array
// 
public class ArrayForAtLeast extends BaseArity2 implements Node, Arity2, SupportsAttributes {
    private static Logger log = LogManager.getLogger(ArrayForAtLeast.class);
    
    public ArrayForAtLeast(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "forAtLeast", "count", "test");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType arrayToTest = (ArrayType) currentValue.evaluate();
            if (arrayToTest.getValues().size() == 0) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Empty array is not supported.");
            }

            // For how many the test-function must apply at least
            int atLeastCount = (int) ((NumberType) this.getParam1().evaluate()).getDoubleValue();
            int counter = 0;

            Path currentPath = this.getStatement().getCurrentPath();
            OperonValue objLink = currentPath.getObjLink();
            if (objLink == null) {
                objLink = arrayToTest;
            }

            Path resetPath = currentPath.copy();
            resetPath.setObjLink(objLink);

            for (int i = 0; i < arrayToTest.getValues().size(); i ++) {
                log.debug("loop, i == " + i);
                
                Path currentPathCopy = currentPath.copy();
                PathPart pp = new PosPathPart(i + 1);
                currentPathCopy.getPathParts().add(pp);
                currentPathCopy.setObjLink(objLink);
                this.getStatement().setCurrentPath(currentPathCopy);
                
                OperonValue valueToTest = ArrayGet.baseGet(this.getStatement(), arrayToTest, i + 1);
                
                Node paramNode = this.getParam2();
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
                    LambdaFunctionRef forallFnRef = (LambdaFunctionRef) forAtLeastFunctionRefNode;
                    forallFnRef.getParams().clear();
                    // NOTE: we cannot guarantee the order of keys that Map.keySet() returns,
                    //       therefore we must assume that the keys are named in certain manner.
                    forallFnRef.getParams().put("$a", valueToTest);
                    forallFnRef.setCurrentValueForFunction(arrayToTest);
                    testValueResult = (OperonValue) forallFnRef.invoke();
                }
                
                else {
                    //Node node = this.getParam2(); // we require unevaluated node
                    //node.getStatement().setCurrentValue(valueToTest);
                    //testValueResult = (OperonValue) node.evaluate();
                    testValueResult = (OperonValue) forAtLeastFunctionRefNode;
                }
                
                if (testValueResult instanceof TrueType) {
                    counter += 1;
                    if (counter >= atLeastCount) {
                        TrueType resultTrue = new TrueType(this.getStatement());
                        this.getStatement().setCurrentPath(resetPath);
                        return resultTrue;
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