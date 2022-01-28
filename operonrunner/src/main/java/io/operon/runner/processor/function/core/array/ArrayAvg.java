/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ArrayAvg extends BaseArity0 implements Node, Arity0 {
    private static Logger log = LogManager.getLogger(ArrayAvg.class);
    
    public ArrayAvg(Statement statement) {
        super(statement);
        this.setFunctionName("avg");
    }

    /**
     * Returns: average of the array-elements, assuming they are all numbers.
     *
     */
    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType array = (ArrayType) currentValue.evaluate();
            List<Node> arrayValues = array.getValues();
            
            double resultSum = 0.0;
            
            for (int i = 0; i < arrayValues.size(); i ++) {
                Node n = arrayValues.get(i);
                NumberType number = (NumberType) n.evaluate();
                resultSum += number.getDoubleValue();
            }
            
            double avg = resultSum / arrayValues.size();
            
            // Begin Set precision
            byte precision = NumberType.getPrecisionFromStr(String.valueOf(avg)); // TODO: does toString convert to scientific-notation?
            
            // End Set precision
            
            NumberType result = new NumberType(this.getStatement());
            result.setDoubleValue(avg);
            result.setPrecision(precision);
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

}