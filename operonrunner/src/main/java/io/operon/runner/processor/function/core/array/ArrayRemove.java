/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.UnaryNode;
import io.operon.runner.node.BinaryNode;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ArrayRemove extends BaseArity1 implements Node, Arity1 {

    private static Logger log = LogManager.getLogger(ArrayRemove.class);

    public ArrayRemove(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "remove", "index");
    }

    public ArrayType evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType currentValueArray = (ArrayType) currentValue.evaluate();

            if (this.getParam1() != null) {
                NumberType getIndexNumberType = (NumberType) this.getParam1().evaluate();
                int removeIndex = (int) getIndexNumberType.getDoubleValue();
                
                // Remove by removeIndex
                if (removeIndex >= 1) {
                    List<Node> arrayNodes = currentValueArray.getValues();
                    try {
                      arrayNodes.remove(removeIndex - 1);
                    } catch (Exception e) {
                        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Index out of bounds.");
                        return null;
                    }
                }
            }

            return currentValueArray;
        } catch (OperonGenericException oge) {
            throw oge;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}
