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
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Attributes: pos, parent
//
public class ArrayGet extends BaseArity1 implements Node, Arity1, SupportsAttributes {
    private static Logger log = LogManager.getLogger(ArrayGet.class);
    
    public ArrayGet(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "get", "index");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType currentValueArray = (ArrayType) currentValue.evaluate();

            int getIndex = 1;

            if (this.getParam1() != null) {
                NumberType getIndexNumberType = (NumberType) this.getParam1().evaluate();
                getIndex = (int) getIndexNumberType.getDoubleValue();
            }

            OperonValue result = ArrayGet.baseGet(this.getStatement(), currentValueArray, getIndex);
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

    // 
    // This assigns the parent and position -attributes for the value accessed from the array.
    // Internally, when array is accessed, this should be used.
    // 
    public static OperonValue baseGet(Statement statement, ArrayType currentValueArray, int getFromIndex) throws OperonGenericException {
        int arraySize = currentValueArray.getValues().size();
        OperonValue result = null;

        if (arraySize == 0) {
            result = new EmptyType(statement);
            return result;
        }

        else if (getFromIndex == 0) {
            return new ArrayType(statement);
        }
        
        else if (getFromIndex < 0) {
            getFromIndex = arraySize + getFromIndex + 1;
            if (getFromIndex == 0) {
                return currentValueArray;
            }
        }

        Node arrayItem = currentValueArray.getValues().get(getFromIndex - 1); // could be e.g. UnaryNode, therefore do not cast as OperonValue

        if (arrayItem instanceof UnaryNode || arrayItem instanceof BinaryNode || arrayItem instanceof MultiNode) {
            result = (OperonValue) arrayItem.evaluate();
        }
        else {
            result = (OperonValue) arrayItem;
        }
        
        result = (OperonValue) result.evaluate(); // ensure that result is unboxed
        
        //
        // ATTRIBUTES
        //
        /*
        Path currentPath = (Path) statement.getCurrentPath();
        PathPart pp = new PosPathPart(getFromIndex);
        currentPath.getPathParts().add(pp);
        if (currentPath.getObjLink() == null) {
            currentPath.setObjLink(currentValueArray);
        }
        statement.setCurrentPath(currentPath);
        */
        //
        // END ATTRIBUTES
        //
        
        // TODO: this logic is redundant and should be put in one place only
        // TODO: should the above be available for FunctionRefs as well?
        if (result instanceof LambdaFunctionRef) {
            LambdaFunctionRef lfr = (LambdaFunctionRef) result;
            lfr.getStatement().getRuntimeValues().put("_", currentValueArray);
        }
        return result;
    }

}