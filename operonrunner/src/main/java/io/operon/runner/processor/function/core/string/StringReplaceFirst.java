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
import io.operon.runner.node.type.*;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringReplaceFirst extends BaseArity2 implements Node, Arity2 {
    
    public StringReplaceFirst(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "replaceFirst", "search", "replace");
        this.setNs(Namespaces.STRING);
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            StringType searchNode = (StringType) this.getParam1().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            StringType replaceNode = (StringType) this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            
            String searchStr = searchNode.getJavaStringValue();
            String replaceStr = replaceNode.getJavaStringValue();
            
            String resultString = ((StringType) currentValue.evaluate()).getJavaStringValue();

            int index = resultString.indexOf(searchStr);
            if (index > -1) {
                String part1 = resultString.substring(0, index);
                String part2 = resultString.substring(index + searchStr.length(), resultString.length());
                resultString = part1 + replaceStr + part2;
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