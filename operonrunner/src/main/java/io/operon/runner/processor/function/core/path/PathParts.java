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

public class PathParts extends BaseArity0 implements Node, Arity0 {
    
    public PathParts(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("parts");
    }

    public ArrayType evaluate() throws OperonGenericException {
        try {
            Path path = (Path) this.getStatement().getCurrentValue();
            ArrayType result = PathParts.getPathParts(path);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static ArrayType getPathParts(Path path) {
        ArrayType result = new ArrayType(path.getStatement());
        for (int i = 0; i < path.getPathParts().size(); i ++) {
            PathPart pp = path.getPathParts().get(i).copy();
            if (pp instanceof KeyPathPart) {
                StringType jStr = new StringType(path.getStatement());
                jStr.setFromJavaString(pp.toString().substring(1, pp.toString().length()));
                result.getValues().add(jStr);
            }
            else {
                NumberType n = new NumberType(path.getStatement());
                double val = (Double.valueOf(pp.toString().substring(1, pp.toString().length() - 1))).doubleValue();
                n.setDoubleValue(val);
                n.setPrecision((byte) 0);
                result.getValues().add(n);
            }
        }
        return result;
    }

}