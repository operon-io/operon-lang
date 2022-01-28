/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.string;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringToCodePoints extends BaseArity0 implements Node, Arity0 {
    
    public StringToCodePoints(Statement statement) {
        super(statement);
        this.setFunctionName("toCodePoints");
    }

    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StringType str = (StringType) currentValue.evaluate();
            
            String strValue = str.getJavaStringValue();

            ArrayType result = new ArrayType(this.getStatement());
            for (int i : strValue.codePoints().toArray()) {
                NumberType cp = new NumberType(this.getStatement());
                cp.setDoubleValue((double) i);
                result.getValues().add(cp);
            }
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }

}