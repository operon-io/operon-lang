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
import io.operon.runner.processor.function.core.path.PathParts;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Cast value to Array
 *
 */
public class CastArray extends BaseArity0 implements Node, Arity0 {
     // no logger 
    
    public CastArray(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("array");
    }

    public ArrayType evaluate() throws OperonGenericException {
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        ArrayType result = new ArrayType(this.getStatement());

        if (currentValue instanceof ObjectType) {
            ObjectType obj = (ObjectType) currentValue;
            for (PairType pair : obj.getPairs()) {
                ObjectType objWithSinglePair = new ObjectType(this.getStatement());
                objWithSinglePair.addPair(pair);
                result.getValues().add(objWithSinglePair);
            }
        }
        
        else if (currentValue instanceof Path) {
            result = PathParts.getPathParts((Path) currentValue);
        }

        else if (currentValue instanceof ArrayType) {
            result = (ArrayType) currentValue;
        }

        else {
            result.getValues().add(currentValue);
        }
        
        return result;
    }

}