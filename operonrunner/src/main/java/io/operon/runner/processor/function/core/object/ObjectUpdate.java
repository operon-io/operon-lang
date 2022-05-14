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

package io.operon.runner.processor.function.core.object;

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
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// NOTE: this throws an error if the target was not found
//
public class ObjectUpdate extends BaseArity2 implements Node, Arity2 {
    
    public ObjectUpdate(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        this.setParams(params, "update", "value", "target");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        try {
            //System.out.println("object:update");
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType obj = (ObjectType) currentValue.evaluate();
            
            ObjectType result = null;
            
            if (this.getParam1() != null && this.getParam2() != null) {
                OperonValue updateKeyValue = this.getParam2().evaluate();
                this.getStatement().setCurrentValue(obj);
                Node updateValueNode = this.getParam1();
                
                //System.out.println("object:update :: updateKeyValue :: " + );
                
                ObjectType objCopy = (ObjectType) obj.copy();
                
                if (this.getParam1() != null && this.getParam2() != null && updateKeyValue instanceof StringType) {
                    StringType updateKeyJson = (StringType) updateKeyValue;
                    String updateKeyStr = updateKeyJson.getJavaStringValue();
                    result = ObjectUpdate.doUpdateByKey(objCopy, updateKeyStr, updateValueNode);
                }
                
                else if (this.getParam1() != null && this.getParam2() != null && updateKeyValue instanceof NumberType) {
                    NumberType updateKeyJson = (NumberType) updateKeyValue;
                    int index = (int) (updateKeyJson.getDoubleValue() - 1);
                    String updateKeyStr = obj.getKeyByIndex(index);
                    updateKeyStr = updateKeyStr.substring(1, updateKeyStr.length() - 1); // remove double quotes
                    result = ObjectUpdate.doUpdateByKey(objCopy, updateKeyStr, updateValueNode);
                }
            }

            else if (this.getParam1() != null) {
                Node updateValueNode = this.getParam1();
                ObjectType objCopy = (ObjectType) obj.copy();
                for (int i = 0; i < obj.getPairs().size(); i ++) {;
                    String updateKeyStr = obj.getKeyByIndex(i);
                    updateKeyStr = updateKeyStr.substring(1, updateKeyStr.length() - 1); // remove double quotes
                    if (result == null) {
                        result = ObjectUpdate.doUpdateByKey(objCopy, updateKeyStr, updateValueNode);
                    }
                    else {
                        result = ObjectUpdate.doUpdateByKey(result, updateKeyStr, updateValueNode);
                    }
                }
            }
            
            return result;
        } catch (OperonGenericException e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static ObjectType doUpdateByKey(ObjectType obj, String updateKey, Node updateValueNode) throws OperonGenericException {
        // inject with currentValue
        OperonValue injectValue = obj.getByKey(updateKey);
        
        // inject value in object
        ObjectType injectObj = new ObjectType(obj.getStatement());
        PairType injectPair = new PairType(obj.getStatement());
        injectPair.setPair("\"" + updateKey + "\"", injectValue);
        injectObj.addPair(injectPair);
        
        updateValueNode.getStatement().setCurrentValue(injectObj);
        OperonValue updateValueJson = updateValueNode.evaluate();
        
        if (updateValueJson instanceof EmptyType == false) {
            obj.updatePairByKey(updateKey, updateValueJson);
        }
        else {
            obj.removePairByKey(updateKey);
        }
        return obj;
    }

}