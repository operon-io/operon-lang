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
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringToRaw extends BaseArity0 implements Node, Arity0 {
    
    public StringToRaw(Statement statement) {
        super(statement);
        this.setFunctionName("toRaw");
        this.setNs(Namespaces.STRING);
    }

    public RawValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StringType str = (StringType) currentValue.evaluate();
            String strValue = str.getJavaStringValue();
            RawValue result = new RawValue(this.getStatement());
            result.setValue(StringToRaw.stringToBytes(strValue, true));
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }

    public static byte[] stringToBytes(String strValue, boolean unescape) {
        if (unescape) {
            //
            // NOTE: the replaces must be run in correct order.
            //
            strValue = StringToRaw.unescapeString(strValue);
            return strValue.getBytes();
        }
        else {
            return strValue.getBytes();
        }
    }
    
    //
    // NOTE: see raw:rawToStringType for escaping string.
    //
    public static String unescapeString(String strValue) {
        return strValue.replaceAll("\\\\\"", "\"") // replace double-quotes
                        .replaceAll("\\\\n", "\n") // replace new-lines
                        .replaceAll("\\\\r", "\r") // replace carriage-return
                        .replaceAll("\\\\t", "\t") // replace tab
                        .replaceAll("\\\\f", "\f") // replace form-feed
                        .replaceAll("\\\\b", "\b") // replace backspace
                        .replaceAll("\\\\\\\\", "\\\\"); // replace backslash: "\\foo" -> "\foo"
    }
}