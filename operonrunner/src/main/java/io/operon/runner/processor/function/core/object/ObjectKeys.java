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
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectKeys extends BaseArity0 implements Node, Arity0 {
    
    public ObjectKeys(Statement statement) {
        super(statement);
        this.setFunctionName("keys");
        this.setNs(Namespaces.OBJECT);
    }

    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType obj = (ObjectType) currentValue.evaluate();
            
            ArrayType result = new ArrayType(this.getStatement());
            
            for (PairType pair : obj.getPairs()) {
                StringType key = new StringType(this.getStatement());
                key.setValue(pair.getKey());
                result.addValue(key);
            }
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}