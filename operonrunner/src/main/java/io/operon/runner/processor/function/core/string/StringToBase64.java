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

package io.operon.runner.processor.function.core.string;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringToBase64 extends BaseArity1 implements Node, Arity1 {
    
    public StringToBase64(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "toBase64", "options");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StringType str = (StringType) currentValue.evaluate();
            
            if (this.getParam1() != null) {
                Info info = this.resolve(currentValue);
                if (info.encoder == Base64Encoder.URLSAFE) {
                    str.setFromJavaString(RawValue.bytesToBase64UrlSafe(str.getJavaStringValue().getBytes(), info.padding));
                    return str;
                }
                // TODO: mime-encoder
                else {
                    str.setFromJavaString(RawValue.bytesToBase64(str.getJavaStringValue().getBytes(), info.padding));
                    return str;
                }
            }
            
            else {
                str.setFromJavaString(RawValue.bytesToBase64(str.getJavaStringValue().getBytes(), true));
                return str;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }

    public Info resolve(OperonValue currentValue) throws Exception, OperonGenericException {
        List<PairType> jsonPairs = ((ObjectType) this.getParam1().evaluate()).getPairs();

        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            pair.getStatement().setCurrentValue(currentValue);
            switch (key.toLowerCase()) {
                case "\"encoder\"":
                    String encoderStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.encoder = Base64Encoder.valueOf(encoderStr.toUpperCase());
                    break;
                case "\"padding\"":
                    Node padding_Node = pair.getValue().evaluate();
                    if (padding_Node instanceof TrueType) {
                        info.padding = true;
                    }
                    else {
                        info.padding = false;
                    }
                    break;
                default:
                    throw new Exception("unknown option: " + key);
            }
        }
        
        return info;
    }

    private class Info {
        private Base64Encoder encoder = Base64Encoder.BASIC;
        private boolean padding = true;
    }
    
    private enum Base64Encoder {
        BASIC, URLSAFE, MIME;
    }
}