/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.processor.function.core.MappableTo;
import io.operon.runner.node.UnaryNode;
import io.operon.runner.node.BinaryNode;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.ObjAccess;
import java.util.List;
import java.util.ArrayList;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// OperonValueConstraint (PairType, Let -statement, FunctionCalls)
//
public class OperonValueConstraint extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(OperonValueConstraint.class);
    private Node constraintExpr;
    private String constraintAsString;
    private OperonValue valueToEvaluateAgainst;
    private OperonValue evaluatedValue; // Evaluates to TrueType or FalseType

    public OperonValueConstraint(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("OperonValueConstraint :: evaluate :: against value :: " + this.getValueToEvaluateAgainst().getClass().getName());
        this.evaluatedValue = (OperonValue) this.getValueToEvaluateAgainst().evaluate();
        log.debug("    evaluatedValue :: " + this.evaluatedValue);
        
        // Set the currentValue (valueToEvaluateAgainst) and evaluate the constraintExpr
        this.getStatement().setCurrentValue(this.getEvaluatedValue());
    
        OperonValue result = null;
        try {
            result = this.getValueConstraint().evaluate(); // evaluates constraintExpr
        } catch (Exception e) {
            FalseType falseResult = new FalseType(this.getStatement());
            log.debug("OperonValueConstraint :: EXCEPTION while evaluating the constraintExpr");
            return falseResult;
        }
        
        if (result instanceof TrueType) {
            log.debug("OperonValueConstraint :: constraint evaluation returned TrueType for value :: " + this.getEvaluatedValue());
            return result;
        }
        
        else if (result instanceof FalseType) {
            log.debug("OperonValueConstraint :: constraint evaluation returned FalseType for value :: " + this.getEvaluatedValue());
            return result;
        }
        
        else {
            log.debug("OperonValueConstraint :: constraint evaluation returned instanceof :: " + result.getClass().getName());
            List<Node> params = new ArrayList<Node>();
            params.add(result);
            this.getStatement().setCurrentValue(this.evaluatedValue);
            MappableTo mapTo = new MappableTo(this.getStatement(), params);
            this.setValueConstraint(mapTo);
            return this.getValueConstraint().evaluate();
        }
    
    }

    public void setValueConstraint(Node constraintExpr) {
        this.constraintExpr = constraintExpr;
    }
    
    public Node getValueConstraint() {
        return this.constraintExpr;
    }
    
    public void setValueToEvaluateAgainst(OperonValue value) throws OperonGenericException {
        log.debug("OperonValueConstraint :: setValueToEvaluateAgainst :: " + value.getClass().getName());
        //this.valueToEvaluateAgainst = (OperonValue) value.evaluate(); // might not work if DeferredMultiNode
        this.valueToEvaluateAgainst = value;
    }

    public OperonValue getValueToEvaluateAgainst() {
        return this.valueToEvaluateAgainst;
    }

    public OperonValue getEvaluatedValue() {
        return this.evaluatedValue;
    }

    public void setConstraintAsString(String jvc) {
        this.constraintAsString = jvc;
    }
    
    public String getConstraintAsString() {
        if (this.constraintAsString == null) {
            return "";
        }
        else {
            return this.constraintAsString;
        }
    }
    
    public static void evaluateConstraintAgainstOperonValue(OperonValue value, OperonValueConstraint c) throws OperonGenericException{
        log.debug("Base-statement :: evaluateConstraint()");

        // Put the same constraint also into OperonValue:
        value.setOperonValueConstraint(c);
        log.debug("Base-statement :: evaluateConstraint() :: added constraint to evaluated value.");
        
        //System.out.println("Constraint value: " + value);
        c.setValueToEvaluateAgainst(value);
        OperonValue constraintResult = c.evaluate();
        if (constraintResult instanceof FalseType) {
            //System.out.println("Throwing exception, constraint evaluated into false");
            ErrorUtil.createErrorValueAndThrow(value.getStatement(), "CONSTRAINT", "VIOLATION", "Value " + value + " violates constraint " +  c.getConstraintAsString());
        }
        else {
            //System.out.println("Constraint evaluated into true");
        }
    }
    
    @Override
    public String toString() {
        return this.getConstraintAsString();
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return this.getConstraintAsString();
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return this.getConstraintAsString();
    }

}