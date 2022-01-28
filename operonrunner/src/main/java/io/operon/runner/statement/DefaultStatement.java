/** OPERON-LICENSE **/
package io.operon.runner.statement;

import java.util.Map;
import java.util.HashMap;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.OperonValueConstraint;
import io.operon.runner.ExceptionHandler;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class DefaultStatement extends BaseStatement implements Statement {
    private static Logger log = LogManager.getLogger(DefaultStatement.class);
    
    public DefaultStatement(Context ctx) {
        super(ctx);
        this.setId("DefaultStatement");
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        //System.out.println("DefaultStatement evaluate, stmt id=" + this.getId());
        log.debug("Default-statement :: evaluate()");
        OperonValue result = this.getNode().evaluate();
        this.synchronizeState();
        this.setEvaluatedValue(result);
        
        if (this.getOperonValueConstraint() != null) {
            // Apply constraint-check:
            OperonValueConstraint c = this.getOperonValueConstraint();
            OperonValueConstraint.evaluateConstraintAgainstOperonValue(this.getEvaluatedValue(), c);
        }
        log.debug("Default statement return :: " + this.getEvaluatedValue());
        return this.getEvaluatedValue();
    }

    private void synchronizeState() {
        for (LetStatement lstmnt : this.getLetStatements().values()) {
            if (lstmnt.getResetType() == LetStatement.ResetType.AFTER_SCOPE) {
                lstmnt.reset();
            }
        }
    }

    @Override
    public String toString() {
        return this.getId();
    }
}