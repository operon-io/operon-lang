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
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Figure out what Operon-type the currentValue is.
 *
 */
public class OperonType extends BaseArity0 implements Node, Arity0 {
     // no logger 
    
    public OperonType(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("type");
    }

    public StringType evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        
        StringType result = new StringType(this.getStatement());
        
        if (currentValue instanceof StringType) {
            result.setFromJavaString("String");
        }
        
        else if (currentValue instanceof NumberType) {
            result.setFromJavaString("Number");
        }
        
        else if (currentValue instanceof TrueType) {
            result.setFromJavaString("True");
        }
        
        else if (currentValue instanceof FalseType) {
            result.setFromJavaString("False");
        }
        
        else if (currentValue instanceof ArrayType) {
            result.setFromJavaString("Array");
        }
        
        else if (currentValue instanceof ObjectType) {
            result.setFromJavaString("Object");
        }
        
        else if (currentValue instanceof NullType) {
            result.setFromJavaString("Null");
        }
        
        else if (currentValue instanceof EmptyType) {
            result.setFromJavaString("Empty");
        }
        
        else if (currentValue instanceof LambdaFunctionRef) {
            LambdaFunctionRef lfr = (LambdaFunctionRef) currentValue;
            int lfrParamsSize = lfr.getParams().size();
            result.setFromJavaString("lambda:" + String.valueOf(lfrParamsSize));
        }
        
        else if (currentValue instanceof FunctionRef) {
            FunctionRef fr = (FunctionRef) currentValue;
            String typeName = "function:" + fr.getFunctionFQName();
            //
            // NOTES:
            //   - function without namespace appears like: "function::foo:0"
            //   - function with namespace appears like: "function:my:foo:0"
            //
            result.setFromJavaString(typeName);
        }

        else if (currentValue instanceof RawValue) {
            result.setFromJavaString("Binary");
        }

        else if (currentValue instanceof StreamValue) {
            result.setFromJavaString("Stream");
        }

        else if (currentValue instanceof ErrorValue) {
            result.setFromJavaString("Error");
        }

        else if (currentValue instanceof io.operon.runner.node.type.Path) {
            result.setFromJavaString("Path");
        }

        else if (currentValue instanceof EndValueType) {
            result.setFromJavaString("End");
        }

        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), "Could not detect type " + currentValue);
            return null;
        }
        
        return result;
    }

}