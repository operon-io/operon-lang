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
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.core.array.ArrayUpdate;
import io.operon.runner.processor.function.core.object.ObjectUpdate;
import io.operon.runner.processor.function.core.path.PathValue;
import io.operon.runner.processor.function.core.path.PathSubPath;
import io.operon.runner.model.path.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// This can update Array or Object, by index, key or Path.
//
// ObjectUpdate and ArrayUpdate -functions are utilized (delegated to),
// when the update-key is String / Number / Array.
//
// When update is done by Path, the algorithm starts the updates from the bottom,
// and traverses towards the top.
//
// TODO: the algorithm for Paths could be improved, by trying to update
//       multiple paths on the same joint. This might require changes to UpdateArray -logic as well.
//
public class GenericUpdate extends BaseArity2 implements Node, Arity2 {
    
    public GenericUpdate(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        this.setParams(params, "update", "value", "target");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            //System.out.println("GenericUpdate.evaluate()");
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            OperonValue updateKeyValue = null;
            Node updateValueNode = null;
            OperonValue currentValueEvaluated = null;
            
            if (this.getParam1() != null && this.getParam2() != null) {
                updateKeyValue = this.getParam2().evaluate(); // $target
                updateValueNode = this.getParam1(); // $value
            }
            else {
                updateKeyValue = NumberType.create(this.getStatement(), 0.0, (byte) 1); // position 0 => update all.
                updateValueNode = this.getParam1(); //$value
            }
            
            this.getStatement().setCurrentValue(currentValue);
            OperonValue result = currentValue;
            
            if (updateKeyValue instanceof StringType) {
                //System.out.println("GenericUpdate updateKeyValue String");
                StringType updateKeyJson = (StringType) updateKeyValue;
                String updateKeyStr = updateKeyJson.getJavaStringValue();
                currentValueEvaluated = currentValue.evaluate();
                ObjectType obj = (ObjectType) currentValueEvaluated;
                ObjectType objCopy = (ObjectType) obj.copy();
                result = ObjectUpdate.doUpdateByKey(objCopy, updateKeyStr, updateValueNode);
            }
            
            else if (updateKeyValue instanceof NumberType) {
                //System.out.println("GenericUpdate updateKeyValue Number");
                currentValueEvaluated = currentValue.evaluate();
                
                if (currentValueEvaluated instanceof ObjectType) {
                    NumberType getIndexNumberType = (NumberType) updateKeyValue;
                    int index = (int) (getIndexNumberType.getDoubleValue());
                    
                    //System.out.println("index == " + index);
                    ObjectType obj = (ObjectType) currentValueEvaluated;
                    if (index != 0) {
                        index -= 1;
                    }
                    String updateKeyStr = obj.getKeyByIndex(index);
                    //System.out.println("keyStr = " + updateKeyStr);
                    updateKeyStr = updateKeyStr.substring(1, updateKeyStr.length() - 1); // remove double quotes
                    ObjectType objCopy = (ObjectType) obj.copy();
                    result = ObjectUpdate.doUpdateByKey(objCopy, updateKeyStr, updateValueNode);
                }
                
                else if (currentValueEvaluated instanceof ArrayType) {
                    NumberType getIndexNumberType = (NumberType) updateKeyValue;
                    ArrayType currentValueArray = (ArrayType) currentValueEvaluated;
                    int index = (int) getIndexNumberType.getDoubleValue();
                    try {
                        if (index != 0) {
                            result = ArrayUpdate.updateSingleIndex(index, currentValueArray, updateValueNode);
                        }
                        else {
                            result = ArrayUpdate.updateAllIndexes(currentValueArray, updateValueNode);
                        }
                    } catch (Exception e) {
                        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), e.getMessage());
                    }
                }
            }
            
            else if (updateKeyValue instanceof ArrayType) {
                //System.out.println("GenericUpdate updateKeyValue Array");
                ArrayType updateIndexList = (ArrayType) updateKeyValue;
                currentValueEvaluated = currentValue.evaluate();

                if (currentValueEvaluated instanceof ObjectType) {
                    //
                    // TODO: implement this.
                    //
                    return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Update with an array is not supported for Object.");
                }
                
                else if (currentValueEvaluated instanceof ArrayType) {
                    ArrayType currentValueArray = (ArrayType) currentValueEvaluated;
                    try {
                        result = ArrayUpdate.updateMultipleIndex(updateIndexList, currentValueArray, updateValueNode);
                    } catch (Exception e) {
                        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), e.getMessage());
                    }
                }
            }
            
            else if (updateKeyValue instanceof Path) {
                //System.out.println("GenericUpdate updateKeyValue Path");
                result = updatePathValue(currentValue, (Path) updateKeyValue, updateValueNode);
            }
            
            return result;
        } catch (OperonGenericException oge) {
            //System.err.println("ERROR :: " + e.getMessage());
            throw oge;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

    //
    // Update the value refered by Path
    //
    public static OperonValue updatePathValue(OperonValue valueToUpdate, Path pathToUpdate, Node updateValueNode) throws OperonGenericException {
        //
        // Initial result for the first update:
        //
        OperonValue pathValue = PathValue.get(valueToUpdate, pathToUpdate);

        //System.out.println("SET pathValue=" + pathValue);
        updateValueNode.getStatement().setCurrentValue(pathValue);

        OperonValue result = updateValueNode.evaluate();
        //System.out.println("initial result=" + result);
    
        //System.out.println("Updating path: " + pathToUpdate);
        PathPart lastPathPart = pathToUpdate.getPathParts().get(pathToUpdate.getPathParts().size() - 1);
        OperonValue currentParentValue = valueToUpdate;
        //System.out.println(">> 1");
        
        //
        // This algorithm starts the updates from the bottom,
        // and traverses path back to top.
        //
        // First do the intermediate updates:
        //
        if (pathToUpdate.getPathParts().size() > 1) {
            //System.out.println("TRAVERSE TO: " + PathSubPath.subPath(pathToUpdate, pathToUpdate.getPathParts().size() - 1));
            currentParentValue = PathValue.get(valueToUpdate, PathSubPath.subPath(pathToUpdate, pathToUpdate.getPathParts().size() - 1));
            //System.out.println("Initial parent-value: " + currentParentValue);
            
            //
            // Skip the last path-part, because we start the update from it, so it is the 
            // initial - first update, which is in the result
            //
            for (int i = pathToUpdate.getPathParts().size() - 1; i >= 1; i --) {
                //System.out.println(">> LOOP, i = " + i + ", pathToUpdate = " + pathToUpdate);
                
                // Skip the initial value-fetch, because result is the initial-result we want to update to last-path part:
                if (i < pathToUpdate.getPathParts().size() - 1) {
                    result = PathValue.get(valueToUpdate, pathToUpdate);
                }
                
                //System.out.println(">> result " + result);
                
                lastPathPart = pathToUpdate.getPathParts().get(pathToUpdate.getPathParts().size() - 1);
                
                // #Added to fix UpdateTests.updatePersons2Test
                // TODO: this should be optimized
                currentParentValue = PathValue.get(valueToUpdate, PathSubPath.subPath(pathToUpdate, pathToUpdate.getPathParts().size() - 1));
                
                if (lastPathPart instanceof KeyPathPart) {
                    //System.out.println("KeyPathPart. Try to update result - intermediate, lastPathPart=" + lastPathPart);
                    //System.out.println("  Update against: " + currentParentValue);
                    //System.out.println("  Update with: " + result);
                    ObjectType objCopy = (ObjectType) (currentParentValue.evaluate().copy());
                    result = ObjectUpdate.doUpdateByKey(objCopy, ((KeyPathPart) lastPathPart).getKey(), result);
                    //System.out.println("KeyPathPart DONE, result " + result);
                }
                
                else { //(lastPathPart instanceof PosPathPart)
                    //System.out.println("PosPathPart: " + lastPathPart);
                    //System.out.println("  currentParentValue=" + currentParentValue.evaluate());
                    //System.out.println("  result=" + result);
                    try {
                        result = ArrayUpdate.updateSingleIndex(((PosPathPart) lastPathPart).getPos(), (ArrayType) currentParentValue.evaluate(), result);
                    } catch (Exception e) {
                        return ErrorUtil.createErrorValueAndThrow(valueToUpdate.getStatement(), "FUNCTION", "update", e.getMessage());
                    }
                    //System.out.println("PosPathPart DONE");
                }
                
                pathToUpdate = PathSubPath.subPath(pathToUpdate, i);
            }
        }
        
        //System.out.println("Final update");
        
        //
        // Do the final update:
        //
        lastPathPart = pathToUpdate.getPathParts().get(pathToUpdate.getPathParts().size() - 1);
        // #Added to fix UpdateTests.updatePersons2Test
        currentParentValue = PathValue.get(valueToUpdate, PathSubPath.subPath(pathToUpdate, pathToUpdate.getPathParts().size() - 1));
        if (lastPathPart instanceof KeyPathPart) {
            //System.out.println("Try to final update result KeyPathPart, cv=" + valueToUpdate);
            try {
                ObjectType objCopy = (ObjectType) valueToUpdate.evaluate().copy();
                result = ObjectUpdate.doUpdateByKey(objCopy, ((KeyPathPart) lastPathPart).getKey(), result);
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(valueToUpdate.getStatement(), "FUNCTION", "update", e.getMessage());
            }
        }
        else {// (lastPathPart instanceof PosPathPart)
            //System.out.println("Try to final update result PosPathPart, cv=" + valueToUpdate);
            try {
                result = ArrayUpdate.updateSingleIndex(((PosPathPart) lastPathPart).getPos(), (ArrayType) valueToUpdate.evaluate(), result);
            } catch (Exception e) {
                return ErrorUtil.createErrorValueAndThrow(valueToUpdate.getStatement(), "FUNCTION", "update", e.getMessage());
            }
        }
        return result;
    }

}