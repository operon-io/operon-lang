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

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.UnaryNode;
import io.operon.runner.node.BinaryNode;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ArraySwap extends BaseArity2 implements Node, Arity2, SupportsAttributes {

    private static Logger log = LogManager.getLogger(ArraySwap.class);

    public ArraySwap(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "swap", "a", "b");
    }

    public ArrayType evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType currentValueArray = (ArrayType) currentValue.evaluate();
            NumberType evaluatedParam1 = (NumberType) this.getParam1().evaluate();
            NumberType evaluatedParam2 = (NumberType) this.getParam2().evaluate();
            Collections.swap(currentValueArray.getValues(), (int) (evaluatedParam1.getDoubleValue() - 1), (int) (evaluatedParam2.getDoubleValue() - 1));
            return currentValueArray;
        } catch (Exception e) {
             ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
             return null;
        }
    }

}
