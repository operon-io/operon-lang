/** OPERON-LICENSE **/
package io.operon.runner.node;

import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.statement.Statement;
import io.operon.runner.Context;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.model.exception.OperonGenericException;

public class BinaryNode extends AbstractNode implements Node {
    
    private Node lhs;
    private Node rhs;
    private BinaryNodeProcessor proc;
    
    public BinaryNode(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        Context ctx = this.getStatement().getOperonContext();
        ctx.addStackTraceElement(this);
        OperonValue result = proc.process(this.getStatement(), lhs, rhs);
        this.setEvaluatedValue(result);
        // update the currentValue from the statement
        this.getStatement().setCurrentValue(result);
        return result;
    }
    
    public void setLhs(Node lhs) { this.lhs = lhs; }
    public Node getLhs() { return this.lhs; }
    public void setRhs(Node rhs) { this.rhs = rhs; }
    public Node getRhs() { return this.rhs; }
    public void setBinaryNodeProcessor(BinaryNodeProcessor proc) {this.proc = proc; }
    
    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}
