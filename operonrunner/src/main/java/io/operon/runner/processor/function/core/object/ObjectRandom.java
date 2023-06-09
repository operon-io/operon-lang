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
import java.util.Random;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectRandom extends BaseArity1 implements Node, Arity1 {
    
    public ObjectRandom(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "random", "options");
        this.setNs(Namespaces.OBJECT);
    }

    public ObjectType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType result = (ObjectType) currentValue.evaluate();
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
            
            int count = 1;
            boolean allowSame = false;
            List<PairType> originalResultList = result.getPairs();
            
            if (options != null) {
                if (options.hasKey("\"count\"")) {
                    count = (int) ((NumberType) options.getByKey("count").evaluate()).getDoubleValue();
                }
            }
            
            if (count > originalResultList.size()) {
               throw new Exception("Option \"count\" cannot be larger than the input object size."); 
            }
            
            else if (count < 0) {
               throw new Exception("Option \"count\" cannot be smaller than zero."); 
            }
            
            List<PairType> resultList = new ArrayList<PairType>();

            List<Integer> availableIndexes = new ArrayList<Integer>();
            for (int i = 0; i < originalResultList.size(); i ++) {
                availableIndexes.add(i);
            }
            
            for (int i = 0; i < count; i ++) {
                int randomInt = r.nextInt(availableIndexes.size());
                resultList.add(originalResultList.get(availableIndexes.get(randomInt)));
                availableIndexes.remove(randomInt);
            }
            
            result.setPairs(resultList);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}