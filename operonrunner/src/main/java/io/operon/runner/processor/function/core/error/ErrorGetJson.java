/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.error;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ErrorGetJson extends BaseArity0 implements Node, Arity0 {
    
    public ErrorGetJson(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("getJson");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            ErrorValue currentValue = (ErrorValue) this.getStatement().getCurrentValue();
            OperonValue result = currentValue.getErrorJson();
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "error:" + this.getFunctionName(), e.getMessage());
        }
    }

    public String toString() {
        return "error:" + this.getFunctionName();
    }
}