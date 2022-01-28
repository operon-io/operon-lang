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

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringPadRight extends BaseArity2 implements Node, Arity2 {
    
    public StringPadRight(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        String param1Name = "padWith";
        String param2Name = "padToLength";
        this.setParams(params, "padRight", param1Name, param2Name);
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            //
            // NOTE: for func:2 type functions we must reset the cv between param-evaluations
            //
            StringType padWithNode = (StringType) this.getParam1().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            NumberType padToLengthNode = (NumberType) this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            
            String padWithStr = padWithNode.getJavaStringValue();
            if (padWithStr == null) {
                padWithStr = "";
            }
            int padToLengthInt = (int) padToLengthNode.getDoubleValue();
            if (padToLengthInt < 0) {
                padToLengthInt = 0;
            }

            StringType cvJStr = (StringType) currentValue.evaluate();
            String resultString = cvJStr.getJavaStringValue();
            
            if (resultString.length() < padToLengthInt) {
                int padAmount = padToLengthInt - resultString.length();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < padAmount; i ++) {
                    sb.append(padWithStr);
                }
                String padding = sb.toString();
                if ( padding.length() > padAmount ) {
                    padding = padding.substring(0, padAmount);
                }
                resultString = resultString + padding;
            }
            
            StringType result = new StringType(new DefaultStatement(OperonContext.emptyContext));
            result.setFromJavaString(resultString);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }
    
    public boolean ensuredCurrentValueSetBetweenParamEvaluations() {
        return true;
    }
}