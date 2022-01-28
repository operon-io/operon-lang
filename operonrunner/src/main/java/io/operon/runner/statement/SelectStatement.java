/** OPERON-LICENSE **/
package io.operon.runner.statement;

import java.util.Map;
import java.util.HashMap;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.ExceptionHandler;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.BreakSelect;

import org.apache.logging.log4j.LogManager;

public class SelectStatement extends BaseStatement implements Statement {
    private static Logger log = LogManager.getLogger(DefaultStatement.class);
    
    private Node configs;
    private boolean configsResolved = false;
    
    public SelectStatement(Context ctx) {
        super(ctx);
        this.setId("SelectStatement");
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        log.debug("Select-statement :: evaluate()");
        OperonValue result = null;
        try {
            //
            // Eager-evaluate Let-statements:
            //
            for (Map.Entry<String, LetStatement> entry : this.getLetStatements().entrySet()) {
    		    LetStatement letStatement = (LetStatement) entry.getValue();
    		    letStatement.resolveConfigs();
    		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
    		        letStatement.evaluate();
    		    }
    	    }
    	    // 
    	    // Evaluate the statement
    	    // 
            result = this.getNode().evaluate();
        } 
        catch (BreakSelect bs) {
            //System.out.println("Caught BreakSelect");
            throw bs;
        }
        catch (OperonGenericException e) {
            //System.out.println("Caught Exception: " + e);
            if (this.getExceptionHandler() != null) {
                //
                // Apply ExceptionHandler:
                //
                
                //System.out.println("SelectStatement :: apply ExceptionHandler: " + e.getMessage());
                //System.out.println("  CV: " + e.getCurrentValue());
                log.debug("SelectStatement :: apply ExceptionHandler: " + e.getMessage());
                ExceptionHandler eh = this.getExceptionHandler();
                eh.setException(e);
                result = eh.evaluate(e);
                log.debug("SelectStatement :: applied ExceptionHandler");
            }
            else {
                log.debug("SelectStatement :: exceptionHandler missing, throw to upper-level");
                //System.out.println("error is null? " + e);
                log.debug("  >> error: " + e.getClass().getName() + ", message: " + e.getMessage());
                //
                // Throw the exception to upper-level, since no ExceptionHandler was found:
                //
                throw e;
            }
        }
        this.setEvaluatedValue(result);
        
        if (this.getOperonValueConstraint() != null) {
            // Apply constraint-check:
            OperonValueConstraint c = this.getOperonValueConstraint();
            OperonValueConstraint.evaluateConstraintAgainstOperonValue(this.getEvaluatedValue(), c);
        }
        log.debug("Select statement return :: " + this.getEvaluatedValue());
        return this.getEvaluatedValue();
    }

    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this);
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

    public void resolveConfigs() throws OperonGenericException {
        log.debug("Select, resolveConfigs");
        ObjectType conf = this.getConfigs();
        if (conf == null) {
            return;
        }
        for (PairType pair : conf.getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"prettyprint\"":
                    break;
                default:
                    break;
            }
        }
        this.configsResolved = true;
        // resolving configs changes the currentValue, therefore we set it back to null,
        // assuming that resolveConfigs is done prior to evalute() -call.
        this.setCurrentValue(null);
    }

}