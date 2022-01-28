/** OPERON-LICENSE **/
package io.operon.runner.processor.function;

import java.util.List;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionNamedArgument;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public abstract class BaseArity0 extends AbstractNode implements Arity0 {
    private String functionName;
    
    public BaseArity0(Statement statement) {
        super(statement);
    }
    
    public void setFunctionName(String fn) {this.functionName = fn;}
    public String getFunctionName() {return this.functionName;}
   
}