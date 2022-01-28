/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectHasKey extends BaseArity1 implements Node, Arity1 {
    
    public ObjectHasKey(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "hasKey", "key");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            Node keyNode = this.getParam1().evaluate();
            String key = ((StringType) keyNode).getJavaStringValue();
            
            ObjectType obj = (ObjectType) currentValue.evaluate();
            boolean hasKey = obj.hasKey("\"" + key + "\"");
            
            if (hasKey) {
                TrueType result = new TrueType(this.getStatement());
                return result;
            }
            
            else {
                FalseType result = new FalseType(this.getStatement());
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}