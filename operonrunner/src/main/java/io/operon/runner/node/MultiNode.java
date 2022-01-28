/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;
import io.operon.runner.statement.Statement;
import io.operon.runner.Context;
import io.operon.runner.node.type.OperonValue;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class MultiNode extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(MultiNode.class);
    private List<Node> nodes;
    
    public MultiNode(Statement stmnt) {
        super(stmnt);
        this.nodes = new ArrayList<Node>();
    }

    public OperonValue evaluate() throws OperonGenericException {
        // loop through nodes, and evaluate each
        OperonValue result = null;
        log.debug("ENTER MULTINODE EVALUATE");
        // Evaluate in reverse order
        //log.debug("MultiNode :: Runtimevalues :: " + this.getStatement().getRuntimeValues());
        for (int i = this.nodes.size() - 1; i >= 0; i --) {
            Node node = this.nodes.get(i);
            Context ctx = this.getStatement().getOperonContext();
            ctx.addStackTraceElement(node);
            //System.out.println("MultiNode :: Node :: " + node.getClass().getName() + ", stmt=" + node.getStatement());
            //System.out.println("  >> stmt cv=" + node.getStatement().getCurrentValue());
            
            // 
            // Continuation is the Node that will be executed after timeout.
            // 
            if (node instanceof Aggregate) {
                Aggregate agg = (Aggregate) node;
                if (i > 0) {
                    agg.setTimeoutContinuation(this.nodes.get(i - 1));
                }
            }
            
            result = node.evaluate();
            //System.out.println("MultiNode CV :: " + result);
            //System.out.println("  pos @: " + result.getPosition());
            //System.out.println("  parentKey @: " + result.getParentKey());
            //System.out.println("  parentObj @: " + result.getParentObj());
            this.getStatement().setCurrentValue(result);
        }
        //System.out.println("MultiNode setEvaluatedValue :: " + result);
        this.setEvaluatedValue(result);
        //System.out.println("Multinode :: result :: " + this.getStatement().getId() + " :: " + result);
        log.debug("EXIT MULTINODE EVALUATE");
        return result;
    }
    
    public void addNode(Node n) {
        this.nodes.add(n);
    }
    
    public List<Node> getNodes() {
        return this.nodes;
    }
    
    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}
