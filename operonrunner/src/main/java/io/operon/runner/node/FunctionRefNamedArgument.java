/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// A single named argument. Form: '$a: (expr | FunctionRefArgumentPlaceholder)'.
//
public class FunctionRefNamedArgument extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FunctionRefNamedArgument.class);

    private String argName;
    private Node exprNode;
    private boolean hasPlaceholder = false;
    
    public FunctionRefNamedArgument(Statement stmnt) {
        super(stmnt);
    }

    public String getArgumentName() {
        return this.argName;
    }
    
    public void setArgumentName(String aName) {
        this.argName = aName;
    }

    public Node getExprNode() {
        return this.exprNode;
    }
    
    public void setExprNode(Node e) {
        this.exprNode = e;
    }
    
    public void setHasPlaceholder(boolean b) {
        this.hasPlaceholder = b;
    }
    
    public boolean getHasPlaceholder() {
        return this.hasPlaceholder;
    }
    
    @Override
    public String toString() {
        return "FunctionRefNamedArgument :: " + this.getArgumentName();
    }
}