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
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.core.string.StringToRaw;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Cast value to Raw
 *
 */
public class CastRaw extends BaseArity1 implements Node, Arity1 {
    private static Logger log = LogManager.getLogger(CastRaw.class);
    
    public CastRaw(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "raw", "options");
    }

    public RawValue evaluate() throws OperonGenericException {
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();

        if (currentValue instanceof RawValue) {
            return (RawValue) currentValue;
        }

        else if (currentValue instanceof StringType) {
            StringType str = (StringType) currentValue;
            String strValue = str.getJavaStringValue();
            RawValue result = new RawValue(this.getStatement());
            boolean unescape = true;
            if (this.getParam1() != null) {
                ObjectType options = ((ObjectType) this.getParam1().evaluate());
                if (options.hasKey("\"unescape\"")) {
                    OperonValue unescapeValue = (OperonValue) options.getByKey("unescape").evaluate();
                    if (unescapeValue instanceof FalseType) {
                        unescape = false;
                    }
                }
            }
            result.setValue(StringToRaw.stringToBytes(strValue, unescape));
            return result;
        }

        else if (currentValue instanceof ArrayType) {
            RawValue result = new RawValue(this.getStatement());
            List<Node> values = ((ArrayType) currentValue.evaluate()).getValues();
            
            StringBuilder sb = new StringBuilder();
            ObjectType options = null;
            String separator = "";
            
            if (this.getParam1() != null) {
                options = ((ObjectType) this.getParam1().evaluate());
                if (options.hasKey("\"separator\"")) {
                    separator = ((StringType) options.getByKey("separator")).getJavaStringValue();
                }
            }

            for (int i = 0; i < values.size(); i ++) {
                OperonValue jv = (OperonValue) values.get(i).evaluate();
                if (jv instanceof StringType) {
                    StringType s = (StringType) jv;
                    sb.append(s.getJavaStringValue());
                }
                else if (jv instanceof RawValue) {
                    RawValue raw = (RawValue) jv;
                    sb.append(new String(raw.getBytes()));
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
            
            result.setValue(sb.toString().getBytes());
            return result;
        }

        else if (currentValue instanceof LambdaFunctionRef) {
            LambdaFunctionRef lfr = (LambdaFunctionRef) currentValue;
            String lfrExpr = lfr.getExpr();
            RawValue result = new RawValue(this.getStatement());
            boolean unescape = false;
            result.setValue(StringToRaw.stringToBytes(lfrExpr, unescape));
            return result;
        }

        else {
            String strValue = currentValue.toString();
            RawValue result = new RawValue(this.getStatement());
            result.setValue(StringToRaw.stringToBytes(strValue, true));
            return result;
        }
    }

}