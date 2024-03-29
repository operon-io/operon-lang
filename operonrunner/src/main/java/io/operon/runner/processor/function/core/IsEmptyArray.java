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

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Check if value is an empty String (""). Returns true or false.
 *
 */
public class IsEmptyArray extends BaseArity0 implements Node, Arity0 {
     // no logger 
    
    public IsEmptyArray(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("isEmptyArray");
        this.setNs(Namespaces.CORE);
    }

    public OperonValue evaluate() throws OperonGenericException {
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();

        if (currentValue instanceof ArrayType) {
            ArrayType arr = (ArrayType) currentValue;
            if (arr.getValues().size() == 0) {
                TrueType resultTrue = new TrueType(this.getStatement());
                return resultTrue;
            }
            else {
                FalseType resultFalse = new FalseType(this.getStatement());
                return resultFalse;
            }
        }
        
        else {
            FalseType resultFalse = new FalseType(this.getStatement());
            return resultFalse;
        }
    }

}