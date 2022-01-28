/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.string;

import java.util.List;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringEndsWith extends BaseArity1 implements Node, Arity1 {
    
    public StringEndsWith(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "endsWith", "value");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            Node endsWithNode = this.getParam1().evaluate();
            String endsWithStr = ((StringType) endsWithNode).getJavaStringValue();
            
            StringType str = (StringType) currentValue.evaluate();
            String strValue = str.getJavaStringValue();
            
            if (strValue.endsWith(endsWithStr)) {
                TrueType result = new TrueType(this.getStatement());
                return result;
            }
            
            else {
                FalseType result = new FalseType(this.getStatement());
                return result;
            }
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
        }
    }
    
}