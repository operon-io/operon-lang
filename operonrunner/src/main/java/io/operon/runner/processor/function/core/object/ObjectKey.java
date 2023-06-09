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
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Returns the key from given index. If no index given, then takes the first key.
// If object is empty, then returns empty-value.
//
public class ObjectKey extends BaseArity1 implements Node, Arity1 {
    
    public ObjectKey(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "key", "index");
        this.setNs(Namespaces.OBJECT);
    }

    // String, Number Or Empty
    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            OperonValue result = null;
            
            //System.out.println("=> key(), CurrentValue :: " + currentValue);
            OperonValue evaluatedValue = (OperonValue) currentValue.evaluate();
            
            if (evaluatedValue instanceof ObjectType) {
                //System.out.println("=> key(), check first key");
                ObjectType obj = (ObjectType) evaluatedValue;
                List<PairType> pairs = obj.getPairs();
                
                int keyIndex = 0;
                if (this.getParam1() != null) {
                    NumberType keyIndexNumber = (NumberType) this.getParam1().evaluate();
                    keyIndex = (int) (keyIndexNumber.getDoubleValue() - 1);
                }
                
                if (pairs.size() > 0) {
                    PairType p = pairs.get(keyIndex);
                    StringType resultStr = new StringType(this.getStatement());
                    resultStr.setValue(p.getKey());
                    result = resultStr;
                }
                else {
                    result = new EmptyType(this.getStatement());
                }
            }
            else {
                result = new EmptyType(this.getStatement());
            }

            //System.out.println("=> key() = " + result);
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
        }
    }

}