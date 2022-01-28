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
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// 
// 
public class ArrayForEach extends BaseArity1 implements Node, Arity1, SupportsAttributes {
    private static Logger log = LogManager.getLogger(ArrayForEach.class);
    
    public ArrayForEach(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "forEach", "expr");
    }

    public ArrayType evaluate() throws OperonGenericException {
        log.debug("ForEach :: evaluate()");
        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ArrayType arrayToLoop = (ArrayType) currentValue.evaluate();
        if (arrayToLoop.getValues().size() == 0) {
            //System.err.println("Array to loop size :: " + arrayToLoop.getValues().size());
            //System.err.println("CV :: " + arrayToLoop);
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Empty array is not supported.");
            return null;
        }
        
        Path currentPath = this.getStatement().getCurrentPath();
        OperonValue objLink = currentPath.getObjLink();
        if (objLink == null) {
            objLink = currentValue;
        }
        Path resetPath = currentPath.copy();
        resetPath.setObjLink(objLink);
        
        try {
            ArrayType result = new ArrayType(this.getStatement());
            OperonValue evaluatedNode = null;
            Node node = this.getParam1();
            
            OperonValue valueToTransform = ArrayGet.baseGet(this.getStatement(), arrayToLoop, 1);
            node.getStatement().setCurrentValue(valueToTransform);
            
            this.setCurrentPathWithPos(this.getStatement(), 1, objLink);
            //System.out.println(">> Initial ");
            evaluatedNode = node.evaluate();
            //System.out.println(">> Initial done");

            if (evaluatedNode instanceof FunctionRef) {
                for (int i = 0; i < arrayToLoop.getValues().size(); i ++) {
                    log.debug("loop, i == " + i);
                    this.setCurrentPathWithPos(this.getStatement(), i + 1, objLink);
                    
                    valueToTransform = ArrayGet.baseGet(this.getStatement(), arrayToLoop, i + 1);
                    FunctionRef foreachFnRef = (FunctionRef) evaluatedNode;
                    foreachFnRef.getParams().clear();
                    foreachFnRef.getParams().add(valueToTransform);
                    foreachFnRef.setCurrentValueForFunction(arrayToLoop); // ops. took out currentValue
                    OperonValue transformValueResult = foreachFnRef.invoke();
                    result.getValues().add(transformValueResult);
                }
            }
            else if (evaluatedNode instanceof LambdaFunctionRef) {
                for (int i = 0; i < arrayToLoop.getValues().size(); i ++) {
                    log.debug("loop, i == " + i);
                    this.setCurrentPathWithPos(this.getStatement(), i + 1, objLink);
                    
                    valueToTransform = ArrayGet.baseGet(this.getStatement(), arrayToLoop, i + 1);
                    LambdaFunctionRef foreachLfnRef = (LambdaFunctionRef) evaluatedNode;
                    foreachLfnRef.getParams().clear();
                    foreachLfnRef.getParams().put("$a", valueToTransform);
                    foreachLfnRef.setCurrentValueForFunction(arrayToLoop); // ops. took out currentValue
                    OperonValue transformValueResult = foreachLfnRef.invoke();
                    result.getValues().add(transformValueResult);
                }
            }
            else {
                // Observe: the first item was already evaluated above, so we add it into results
                //          here and we continue evaluating from the _second_ value.
                //          This is because we don't know when evaluating the first time what
                //          is the expr-node type (FunctionRef, FunctionLambdaRef, or pure expression).
                result.getValues().add(evaluatedNode);
                for (int i = 1; i < arrayToLoop.getValues().size(); i ++) {
                    //System.out.println(">> i=" + i + 1);
                    Node n = this.getParam1();
                    this.setCurrentPathWithPos(n.getStatement(), i + 1, objLink);
                    valueToTransform = ArrayGet.baseGet(this.getStatement(), arrayToLoop, i + 1);
                    n.getStatement().setCurrentValue(valueToTransform);
                    evaluatedNode = n.evaluate();
                    result.getValues().add(evaluatedNode);
                }
            }
            this.getStatement().setCurrentPath(resetPath);
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