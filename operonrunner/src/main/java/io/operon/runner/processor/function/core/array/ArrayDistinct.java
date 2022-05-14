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
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ArrayDistinct extends BaseArity0 implements Node, Arity0 {
     // no logger 
    
    public ArrayDistinct(Statement statement) {
        super(statement);
        this.setFunctionName("distinct");
    }

    public ArrayType evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType array = (ArrayType) currentValue.evaluate();
            
            List<Node> resultList = new ArrayList<Node>();
            
            //
            // This algorithm is naive --> improve
            //
            for (int i = 0; i < array.getValues().size(); i ++) {
                OperonValue value = (OperonValue) array.getValues().get(i);
                boolean found = false;
                int resultSize = resultList.size();
                
                for (int j = 0; j < resultSize; j ++) {
                    if (JsonUtil.isIdentical(value, (OperonValue) resultList.get(j)) == true) {
                        found = true;
                    }
                }
                if (found == false) {
                    resultList.add(value);
                }
            }
            array.setValues(resultList);
            ////:OFF:log.debug("DISTINCT :: " + this.getStatement().getId());
            return array;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}