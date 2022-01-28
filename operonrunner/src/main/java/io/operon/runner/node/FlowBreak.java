/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.NullType;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FlowBreak extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FlowBreak.class);

    private Node exprNode;

    public FlowBreak(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER FlowBreak.evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        this.getExprNode().getStatement().setCurrentValue(currentValue);
        OperonValue result = this.getExprNode().evaluate();
        return result;
    }

    public void setExprNode(Node expr) {
        this.exprNode = expr;
    }
    
    public Node getExprNode() {
        return this.exprNode;
    }

}