/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.string;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringRepeat extends BaseArity1 implements Node, Arity1 {
    
    public StringRepeat(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "repeat", "count");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StringType cvJsStr = (StringType) currentValue.evaluate();

            Node countNode = this.getParam1().evaluate();
            int countInt = (int) ((NumberType) countNode).getDoubleValue();
            String strValue = cvJsStr.getJavaStringValue();
            
            strValue = strValue.repeat(countInt);
            cvJsStr.setFromJavaString(strValue);
            return cvJsStr;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }

}