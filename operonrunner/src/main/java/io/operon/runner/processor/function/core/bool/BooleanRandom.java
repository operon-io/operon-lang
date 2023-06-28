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

package io.operon.runner.processor.function.core.bool;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.Random;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class BooleanRandom extends BaseArity1 implements Node, Arity1 {
    
    public BooleanRandom(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "random", "options");
        this.setNs(Namespaces.BOOLEAN);
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            long seed = 0;
            ObjectType options = null;
            double probability = 0.5; // Default probability value
            
            if (this.getParam1() != null) {
                options = (ObjectType) this.getParam1().evaluate();
                
                if (options.hasKey("\"seed\"")) {
                    seed = (long) ((NumberType) options.getByKey("seed").evaluate()).getDoubleValue();
                }
                
                if (options.hasKey("\"probability\"")) {
                    probability = ((NumberType) options.getByKey("probability").evaluate()).getDoubleValue();
                }
            }
            
            if (probability < 0 || probability > 1) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "boolean:" + this.getFunctionName(), "Invalid probability value. Probability must be between 0 and 1.");
            }
            
            Random r;
            
            if (seed != 0) {
                r = new Random(seed);
            } else {
                r = new Random();
            }
            
            boolean randomBoolean = r.nextDouble() > probability;
            
            if (randomBoolean) {
                return new TrueType(this.getStatement());
            } else {
                return new FalseType(this.getStatement());
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "boolean:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }


}