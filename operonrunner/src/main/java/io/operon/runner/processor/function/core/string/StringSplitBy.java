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

import java.util.List;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/*
 * Splits string by given string.
 *
 **/
public class StringSplitBy extends BaseArity1 implements Node, Arity1 {
    
    public StringSplitBy(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "splitBy", "value");
        this.setNs(Namespaces.STRING);
    }

    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            StringType split = (StringType) this.getParam1().evaluate();
            String splitByStr = split.getJavaStringValue();
            
            StringType str = (StringType) currentValue.evaluate();
            String strValue = str.getJavaStringValue();
            ArrayType result = new ArrayType(this.getStatement());
            
            int startIndex = 0;
            
            while (startIndex >= 0) {
                startIndex = strValue.indexOf(splitByStr);
                
                if (startIndex >= 0) {
                    String strPart = strValue.substring(0, startIndex);
                    strValue = strValue.substring(startIndex + splitByStr.length(), strValue.length());
                    StringType resultStringPart = new StringType(this.getStatement());
                    resultStringPart.setFromJavaString(strPart);
                    result.addValue(resultStringPart);
                }
                
                else {
                    startIndex = -1;
                }
            }
            
            // Last part:
            StringType resultStringPart = new StringType(this.getStatement());
            resultStringPart.setFromJavaString(strValue);
            result.addValue(resultStringPart);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }
    
}