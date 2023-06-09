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
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Cast value to String
 *
 */
public class CastString extends BaseArity1 implements Node, Arity1 {
     // no logger 
    
    public CastString(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "string", "options");
        this.setNs(Namespaces.CORE);
    }

    public StringType evaluate() throws OperonGenericException {
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();

        if (currentValue instanceof StringType) {
            return (StringType) currentValue;
        }

        else if (currentValue instanceof RawValue) {
            boolean escape = true;
            if (this.getParam1() != null) {
                ObjectType options = ((ObjectType) this.getParam1().evaluate());
                if (options.hasKey("\"escape\"")) {
                    OperonValue escapeValue = (OperonValue) options.getByKey("escape").evaluate();
                    if (escapeValue instanceof FalseType) {
                        escape = false;
                    }
                }
            }
            StringType result = RawToStringType.rawToStringType(this.getStatement(), (RawValue) currentValue.evaluate(), escape);
            return result;
        }

        //
        // This joins the array values into one string with the given "separator".
        // E.g. ["bin", "bai", "baa"] --> "bin,bai,baa"
        //
        // TODO: this is a problematic behavior. The evaluate on the top-level re-evaluates the ObjectType each time.
        //
        else if (currentValue instanceof ArrayType) {
            StringType result = new StringType(this.getStatement());

            StringBuilder sb = new StringBuilder();
            ObjectType options = null;
            String separator = null;
            
            if (this.getParam1() != null) {
                options = ((ObjectType) this.getParam1().evaluate());
                if (options.hasKey("\"separator\"")) {
                    separator = ((StringType) options.getByKey("separator")).getJavaStringValue();
                }
            }

            if (separator != null) {
                List<Node> values = ((ArrayType) currentValue.evaluate()).getValues();
    
                for (int i = 0; i < values.size(); i ++) {
                    OperonValue jv = (OperonValue) values.get(i).evaluate();
                    if (jv instanceof StringType) {
                        StringType s = (StringType) jv;
                        sb.append(s.getJavaStringValue());
                    }
                    else if (jv instanceof ArrayType == false &&
                        jv instanceof ObjectType == false) {
                        sb.append(jv.toString());
                    }
                    else {
                        // Don't join arrays or objects
                        continue;
                    }
                    if (i < values.size() - 1) {
                        if (this.getParam1() != null) {
                            sb.append(separator);
                        }
                        else {
                            sb.append(",");
                        }
                    }
                }
                
                result.setFromJavaString(sb.toString());
            }
            
            else {
                String arrayString = ((ArrayType) currentValue.evaluate()).toString();
                arrayString = arrayString.replaceAll("\"", "\\\\\"");
                result.setFromJavaString(arrayString);
            }
            
            return result;
        }

        else if (currentValue instanceof io.operon.runner.node.type.Path) {
            StringType result = new StringType(this.getStatement());
            String resultStr = ((io.operon.runner.node.type.Path) currentValue).toString();
            result.setValue(resultStr);
            return result;
        }

        else {
            StringType result = new StringType(this.getStatement());
            String setFromString = currentValue.toString();
            setFromString = setFromString.replaceAll("\"", "\\\\\"");
            result.setFromJavaString(setFromString);
            return result;
        }
    }

}