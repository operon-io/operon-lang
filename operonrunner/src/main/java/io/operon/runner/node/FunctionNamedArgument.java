/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.Node;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// A single named argument. Form: '$a: expr'.
//
public class FunctionNamedArgument extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FunctionNamedArgument.class);

    private String argName;
    private Node argValue;
    
    public FunctionNamedArgument(Statement stmnt) {
        super(stmnt);
    }

    public String getArgumentName() {
        return this.argName;
    }
    
    public void setArgumentName(String aName) {
        this.argName = aName;
    }

    public Node getArgumentValue() {
        return this.argValue;
    }
    
    public void setArgumentValue(Node av) {
        this.argValue = av;
    }

}