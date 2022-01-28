/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.math;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.Random;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class MathCeil extends BaseArity0 implements Node, Arity0 {

    public MathCeil(Statement statement) {
        super(statement);
        this.setFunctionName("ceil");
    }

    public NumberType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            if (currentValue instanceof NumberType == false) {
                currentValue = (OperonValue) currentValue.evaluate();
            }
            double ceilValue = Math.ceil( ((NumberType) currentValue).getDoubleValue() );
            NumberType result = new NumberType(this.getStatement());
            
            // Begin Set precision
            result.setPrecision((byte) 0);
            // End Set precision
            
            result.setDoubleValue(ceilValue);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "math:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}