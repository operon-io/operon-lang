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
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// Reduces to single value, starting from left.
//    NOTE: if LambdaFunctionRef is used, then the params must be named as "$a" and "$b" (also in this order).
// 
public class ArrayReduce extends BaseArity2 implements Node, Arity2, SupportsAttributes {
     // no logger 
    
    private Node configs;
    
    public ArrayReduce(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        this.setParams(params, "reduce", "expr", "options");
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("Reduce :: evaluate()");
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            ArrayType arrayToReduce = (ArrayType) currentValue.evaluate();
            ArrayType arrayToReduceCopy = JsonUtil.copyArray(arrayToReduce);
            
            if (this.getParam2() != null) {
                this.setConfigs(this.getParam2());
            }
            if (arrayToReduce.getValues().size() == 0) {
                return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Empty array is not supported.");
            }
            Info info = this.resolveConfigs();
            if (arrayToReduce.getValues().size() == 1 && info.initialValue == null) {
                return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Initial-value is required when array has only one member.");
            }
            boolean fromLeft = true;
            if (info.direction.equals("left") == false) {
                fromLeft = false;
            }
            
            //
            // Must copy to ensure not to modify the underlying currentValue from stmt.
            //
            OperonValue result = this.doReduce(arrayToReduceCopy, fromLeft, info.initialValue);
            //:OFF:log.debug("Reduce :: loop done");
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

    public OperonValue doReduce(ArrayType arrayToReduce, boolean fromLeft, OperonValue initialValue) throws OperonGenericException {
        // Set the initial value to accumulate to:
        OperonValue result = null;
        int offsetLoop = 1;
        int offsetGet = 2;
        if (initialValue == null) {
            if (fromLeft) {
                result = ArrayGet.baseGet(this.getStatement(), arrayToReduce, 1);
                offsetLoop = 1;
                offsetGet = 2;
            }
            else {
                result = ArrayGet.baseGet(this.getStatement(), arrayToReduce, arrayToReduce.getValues().size());
                offsetLoop = 1;
                offsetGet = 1;
            }
        }
        else {
            result = initialValue;
            if (fromLeft) {
                offsetLoop = 0;
                offsetGet = 1;
            }
            else {
                offsetLoop = 0;
                offsetGet = 0;
            }
        }
        //:OFF:log.debug("Reduce :: start loop");
        
        Path currentPath = arrayToReduce.getStatement().getCurrentPath();
        OperonValue objLink = currentPath.getObjLink();
        if (objLink == null) {
            objLink = arrayToReduce;
        }
        
        Path resetPath = currentPath.copy();
        resetPath.setObjLink(objLink);
        
        for (int i = 0; i < arrayToReduce.getValues().size() - offsetLoop; i ++) {
            OperonValue value1 = result; // NOTE: value1 does not support attributes (pos, next, etc.) because it is an accumulator for result.
            OperonValue value2 = null;
            
            //
            // Set new PosPathPart:
            //
            Path currentPathCopy = currentPath.copy();
            currentPathCopy.setObjLink(objLink);
            
            if (fromLeft) {
                int pos = i + offsetGet;
                PathPart pp = new PosPathPart(pos);
                currentPathCopy.getPathParts().add(pp);
                value2 = ArrayGet.baseGet(this.getStatement(), arrayToReduce, pos);
            }
            else {
                int pos = arrayToReduce.getValues().size() - (i + offsetGet);
                PathPart pp = new PosPathPart(pos);
                currentPathCopy.getPathParts().add(pp);
                value2 = ArrayGet.baseGet(this.getStatement(), arrayToReduce, pos);
            }
            
            this.getStatement().setCurrentPath(currentPathCopy);
            //
            // END ATTRIBUTES
            //
            
            Node paramNode = this.getParam1();

            paramNode.getStatement().getRuntimeValues().put("$a", value1);
            paramNode.getStatement().getRuntimeValues().put("$b", value2);
            Node reduceFunctionRefNode = paramNode.evaluate();
            
            OperonValue reducedValue = null;
            if (reduceFunctionRefNode instanceof FunctionRef) {
                FunctionRef reduceFnRef = (FunctionRef) reduceFunctionRefNode;
                reduceFnRef.getParams().clear();
                reduceFnRef.getParams().add(value1);
                reduceFnRef.getParams().add(value2);
                reduceFnRef.setCurrentValueForFunction(arrayToReduce);
                reducedValue = reduceFnRef.invoke();
            } 
            else if (reduceFunctionRefNode instanceof LambdaFunctionRef) {
                LambdaFunctionRef reduceLfnRef = (LambdaFunctionRef) reduceFunctionRefNode;
                reduceLfnRef.getParams().clear();
                // NOTE: we cannot guarantee the order of keys that Map.keySet() returns,
                //       therefore we must assume that the keys are named in certain manner.
                reduceLfnRef.getParams().put("$a", value1);
                reduceLfnRef.getParams().put("$b", value2);
                reduceLfnRef.setCurrentValueForFunction(arrayToReduce);
                reducedValue = reduceLfnRef.invoke();
            }
            else {
                reducedValue = (OperonValue) reduceFunctionRefNode;
            }
            result = reducedValue;
        }
        this.getStatement().setCurrentPath(resetPath);
        return result;
    }
    
    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this.getStatement());
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

    public Info resolveConfigs() throws OperonGenericException {
        Info info = new Info();
        
        if (this.configs == null) {
            return info;
        }
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"direction\"":
                    String directionValue = ((StringType) pair.getEvaluatedValue()).getJavaStringValue();
                    info.direction = directionValue.toLowerCase();
                    break;
                case "\"initialvalue\"":
                    OperonValue initialValue = pair.getEvaluatedValue();
                    info.initialValue = initialValue;
                    break;
                default:
                    break;
            }
        }
        return info;
    }

    private class Info {
        public String direction = "left";
        public OperonValue initialValue = null;
    }

}