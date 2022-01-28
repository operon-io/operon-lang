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

public class PathCurrent extends BaseArity0 implements Node, Arity0 {
    
    public PathCurrent(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("current");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            //System.out.println("path:current() evaluate");
            Path currentPath = (Path) this.getStatement().getCurrentPath();
            //System.out.println("path:current() :: " + currentPath);
            
            // Create a copy, ref ArrayForEachPairTests#arrayForEachExprNext_Test
            Path result = currentPath.copy();
            result.setObjLink(currentPath.getObjLink());
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}