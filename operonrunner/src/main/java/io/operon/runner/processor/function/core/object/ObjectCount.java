/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectCount extends BaseArity0 implements Node, Arity0 {
    
    public ObjectCount(Statement statement) {
        super(statement);
        this.setFunctionName("count");
    }

    public NumberType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType obj = (ObjectType) currentValue.evaluate();
            
            int resultCount = obj.getPairs().size();

            NumberType result = new NumberType(this.getStatement());
            result.setDoubleValue((double) resultCount);
            result.setPrecision((byte) 0);
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}