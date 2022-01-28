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
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.core.path.PathCreate;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ArrayToPath extends BaseArity0 implements Node, Arity0 {
    
    public ArrayToPath(Statement statement) {
        super(statement);
        this.setFunctionName("toPath");
    }

    public Path evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType array = (ArrayType) currentValue.evaluate();
            
            Path resultPath = new Path(this.getStatement());
            
            StringBuilder sb = new StringBuilder();
            
            for (int i = 0; i < array.getValues().size(); i ++) {
                Node value = array.getValues().get(i);
                value = (OperonValue) value.evaluate();
                if (value instanceof NumberType) {
                    int intValue = (int) ((NumberType) value).getDoubleValue();
                    sb.append("[" + intValue + "]");
                }
                else if (value instanceof StringType) {
                    sb.append("." + ((StringType) value).getJavaStringValue());
                }
            }
            
            List<PathPart> pathParts = PathCreate.constructPathParts(sb.toString());
            resultPath.setPathParts(pathParts);
            return resultPath;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}