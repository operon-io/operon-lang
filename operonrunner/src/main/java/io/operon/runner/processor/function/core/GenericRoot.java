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

package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class GenericRoot extends BaseArity0 implements Node, Arity0 {
    
    public GenericRoot(Statement statement) {
        super(statement);
        this.setFunctionName("root");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            Path path = this.getStatement().getCurrentPath();
            //System.out.println("path=" + path);
            OperonValue result = path.getObjLink();
            
            // If no objLink, default to currentValue:
            if (result == null) {
                //System.out.println("No objLink");
                result = this.getStatement().getCurrentValue();
            }

            //Do not set new path!
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

}