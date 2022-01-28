/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.Map;
import java.util.HashMap;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class LambdaFunctionCall extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(LambdaFunctionCall.class);
    
    private Map<String, Node> params;
    private Node functionBodyExpr;
    
    public LambdaFunctionCall(Statement stmnt) {
        super(stmnt);
        this.params = new HashMap<String, Node>();
    }

    public void setFunctionBodyExpr(Node node) {
        this.functionBodyExpr = node;
    }
    
    public Node getFunctionBodyExpr() {
        return this.functionBodyExpr;
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER LambdaFunctionCall.evaluate()");
        
        OperonValue currentValue = null;
        //
        // At this point the CV is already wrong (for both: stmt and prevStmt):
        //  --> copying the expr does not help in this case.
        // Prototype is correct.
        //System.out.println("LFC: isPrototype:" + this.getStatement().isPrototype());
        
        //
        // Get current value from the previous statement (scope)
        //
        log.debug("    LambdaFunctionCall :: >> Current-stmt: " + this.getStatement().getId());
        log.debug("    LambdaFunctionCall :: >> Accessing previous-statement: ");
        
        Statement previousStatement = this.getStatement().getPreviousStatement();
        // DO not log these (serialization for recursive values causes problems)
        //log.debug("        LambdaFunctionCall :: >> Prev-stmt: " + previousStatement.getId());
        //log.debug("          LambdaFunctionCall :: Runtimevalues (previousStatement) :: " + previousStatement.getRuntimeValues());
        
        // Comment this after testing:
        //log.debug("        LambdaFunctionCall :: >> Prev-stmt current-value: " + previousStatement.getCurrentValue());
        // BUG: previous stmt currentValue might be null, if Let-stmt
        OperonValue previousStatementCurrentValue = previousStatement.getCurrentValue();
        
        // This could be e.g. if Let-stmt has lambdaFunctionRef, the lfr sees Let-stmt's currentValue as null.
        if (previousStatementCurrentValue == null) {
            currentValue = new EmptyType(this.getStatement());
        }
        else {
            currentValue = previousStatementCurrentValue;
            //System.out.println("LFC: GOT CV (from previous-stmt) :: " + currentValue);
        }
        // set current value for function
        this.getStatement().getRuntimeValues().put("@", currentValue);
        
        //log.debug("     LambdaFunctionCall :: Runtimevalues (before) :: " + this.getStatement().getRuntimeValues());
        
        //this.getStatement().setCurrentValue(currentValue); // Added while fixing LambdaFunctionRefTests
        
        log.debug("    LambdaFunctionCall :: >> Copied current-value.");
        FunctionStatement functionStatement = (FunctionStatement) this.getStatement();
        functionStatement.setNode(this.getFunctionBodyExpr());
        for (Map.Entry<String, Node> param : this.getParams().entrySet()) {
            //log.debug("  LambdaFunctionCall :: Param map :: " + param.getKey());
            Node paramValue = param.getValue();
            while (!(paramValue instanceof OperonValue)) {
                //log.debug("  LambdaFunctionCall :: Unbox param-value :: " + paramValue.getClass().getName());
                try {
                    paramValue = paramValue.evaluate();
                } catch (NullPointerException npe) {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "LAMBDA_FUNCTION_CALL", "ARGUMENTS", "missing argument for " + param.getKey());
                }
                //log.debug("  LambdaFunctionCall :: Unboxing param-value done");
            }
            //if (paramValue == null) {
            //    log.debug("LambdaFunctionCall :: PARAM VALUE WAS NULL!!!");
            //}
            
            //log.debug("ACCESSING PARAM :: " + param.getKey() + " -> " + param.getValue());
            //System.out.println("SET PARAM: " + param.getKey() + " :: " + (OperonValue) paramValue);
            this.getStatement().getRuntimeValues().put(param.getKey(), (OperonValue) paramValue);
            
            this.getStatement().setCurrentValue(currentValue); // Set back the current-value, so previous evaluation won't change it for next argument.
            log.debug("     LambdaFunctionCall :: REMOVE :: Current value in the loop :: " + currentValue);
        }
        //log.debug("    >> Evaluate body expr. Current value :: " + currentValue);
        
        //System.out.println("FS RT :: " + functionStatement.getRuntimeValues());
        OperonValue result = functionStatement.evaluate();
        
        this.getStatement().setCurrentValue(result);
        this.getStatement().getPreviousStatement().setCurrentValue(result);
        log.debug("     LambdaFunctionCall :: Runtimevalues (after, stmt :: " + this.getStatement().getId() + ") :: " + this.getStatement().getRuntimeValues());
        log.debug("     LambdaFunctionCall :: evaluatedValue :: " + this.getEvaluatedValue());

        return result;
    }
    
    public void setParams(Map<String, Node> params) {
        this.params = params;
    }
    
    public Map<String, Node> getParams() {
        return this.params;
    }
    
    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}