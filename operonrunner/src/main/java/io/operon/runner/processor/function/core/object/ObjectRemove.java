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
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectRemove extends BaseArity1 implements Node, Arity1 {
    
    public ObjectRemove(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "remove", "key");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ObjectType obj = (ObjectType) currentValue.evaluate();
        
        OperonValue rmKeyValue = (OperonValue) this.getParam1().evaluate();
        
        if (rmKeyValue instanceof StringType) {
            StringType rmKey = (StringType) rmKeyValue;
            obj.removePairByKey(rmKey.getJavaStringValue());
        }
        else if (rmKeyValue instanceof NumberType) {
            NumberType rmKey = (NumberType) rmKeyValue;
            int index = (int) (rmKey.getDoubleValue() - 1);
            String rmKeyStr = obj.getKeyByIndex(index);
            rmKeyStr = rmKeyStr.substring(1, rmKeyStr.length() - 1);
            obj.removePairByKey(rmKeyStr);
        }
        
        return obj;
    }

}