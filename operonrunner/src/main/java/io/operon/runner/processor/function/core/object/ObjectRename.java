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

package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectRename extends BaseArity2 implements Node, Arity2 {
    
    public ObjectRename(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        this.setParams(params, "rename", "key", "to");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ObjectType obj = (ObjectType) currentValue.evaluate();
        OperonValue rnKeyValue = (OperonValue) this.getParam1().evaluate();
        
        if (rnKeyValue instanceof StringType) {
            StringType rnKey = (StringType) rnKeyValue;
            
            //
            // This is not needed because rnKey _is_ the current-value aswell:
            //
            //this.getStatement().setCurrentValue(rnKey);
            StringType rnTo = (StringType) this.getParam2().evaluate();
            //this.getStatement().setCurrentValue(currentValue);
            
            //
            // Must create a copy so that the following would work:
            //   $: {"src": 1, "trgt": 2} Select: {"bin": $ => rename("src", "trgt"), "bai": $}
            // otherwise "bai" would get the renamed object.
            //
            ObjectType objCopy = ((ObjectType) obj.copy());
            objCopy.renameKey(rnKey.getJavaStringValue(), rnTo.getJavaStringValue());
            return objCopy;
        }
        
        else if (rnKeyValue instanceof NumberType) {
            NumberType rnKey = (NumberType) rnKeyValue;
            int index = (int) (rnKey.getDoubleValue() - 1);
            String rnKeyStr = obj.getKeyByIndex(index);
            StringType rnKeyJsStr = new StringType(this.getStatement());
            rnKeyJsStr.setValue(rnKeyStr); // already double-quoted
            
            this.getStatement().setCurrentValue(rnKeyJsStr);
            StringType rnTo = (StringType) this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            
            //
            // Must create a copy so that the following would work:
            //   $: {"src": 1, "trgt": 2} Select: {"bin": $ => rename("src", "trgt"), "bai": $}
            // otherwise "bai" would get the renamed object.
            //
            ObjectType objCopy = ((ObjectType) obj.copy());
            objCopy.renameByIndex(index, rnTo.getJavaStringValue());
            return objCopy;
        }
        
        // {"bin": "binToBaa"}
        if (rnKeyValue instanceof ObjectType) {
            ObjectType rnObj = (ObjectType) rnKeyValue;
            List<PairType> rnObjPairs = rnObj.getPairs();
            ObjectType objCopy = ((ObjectType) obj.copy());
            
            for (int i = 0; i < rnObjPairs.size(); i ++) {
                String rnKey = rnObjPairs.get(i).getKey();
                rnKey = rnKey.substring(1, rnKey.length() - 1); // rm quotes
                
                // This is to set the rnKey as current-value when evaluating
                // the rnTo -value.
                StringType rnKeyJsStr = StringType.create(this.getStatement(), rnKey);
                OperonValue rnToValue = rnObjPairs.get(i).getValue();
                rnToValue.getStatement().setCurrentValue(rnKeyJsStr);
                StringType rnTo = (StringType) rnToValue.evaluate();
                this.getStatement().setCurrentValue(currentValue);
                
                objCopy.renameKey(rnKey, rnTo.getJavaStringValue());
            }
            return objCopy;
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "FUNCTION", "PARAM", "object:" + this.getFunctionName() + ": key type not valid");
            return null;
        }
    }

    public boolean ensuredCurrentValueSetBetweenParamEvaluations() {
        return true;
    }

}