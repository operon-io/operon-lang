/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.core.path.PathCreate;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ArrayToPath extends BaseArity0 implements Node, Arity0 {
    
    public ArrayToPath(Statement statement) {
        super(statement);
        this.setFunctionName("toPath");
    }

    public Path evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType array = (ArrayType) currentValue.evaluate();
            
            Path resultPath = new Path(this.getStatement());
            
            StringBuilder sb = new StringBuilder();
            
            for (int i = 0; i < array.getValues().size(); i ++) {
                Node value = array.getValues().get(i);
                value = (OperonValue) value.evaluate();
                if (value instanceof NumberType) {
                    int intValue = (int) ((NumberType) value).getDoubleValue();
                    sb.append("[" + intValue + "]");
                }
                else if (value instanceof StringType) {
                    sb.append("." + ((StringType) value).getJavaStringValue());
                }
            }
            
            List<PathPart> pathParts = PathCreate.constructPathParts(sb.toString());
            resultPath.setPathParts(pathParts);
            return resultPath;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}