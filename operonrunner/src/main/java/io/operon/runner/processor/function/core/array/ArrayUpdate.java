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

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ArrayUpdate extends BaseArity2 implements Node, Arity2, SupportsAttributes {

     // no logger 

    public ArrayUpdate(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        this.setParams(params, "update", "value", "target");
        this.setNs(Namespaces.ARRAY);
    }

    public ArrayType evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType currentValueArray = (ArrayType) currentValue.evaluate();

            if (this.getParam1() != null && this.getParam2() != null) {
                OperonValue evaluatedParam2 = (OperonValue) this.getParam2().evaluate(); // $target
                if (evaluatedParam2 instanceof NumberType) {
                    NumberType getIndexNumberType = (NumberType) evaluatedParam2;
                    currentValueArray = ArrayUpdate.updateSingleIndex((int) getIndexNumberType.getDoubleValue(), currentValueArray, this.getParam1());
                }
                
                else if (evaluatedParam2 instanceof ArrayType) {
                    ArrayType updateIndexList = (ArrayType) evaluatedParam2;
                    currentValueArray = ArrayUpdate.updateMultipleIndex(updateIndexList, currentValueArray, this.getParam1());
                }
            }
            
            else if (this.getParam1() != null) {
                currentValueArray = ArrayUpdate.updateAllIndexes(currentValueArray, this.getParam1());
            }

            return currentValueArray;
        } catch (OperonGenericException oge) {
            throw oge;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static ArrayType updateSingleIndex(int updateIndex, ArrayType currentValueArray, Node updateNode) throws OperonGenericException, Exception {
        //
        // Update by updateIndex
        //
        Path currentPath = currentValueArray.getStatement().getCurrentPath();
        OperonValue objLink = currentPath.getObjLink();
        if (objLink == null) {
            objLink = currentValueArray;
        }
        
        Path resetPath = currentPath.copy();
        resetPath.setObjLink(objLink);
        
        if (updateIndex >= 1) {
            List<Node> arrayNodes = currentValueArray.getValues();
            try {
                //
                // Set new PosPathPart:
                //
                Path currentPathCopy = currentPath.copy();
                PathPart pp = new PosPathPart(updateIndex);
                currentPathCopy.getPathParts().add(pp);
                currentPathCopy.setObjLink(objLink);
                updateNode.getStatement().setCurrentPath(currentPathCopy);
                
                OperonValue arrayValueToUpdate = ArrayGet.baseGet(currentValueArray.getStatement(), currentValueArray, updateIndex);
                updateNode.getStatement().setCurrentValue(arrayValueToUpdate);
                //System.out.println("Set @ : " + arrayValueToUpdate);
                //System.out.println("Pos @ : " + arrayValueToUpdate.getPosition());
                //System.out.println("Par @ : " + arrayValueToUpdate.getParentObj());
                OperonValue updateValue = (OperonValue) updateNode.evaluate();
                arrayNodes.set(updateIndex - 1, updateValue);
            } catch (Exception ex) {
                ErrorUtil.createErrorValueAndThrow(currentValueArray.getStatement(), "FUNCTION", "array:update", "Index out of bounds.");
            }
        }
        currentValueArray.getStatement().setCurrentPath(resetPath);
        return currentValueArray;
    }

    public static ArrayType updateMultipleIndex(ArrayType updateIndexList, ArrayType currentValueArray, Node updateNode) throws OperonGenericException, Exception {
        List<Node> arrayNodes = currentValueArray.getValues();
        
        Path currentPath = currentValueArray.getStatement().getCurrentPath();
        OperonValue objLink = currentPath.getObjLink();
        if (objLink == null) {
            objLink = currentValueArray;
        }
        
        Path resetPath = currentPath.copy();
        resetPath.setObjLink(objLink);
        
        for (Node index : updateIndexList.getValues()) {
            int updateIndex = (int) ((NumberType) index.evaluate()).getDoubleValue();
            if (updateIndex >= 1) {
                //
                // Set new PosPathPart:
                //
                Path currentPathCopy = currentPath.copy();
                PathPart pp = new PosPathPart(updateIndex);
                currentPathCopy.getPathParts().add(pp);
                currentPathCopy.setObjLink(objLink);
                updateNode.getStatement().setCurrentPath(currentPathCopy);
                
                try {
                    OperonValue arrayValueToUpdate = ArrayGet.baseGet(currentValueArray.getStatement(), currentValueArray, updateIndex);
                    updateNode.getStatement().setCurrentValue(arrayValueToUpdate);
                    OperonValue updateValue = (OperonValue) updateNode.evaluate();
                    arrayNodes.set(updateIndex - 1, updateValue);
                } catch (Exception ex) {
                    ErrorUtil.createErrorValueAndThrow(currentValueArray.getStatement(), "FUNCTION", "array:update", "Index out of bounds.");
                }
            }
        }
        currentValueArray.getStatement().setCurrentPath(resetPath);
        return currentValueArray;
    }

    public static ArrayType updateAllIndexes(ArrayType currentValueArray, Node updateNode) throws OperonGenericException, Exception {
        List<Node> arrayNodes = currentValueArray.getValues();
        
        Path currentPath = currentValueArray.getStatement().getCurrentPath();
        OperonValue objLink = currentPath.getObjLink();
        if (objLink == null) {
            objLink = currentValueArray;
        }
        
        Path resetPath = currentPath.copy();
        resetPath.setObjLink(objLink);
        
        // NOTE: this uses baseGet, because we still have to evaluate the value against
        //       the update-expression, which might make use of the old-value.
        for (int i = 1; i < currentValueArray.getValues().size() + 1; i ++) {
            try {
                //
                // Set new PosPathPart:
                //
                Path currentPathCopy = currentPath.copy();
                PathPart pp = new PosPathPart(i);
                currentPathCopy.getPathParts().add(pp);
                currentPathCopy.setObjLink(objLink);
                updateNode.getStatement().setCurrentPath(currentPathCopy);
                
                OperonValue arrayValueToUpdate = ArrayGet.baseGet(currentValueArray.getStatement(), currentValueArray, i);
                updateNode.getStatement().setCurrentValue(arrayValueToUpdate);
                OperonValue updateValue = (OperonValue) updateNode.evaluate();
                arrayNodes.set(i - 1, updateValue);
            } catch (Exception ex) {
                ErrorUtil.createErrorValueAndThrow(currentValueArray.getStatement(), "FUNCTION", "array:update", "Index out of bounds.");
            }
        }
        currentValueArray.getStatement().setCurrentPath(resetPath);
        return currentValueArray;
    }

}
