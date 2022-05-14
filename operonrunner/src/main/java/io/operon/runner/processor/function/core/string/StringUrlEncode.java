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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringUrlEncode extends BaseArity0 implements Node, Arity0 {
    
    public StringUrlEncode(Statement statement) {
        super(statement);
        this.setFunctionName("urlEncode");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            StringType currentValue = (StringType) this.getStatement().getCurrentValue();
            String encoded = URLEncoder.encode(currentValue.getJavaStringValue(), StandardCharsets.UTF_8.toString());
            currentValue.setFromJavaString(encoded);
            return currentValue;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }
    
}