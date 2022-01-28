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

import java.util.List;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.processor.function.core.string.StringToRaw;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// NOTE: this does not use regular expressions.
//
public class StringReplaceAll extends BaseArity2 implements Node, Arity2 {
    
    public StringReplaceAll(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "replaceAll", "search", "replace");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            StringType searchNode = (StringType) this.getParam1().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            StringType replaceNode = (StringType) this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            
            String searchStr = searchNode.getJavaStringValue();
            //searchStr = StringToRaw.unescapeString(searchStr);
            //System.out.println("SEARCH: " + searchStr);
            String replaceStr = replaceNode.getJavaStringValue();
            replaceStr = StringToRaw.unescapeString(replaceStr);
            
            String resultString = ((StringType) currentValue.evaluate()).getJavaStringValue();
            resultString = resultString.replace(searchStr, replaceStr); // replace: replaces all.

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