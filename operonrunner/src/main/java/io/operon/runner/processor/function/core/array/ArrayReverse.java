/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

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

public class ArrayReverse extends BaseArity0 implements Node, Arity0 {
    private static Logger log = LogManager.getLogger(ArrayReverse.class);
    
    public ArrayReverse(Statement statement) {
        super(statement);
        this.setFunctionName("reverse");
    }

    /**
     * Returns: reversed array-elements
     *
     */
    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType resultArray = (ArrayType) currentValue.evaluate();
            
            List<Node> arrayValues = resultArray.getValues();
            Collections.reverse(arrayValues);
            
            resultArray.setValue(resultArray);
            return resultArray;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}