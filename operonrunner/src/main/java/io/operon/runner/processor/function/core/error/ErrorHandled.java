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

package io.operon.runner.processor.function.core.error;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ErrorHandled extends BaseArity1 implements Node, Arity1 {
    
    public ErrorHandled(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "handled", "handled");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            Node isHandledNode = this.getParam1().evaluate();
            boolean isHandled = false;
            if (isHandledNode instanceof TrueType) {
                isHandled = true;
                // Clear the exception:
                this.getStatement().getOperonContext().setErrorValue(null);
                this.getStatement().getOperonContext().setException(null);
            }
            this.getStatement().setErrorHandled(isHandled);
            return currentValue;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "error:" + this.getFunctionName(), e.getMessage());
        }
    }

}