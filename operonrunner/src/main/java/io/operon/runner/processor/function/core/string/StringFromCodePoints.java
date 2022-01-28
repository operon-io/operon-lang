/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.string;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringFromCodePoints extends BaseArity0 implements Node, Arity0 {
    
    public StringFromCodePoints(Statement statement) {
        super(statement);
        this.setFunctionName("fromCodePoints");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType codePoints = (ArrayType) currentValue.evaluate();
            
            List<Node> codePointsList = codePoints.getValues();

            StringType result = new StringType(this.getStatement());
            
            List<Integer> codePointsArray = new ArrayList<Integer>();
            
            for (Node n : codePointsList) {
                NumberType num = (NumberType) n.evaluate();
                codePointsArray.add((int) num.getDoubleValue());
            }
            
            int[] cpArray = codePointsArray.stream().mapToInt(i -> i).toArray();
            String resultStr = new String(cpArray, 0, cpArray.length);
            resultStr = resultStr.replaceAll("\"", "\\\\\""); // sanitize-string
            result.setFromJavaString(resultStr);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }

}