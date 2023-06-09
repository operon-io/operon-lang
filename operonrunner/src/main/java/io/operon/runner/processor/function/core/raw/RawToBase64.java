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

package io.operon.runner.processor.function.core.raw;

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
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class RawToBase64 extends BaseArity1 implements Node, Arity1 {
    
    public RawToBase64(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "toBase64", "options");
        this.setNs(Namespaces.RAW);
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            RawValue raw = (RawValue) currentValue.evaluate();
            
            if (this.getParam1() != null) {
                Info info = this.resolve(currentValue);
                if (info.encoder == Base64Encoder.URLSAFE) {
                    StringType result = new StringType(this.getStatement());
                    result.setFromJavaString(RawValue.bytesToBase64UrlSafe(raw.getBytes(), info.padding));
                    return result;
                }
                // TODO: mime-encoder
                else {
                    StringType result = new StringType(this.getStatement());
                    result.setFromJavaString(RawValue.bytesToBase64(raw.getBytes(), info.padding));
                    return result;
                }
            }
            
            else {
                StringType result = new StringType(this.getStatement());
                result.setFromJavaString(RawValue.bytesToBase64(raw.getBytes(), true));
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw", e.getMessage());
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