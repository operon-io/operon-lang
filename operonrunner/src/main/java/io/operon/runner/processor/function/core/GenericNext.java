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

package io.operon.runner.processor.function.core;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.function.core.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.processor.function.core.array.ArrayGet;
import io.operon.runner.model.exception.OperonGenericException;

//
// next() returns the value of the next object-key-pair or array item.
//  NOTE: returns Empty when no next is available.
//        This is better than returning Null, because
//        null might be defined in the array/object,
//        but Empty is not.
public class GenericNext extends BaseArity0 implements Node, Arity0 {
    
    public GenericNext(Statement statement) {
        super(statement);
        this.setFunctionName("next");
        this.setNs(Namespaces.CORE);
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            MultiNode mn = new MultiNode(this.getStatement());
            PathCurrent pathCurrent = new PathCurrent(this.getStatement());
            PathNext pathNext = new PathNext(this.getStatement());
            PathValue pathValue = new PathValue(this.getStatement());
            
            mn.addNode(pathCurrent);
            mn.addNode(pathNext);
            mn.addNode(pathValue);
            
            OperonValue result = mn.evaluate();
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

}