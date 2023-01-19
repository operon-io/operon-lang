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
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Decodes Base64-string
//
public class StringFromBase64 extends BaseArity1 implements Node, Arity1 {
    
    public StringFromBase64(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "fromBase64", "options");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            //
            // Encoded string as Base64
            //
            StringType result = (StringType) currentValue.evaluate();
            
            if (this.getParam1() != null) {
                Info info = this.resolve(currentValue);
                if (info.decoder == Base64Decoder.URLSAFE) {
                    String sanitizedStr = RawToStringType.sanitizeForStringType(new String(RawValue.base64UrlSafeToBytes(result.getJavaStringValue().getBytes())));
                    result.setFromJavaString(sanitizedStr);
                    return result;
                }
                // TODO: mime-encoder
                else {
                    String sanitizedStr = RawToStringType.sanitizeForStringType(new String(RawValue.base64ToBytes(result.getJavaStringValue().getBytes())));
                    result.setFromJavaString(sanitizedStr);
                    return result;
                }
            }
            
            else {
                String sanitizedStr = RawToStringType.sanitizeForStringType(new String(RawValue.base64ToBytes(result.getJavaStringValue().getBytes())));
                result.setFromJavaString(sanitizedStr);
                return result;
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
                case "\"decoder\"":
                    String decoderStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.decoder = Base64Decoder.valueOf(decoderStr.toUpperCase());
                    break;
                default:
                    throw new Exception("unknown option: " + key);
            }
        }
        
        return info;
    }

    private class Info {
        private Base64Decoder decoder = Base64Decoder.BASIC;
    }
    
    private enum Base64Decoder {
        BASIC, URLSAFE, MIME;
    }
}