/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.function;

import io.operon.runner.OperonContext;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Get function namespace, without function name.
 *
 */
public class FunctionNamespace extends BaseArity0 implements Arity0 {
    
    public FunctionNamespace(Statement statement) {
        super(statement);
        this.setFunctionName("namespace");
    }

    public StringType evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        StringType result = new StringType(this.getStatement());
        
        if (currentValue instanceof FunctionRef) {
            FunctionRef fRef = (FunctionRef) currentValue;
            String functionFQName = fRef.getFunctionFQName();
            String functionNamespace = FunctionNamespace.getNamepace(functionFQName);
            result.setFromJavaString(functionNamespace);
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "function:" + this.getFunctionName(), "Expected FunctionRef, but got: " + currentValue.getClass().getName());
            return null;
        }
        
        return result;
    }

    public static String getNamepace(String functionFQName) {
        // Count ":" and take the second last index, and cut the first part.
        int lastIndex = functionFQName.lastIndexOf(':');
        lastIndex = functionFQName.substring(0, lastIndex).lastIndexOf(':');
        String functionNamespace = functionFQName.substring(0, lastIndex);
        return functionNamespace;
    }

}