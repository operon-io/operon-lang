/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ArrayFirst extends BaseArity1 implements Node, Arity1 {
    
    public ArrayFirst(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "first", "count");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ArrayType currentValueArray = (ArrayType) currentValue.evaluate();
            
            int indexCount = 1;
            
            if (this.getParam1() != null) {
                NumberType indexCountNumberType = (NumberType) this.getParam1().evaluate();
                indexCount = (int) indexCountNumberType.getDoubleValue();
            }
            
            int arraySize = currentValueArray.getValues().size();

            OperonValue result = null;
            
            if (arraySize == 0) {
                result = new EmptyType(this.getStatement());
                return result;
            }
            
            // Pull from array
            if (indexCount == 1) {
                result = (OperonValue) ArrayGet.baseGet(this.getStatement(), currentValueArray, 1);
            }
            
            // Result is array
            else {
                ArrayType resultArray = new ArrayType(this.getStatement());
                int endIndex = indexCount;
                if (endIndex > arraySize) {
                    endIndex = arraySize;
                }
                for (int i = 0; i < endIndex; i ++) {
                    OperonValue pulledFromArray = (OperonValue) ArrayGet.baseGet(this.getStatement(), currentValueArray, i + 1);
                    resultArray.addValue(pulledFromArray);
                }                
                result = resultArray;
            }
            
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

}