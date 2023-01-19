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

package io.operon.runner.processor.function.core.string;

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

public class StringRandom extends BaseArity1 implements Node, Arity1 {
    private final String ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!_";
    
    public StringRandom(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "random", "options");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            long seed = -1;
            ObjectType options = null;
            if (this.getParam1() != null) {
                options = (ObjectType) this.getParam1().evaluate();
                
                if (options.hasKey("\"seed\"")) {
                    seed = (long) ((NumberType) options.getByKey("seed").evaluate()).getDoubleValue();
                }
            }
            
            Random r;
            
            if (seed >= 0) {
                r = new Random(seed);
            }
            
            else {
                r = new Random();
            }
            
            int length = 1;
            String allowedChars = null;
            
            if (options != null) {
                if (options.hasKey("\"length\"")) {
                    length = (int) ((NumberType) options.getByKey("length").evaluate()).getDoubleValue();
                }
                
                if (options.hasKey("\"allowedChars\"")) {
                    allowedChars = ((StringType) options.getByKey("allowedChars").evaluate()).getJavaStringValue();
                }
            }
            
            if (length < 0) {
               throw new Exception("Option \"length\" cannot be smaller than zero."); 
            }
            
            StringBuilder sb = new StringBuilder();
            
            if (allowedChars == null) {
                for (int i = 0; i < length; i ++) {
                    sb.append(ALLOWED_CHARS.charAt(r.nextInt(ALLOWED_CHARS.length())));
                }
            }
            
            else {
                for (int i = 0; i < length; i ++) {
                    sb.append(allowedChars.charAt(r.nextInt(allowedChars.length())));
                }
            }
            
            StringType result = new StringType(this.getStatement());
            result.setFromJavaString(sb.toString());
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}