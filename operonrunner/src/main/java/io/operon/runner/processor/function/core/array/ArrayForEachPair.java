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
public class ArrayForEachPair extends BaseArity2 implements Node, Arity2, SupportsAttributes {
     // no logger 
    
    public ArrayForEachPair(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "forEachPair", "array", "expr");
    }

    public ArrayType evaluate() throws OperonGenericException {
        //System.out.println("ArrayForEachPair :: evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ArrayType arrayA = (ArrayType) currentValue.evaluate();
        if (arrayA.getValues().size() == 0) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Empty array is not supported.");
            return null;
        }

        OperonValue objLink = this.getStatement().getCurrentPath().getObjLink();
        
        ArrayType arrayB = (ArrayType) this.getParam1().evaluate();
        
        if (arrayA.getValues().size() != arrayB.getValues().size()) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Both arrays must have same amount of members.");
            return null;
        }
        
        try {
            ArrayType result = new ArrayType(this.getStatement());
            OperonValue evaluatedNode = null;
            Node node = this.getParam2(); // FIXME: we don't address the param2 by name, it is still the first that was set!
            
            OperonValue valueA = ArrayGet.baseGet(this.getStatement(), arrayA, 1);
            OperonValue valueB = ArrayGet.baseGet(this.getStatement(), arrayB, 1);
            
            node.getStatement().setCurrentValue(valueA);
            node.getStatement().getRuntimeValues().put("$a", valueA);
            node.getStatement().getRuntimeValues().put("$b", valueB);
            
            //
            // At this point the currentPath must be '[1]'
            //
            this.setCurrentPathWithPos(this.getStatement(), 1, arrayA);
            evaluatedNode = node.evaluate();
            
            if (evaluatedNode instanceof FunctionRef) {
                for (int i = 0; i < arrayA.getValues().size(); i ++) {
                    //:OFF:log.debug("loop, i == " + i);
                    valueA = ArrayGet.baseGet(this.getStatement(), arrayA, i + 1);
                    valueB = ArrayGet.baseGet(this.getStatement(), arrayB, i + 1);
                    
                    this.setCurrentPathWithPos(this.getStatement(), i + 1, arrayA);
                    
                    FunctionRef foreachFnRef = (FunctionRef) evaluatedNode;
                    foreachFnRef.getParams().clear();
                    foreachFnRef.getParams().add(valueA);
                    foreachFnRef.getParams().add(valueB);
                    foreachFnRef.setCurrentValueForFunction(arrayA); // ops. took out currentValue
                    OperonValue transformValueResult = foreachFnRef.invoke();
                    result.getValues().add(transformValueResult);
                }
            }
            else if (evaluatedNode instanceof LambdaFunctionRef) {
                for (int i = 0; i < arrayA.getValues().size(); i ++) {
                    //:OFF:log.debug("loop, i == " + i);
                    valueA = ArrayGet.baseGet(this.getStatement(), arrayA, i + 1);
                    valueB = ArrayGet.baseGet(this.getStatement(), arrayB, i + 1);
                    
                    this.setCurrentPathWithPos(this.getStatement(), i + 1, arrayA);
                    
                    LambdaFunctionRef foreachLfnRef = (LambdaFunctionRef) evaluatedNode;
                    
                    Map<String, Node> lfrParams = foreachLfnRef.getParams();
                    
                    // Find the param names
                    String param1Name = null;
                    String param2Name = null;
                    short counter = 0;
                    for (String lfrParamKey : lfrParams.keySet()) {
                        if (counter == 0) {
                            param1Name = lfrParamKey;
                        }
                        else if (counter == 1) {
                            param2Name = lfrParamKey;
                        }
                        else {
                            break;
                        }
                        counter += 1;
                    }
                    
                    foreachLfnRef.getParams().clear();
                    foreachLfnRef.getParams().put(param1Name, valueA);
                    foreachLfnRef.getParams().put(param2Name, valueB);
                    foreachLfnRef.setCurrentValueForFunction(arrayA); // ops. took out currentValue
                    OperonValue transformValueResult = foreachLfnRef.invoke();
                    result.getValues().add(transformValueResult);
                }
            }
            else {
                // Pure expression.
                // NOTE: requires params to be named as $a and $b.
                //
                // Observe: the first item was already evaluated above, so we add it into results
                //          here and we continue evaluating from the _second_ value.
                //          This is because we don't know when evaluating the first time what
                //          is the expr-node type (FunctionRef, FunctionLambdaRef, or pure expression).
                result.getValues().add(evaluatedNode);
                for (int i = 1; i < arrayA.getValues().size(); i ++) {
                    Node n = this.getParam2();
                    //
                    // NOTE: baseGet modifies the currentPath from the statement,
                    //       therefore the currentPath must be given only after
                    //       these Gets.
                    valueA = ArrayGet.baseGet(this.getStatement(), arrayA, i + 1);
                    valueB = ArrayGet.baseGet(this.getStatement(), arrayB, i + 1);
                    
                    this.setCurrentPathWithPos(this.getStatement(), i + 1, arrayA);
                    
                    n.getStatement().setCurrentValue(valueA);
                    node.getStatement().getRuntimeValues().put("$a", valueA);
                    node.getStatement().getRuntimeValues().put("$b", valueB);
                    evaluatedNode = n.evaluate();
                    result.getValues().add(evaluatedNode);
                }
            }
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    private void setCurrentPathWithPos(Statement stmt, int pos, OperonValue objLink) {
        //
        // ATTRIBUTES
        //
        Path newPath = new Path(stmt);
        PathPart pp = new PosPathPart(pos);
        newPath.getPathParts().add(pp);
        newPath.setObjLink(objLink);
        stmt.setCurrentPath(newPath);
    }

}