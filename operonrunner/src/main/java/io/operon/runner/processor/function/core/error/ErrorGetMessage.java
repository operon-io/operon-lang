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

public class ErrorGetMessage extends BaseArity0 implements Node, Arity0 {
    
    public ErrorGetMessage(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("getMessage");
    }

    public StringType evaluate() throws OperonGenericException {
        try {
            ErrorValue currentValue = (ErrorValue) this.getStatement().getCurrentValue();
            StringType result = new StringType(this.getStatement());
            result.setFromJavaString(currentValue.getMessage());
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "error:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}