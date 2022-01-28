/** OPERON-LICENSE **/
package io.operon.runner.node;

import io.operon.runner.processor.UnaryNodeProcessor;
import io.operon.runner.statement.Statement;
import io.operon.runner.Context;
import io.operon.runner.node.type.OperonValue;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class UnaryNode extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(UnaryNode.class);
    
    private Node node;
    private UnaryNodeProcessor proc;
    
    public UnaryNode(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        Context ctx = this.getStatement().getOperonContext();
        ctx.addStackTraceElement(this);
        if (this.proc != null) {
            OperonValue result = proc.process(this.getStatement(), this.getNode());
            this.setEvaluatedValue(result);
            // update the currentValue from the statement
            this.getStatement().setCurrentValue(result);
            log.debug("Unary node evaluated with processor :: " + result);
            return result;
        }
        
        else {
            //System.out.println("UnaryNode :: evaluate");
            //System.out.println("  >> UnaryNode :: node stmt=" + this.getNode().getStatement());
            OperonValue result = this.getNode().evaluate();
            this.setEvaluatedValue(result);
            // update the currentValue from the statement
            this.getStatement().setCurrentValue(result);
            log.debug("Unary node evaluated without processor :: class: " + result.getClass().getName());
            return result;
        }
    }
    
    public void setNode(Node node) { this.node = node; }
    
    public Node getNode() { return this.node; }

    public void setUnaryNodeProcessor(UnaryNodeProcessor proc) {this.proc = proc; }

    public String toString() {
        return this.getEvaluatedValue().toString();
    }
    
}
