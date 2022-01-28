/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.error;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Create an Error
 *
 */
public class ErrorCreate extends BaseArity1 implements Node, Arity1 {
    
    public ErrorCreate(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "create", "options");
    }

    public ErrorValue evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        
        ErrorValue result = null;
        
        String errorCode = "";
        String errorType = "";
        String errorMessage = "";
        OperonValue errorJson = new EmptyType(this.getStatement());
        
        if (this.getParam1().evaluate() instanceof ObjectType) {
             ObjectType errorDefinition = (ObjectType) this.getParam1().evaluate();
             
             //
             // TODO: just write one loop:
             //
             try {
                 errorCode = ((StringType) errorDefinition.getByKey("code").evaluate()).getJavaStringValue();
             } catch (OperonGenericException oge) {}
             try {
                 errorType = ((StringType) errorDefinition.getByKey("type").evaluate()).getJavaStringValue();
             } catch (OperonGenericException oge) {}
             try {
                 errorMessage = ((StringType) errorDefinition.getByKey("message").evaluate()).getJavaStringValue();
             } catch (OperonGenericException oge) {}
             try {
                 errorJson = errorDefinition.getByKey("json").evaluate();
             } catch (OperonGenericException oge) {}
             result = ErrorUtil.createErrorValue(this.getStatement(), errorType, errorCode, errorMessage, errorJson);
        }
        
        else {
            result = ErrorUtil.createErrorValue(this.getStatement(), errorType, errorCode, errorMessage, errorJson);
        }
        
        return result;
    }

}