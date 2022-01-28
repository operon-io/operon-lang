/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.UnaryNode;
import io.operon.runner.node.BinaryNode;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ArrayInsertBefore extends BaseArity2 implements Node, Arity2, SupportsAttributes {

    private static Logger log = LogManager.getLogger(ArrayInsertBefore.class);

    public ArrayInsertBefore(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "insertBefore", "value", "position");
    }

    public ArrayType evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            NumberType evaluatedParam2 = (NumberType) this.getParam2().evaluate();
            ArrayType result = (ArrayType) currentValue.evaluate();
            
            List<Node> resultValues = result.getValues();
            int pos = (int) evaluatedParam2.getDoubleValue();
            OperonValue valueToAdd = this.getParam1().evaluate();
            resultValues.add(pos - 1, valueToAdd);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}
