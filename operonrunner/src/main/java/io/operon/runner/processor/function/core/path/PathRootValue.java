/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.path;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class PathRootValue extends BaseArity0 implements Node, Arity0 {
    
    public PathRootValue(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("rootValue");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            Path path = (Path) this.getStatement().getCurrentValue();
            OperonValue linkedRootValue = path.getObjLink();
            OperonValue result = new EmptyType(this.getStatement());
            if (linkedRootValue != null) {
                result = linkedRootValue;
            }
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
        }
    }

}