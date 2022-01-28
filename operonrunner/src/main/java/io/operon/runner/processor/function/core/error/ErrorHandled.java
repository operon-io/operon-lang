/** OPERON-LICENSE **/
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