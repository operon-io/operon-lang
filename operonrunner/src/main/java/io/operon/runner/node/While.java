/** OPERON-LICENSE **/
package io.operon.runner.node; 
 
import java.util.List; 
import java.util.ArrayList; 
import java.util.stream.Collectors;
import java.io.IOException;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.model.exception.BreakLoopException;
import io.operon.runner.model.exception.ContinueLoopException;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.ModuleContext;
import io.operon.runner.processor.BinaryNodeProcessor; 
import io.operon.runner.statement.Statement; 
import io.operon.runner.statement.LetStatement; 
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
/** 
 *  While (expr) ':' expr END
 *  Example: 0 While (@ &lt; 10): @ + 1; #&gt; 10
 *  
 */ 
public class While extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(While.class); 

    private Node whileExpr;
    private Node predicateExpr; // While (predicateExpr): whileExpr;
    private Node configs;

    public While(Statement stmnt) {
        super(stmnt);
        //this.evaluated = false;
    }

    public OperonValue evaluate() throws OperonGenericException {
        assert (this.getWhileExpr() != null) : "While.evaluate() : whileExpr was null";
        
        OperonValue currentValue = null;
        currentValue = this.getStatement().getPreviousStatement().getCurrentValue();

        Info info = this.resolveConfigs();

        OperonValue result = currentValue;
        
        Node wExpr = this.getWhileExpr();
        
        while (this.evaluatePredicate(result)) {
            wExpr.getStatement().setCurrentValue(result);
            this.eagerEvaluateLetStatements();
            try {
                result = wExpr.evaluate();
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
            this.synchronizeState();
        }

        this.getStatement().getPreviousStatement().setCurrentValue(result);
        //System.out.println(">> return result");
        
        return result;
    }

    private boolean evaluatePredicate(OperonValue result) throws OperonGenericException {
        this.getPredicateExpr().getStatement().setCurrentValue(result);
        OperonValue predicateResult = this.getPredicateExpr().evaluate();
        if (predicateResult instanceof TrueType) {
            return true;
        }
        else {
            return false;
        }
    }

    private void eagerEvaluateLetStatements() throws OperonGenericException {
        //
        // Eager-evaluate Let-statements:
        //
        for (java.util.Map.Entry<String, LetStatement> entry : this.getStatement().getLetStatements().entrySet()) {
		    LetStatement letStatement = (LetStatement) entry.getValue();
		    letStatement.resolveConfigs();
		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
		        letStatement.evaluate();
		    }
	    }
    }

    private void synchronizeState() {
        for (LetStatement lstmnt : this.getStatement().getLetStatements().values()) {
            if (lstmnt.getResetType() == LetStatement.ResetType.AFTER_SCOPE) {
                lstmnt.reset();
            }
        }
    }

    public void setPredicateExpr(Node pExpr) {
        this.predicateExpr = pExpr;
    }
    
    public Node getPredicateExpr() {
        return this.predicateExpr;
    }

    public void setWhileExpr(Node wExpr) {
        this.whileExpr = wExpr;
    }
    
    public Node getWhileExpr() {
        return this.whileExpr;
    }

    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this.getStatement());
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

    public Info resolveConfigs() throws OperonGenericException {
        Info info = new Info();
        
        if (this.configs == null) {
            return info;
        }
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                default:
                    break;
            }
        }
        return info;
    }

    private class Info {

    }

}