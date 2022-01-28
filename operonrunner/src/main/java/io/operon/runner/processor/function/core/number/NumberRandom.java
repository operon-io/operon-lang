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

package io.operon.runner.processor.function.core.number;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.Random;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class NumberRandom extends BaseArity1 implements Node, Arity1 {
    
    public NumberRandom(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "random", "options");
    }

    public NumberType evaluate() throws OperonGenericException {        
        try {
            long seed = 0;
            ObjectType options = null;
            
            if (this.getParam1() != null) {
                options = (ObjectType) this.getParam1().evaluate();
                
                if (options.hasKey("\"seed\"")) {
                    seed = (long) ((NumberType) options.getByKey("seed").evaluate()).getDoubleValue();
                }
            }
            
            Random r;
            
            if (seed != 0) {
                r = new Random(seed);
            }
            
            else {
                r = new Random();
            }
            
            double randomDouble = r.nextDouble();
            
            if (options != null) {
                if (options.hasKey("\"min\"") && options.hasKey("\"max\"")) {
                    double minValue = ((NumberType) options.getByKey("min").evaluate()).getDoubleValue();
                    double maxValue = ((NumberType) options.getByKey("max").evaluate()).getDoubleValue();
                    randomDouble = minValue + randomDouble * (maxValue - minValue);
                }
                
                else if (options.hasKey("\"min\"")) {
                    double minValue = ((NumberType) options.getByKey("min").evaluate()).getDoubleValue();
                    randomDouble = minValue + randomDouble;
                }
                
                else if (options.hasKey("\"max\"")) {
                    double maxValue = ((NumberType) options.getByKey("max").evaluate()).getDoubleValue();
                    randomDouble = maxValue * randomDouble;
                }
            }
            
            NumberType result = new NumberType(this.getStatement());
            result.setPrecision((byte) 16);
            result.setDoubleValue(randomDouble);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "number:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}