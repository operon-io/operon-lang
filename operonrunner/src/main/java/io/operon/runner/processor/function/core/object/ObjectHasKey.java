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

public class ObjectHasKey extends BaseArity1 implements Node, Arity1 {
    
    public ObjectHasKey(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "hasKey", "key");
        this.setNs(Namespaces.OBJECT);
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            Node keyNode = this.getParam1().evaluate();
            String key = ((StringType) keyNode).getJavaStringValue();
            
            ObjectType obj = (ObjectType) currentValue.evaluate();
            boolean hasKey = obj.hasKey("\"" + key + "\"");
            
            if (hasKey) {
                TrueType result = new TrueType(this.getStatement());
                return result;
            }
            
            else {
                FalseType result = new FalseType(this.getStatement());
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}