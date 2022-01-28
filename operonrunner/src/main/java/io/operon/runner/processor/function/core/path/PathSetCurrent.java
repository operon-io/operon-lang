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
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class PathSetCurrent extends BaseArity1 implements Node, Arity1 {
    
    public PathSetCurrent(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "setCurrent", "path");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            if (this.getParam1() == null) {
                Path path = (Path) this.getStatement().getCurrentValue();
                this.getStatement().setCurrentPath(path);
                return path;
            }
            
            else {
                Path p = (Path) this.getParam1().evaluate();
                this.getStatement().setCurrentPath(p);
                return this.getStatement().getCurrentValue();
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}