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

public abstract class BaseArity1 extends AbstractNode implements Arity1 {
    private Node param1;
    private String functionName;
    private String param1Name;
    private boolean param1Optional = false;
    
    public BaseArity1(Statement statement) {
        super(statement);
    }
    
    public void setParam1(Node param1) {this.param1 = param1;}
    public Node getParam1() throws OperonGenericException {
        if (this.param1Optional == false && this.param1 == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "ARITY1_RESOLVE_PARAM_1", "Cannot resolve param $" + param1Name);
        }
        return this.param1;
    }
    public void setFunctionName(String fn) {this.functionName = fn;}
    public void setParam1Name(String p1n) {this.param1Name = p1n;}
    public String getFunctionName() {return this.functionName;}
    public String getParam1Name() {return this.param1Name;}
    
    //
    // optionality must be set before setting params
    //
    public void setParam1AsOptional(boolean opt) {this.param1Optional = opt;}
    public boolean isParam1Optional() {return this.param1Optional;}

    public void setParams(List<Node> args, String funcName, String p1) throws OperonGenericException {
        this.setFunctionName(funcName);
        this.setParam1Name(p1);
        
        if (args.size() < 1 && this.isParam1Optional() == false) {
            //System.out.println("Param1 optional :: " + this.isParam1Optional());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Insufficient amount of arguments. Expected 1, got 0");
        }
        //
        // Skip setting params
        //
        else if (args.size() < 1 && this.isParam1Optional() == true) {
            return;
        }
        
        // IMPORTANT: do not _evaluate_ args here (for any function)!
        //            Reason: param could be e.g. ValueRef, and in that case
        //            there's no value registered in the runtime-values.
        //            Check this pattern!
        if (args.get(0) instanceof FunctionRegularArgument) {
            // Note that params are set in reverse order:
            this.setParam1( ((FunctionRegularArgument) args.get(0)).getArgument() );
        }
        else if (args.get(0) instanceof FunctionNamedArgument) {
            // Note that params are set in reverse order:
            FunctionNamedArgument fna1 = (FunctionNamedArgument) args.get(0);
            if (fna1.getArgumentName().equals("$" + this.getParam1Name())) {
                this.setParam1(fna1.getArgumentValue());
            }
            else {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", this.getFunctionName(), "Unknown parameter: " + fna1.getArgumentName());
            }
       }
       else {
           this.setParam1(args.get(0));
       }
    }
    
}