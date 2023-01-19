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

public class StringSubstring extends BaseArity2 implements Node, Arity2 {
    
    public StringSubstring(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        String param1Name = "start";
        String param2Name = "end";
        this.setParams(params, "substring", param1Name, param2Name);
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            // NOTE: for string: -functions we don't need to create copy() of currentValue
            
            //
            // NOTE: for func:2 type functions we must reset the cv between param-evaluations
            //
            Node startPos = this.getParam1().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            Node endPos = this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            int startPosition = (int) ((NumberType) startPos.evaluate()).getDoubleValue();
            int endPosition = (int) ((NumberType) endPos.evaluate()).getDoubleValue();
            
            //
            // We must create a new OperonValue for result, which has a new Stmt, so it won't change the original statement's currentValue.
            //
            StringType result = new StringType(new DefaultStatement(OperonContext.emptyContext));
            String resultString = ((StringType) currentValue.evaluate()).getJavaStringValue().substring(startPosition - 1, endPosition - 1);
            result.setFromJavaString(resultString);
            return result;
        } catch (Exception e) {
            //System.out.println(">> exception :: " + e.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }
    
    public boolean ensuredCurrentValueSetBetweenParamEvaluations() {
        return true;
    }
}