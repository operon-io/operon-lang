/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectValues extends BaseArity0 implements Node, Arity0 {
    
    public ObjectValues(Statement statement) {
        super(statement);
        this.setFunctionName("values");
    }

    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType currentValueObj = (ObjectType) currentValue.evaluate();
            
            ArrayType result = new ArrayType(this.getStatement());
            for (int i = 0; i < currentValueObj.getPairs().size(); i ++) {
                result.getValues().add(currentValueObj.getPairs().get(i).getValue());
            }
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}