/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FunctionArguments extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FunctionArguments.class);

    //
    // FunctionRegularArgument | FunctionNamedArgument
    //
    private List<Node> args;
    
    public FunctionArguments(Statement stmnt) {
        super(stmnt);
        this.args = new ArrayList<Node>();
    }

    public List<Node> getArguments() {
        return this.args;
    }

}