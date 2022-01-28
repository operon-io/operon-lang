/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.raw;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class RawToJson extends BaseArity0 implements Node, Arity0 {
    
    public RawToJson(Statement statement) {
        super(statement);
        this.setFunctionName("toJson");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            RawValue raw = (RawValue) currentValue.evaluate();
            
            //System.out.println("RawValue=" + raw.getBytes());
            
            OperonValue result = JsonUtil.operonValueFromString(new String(raw.getBytes()));
            
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw:" + this.getFunctionName(), e.getMessage());
        }
    }

}