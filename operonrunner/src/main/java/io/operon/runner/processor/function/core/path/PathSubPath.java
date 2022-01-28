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

public class PathSubPath extends BaseArity1 implements Node, Arity1 {
    
    public PathSubPath(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "subPath", "length");
    }

    public Path evaluate() throws OperonGenericException {
        try {
            Path path = (Path) this.getStatement().getCurrentValue();
            NumberType pathLength = (NumberType) this.getParam1().evaluate();
            int len = (int) pathLength.getDoubleValue();
            Path resultPath = PathSubPath.subPath(path, len);
            return resultPath;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static Path subPath(Path path, int len) {
        Path resultPath = new Path(path.getStatement());
        //assert(len <= path.getPathParts().size());
        
        int size = path.getPathParts().size();
        
        if (len >= 0) {
            int target  = size;
            if (len < target) {
                target = len;
            }
            for (int i = 0; i < target; i ++) {
                PathPart pp = path.getPathParts().get(i).copy();
                resultPath.getPathParts().add(pp);
            }
        }
        else {
            int target = size + len;
            for (int i = 0; i < target; i ++) {
                PathPart pp = path.getPathParts().get(i).copy();
                resultPath.getPathParts().add(pp);
            }
        }

        resultPath.setPathParts(resultPath.getPathParts());
        //System.out.println(resultPath);
        return resultPath;
    }

}