/** OPERON-LICENSE **/
package io.operon.runner.node;

import io.operon.runner.node.Node;
import io.operon.runner.statement.Statement;

//
// This is the normal argument for function, i.e. "unnamed"
// and is used with function and lambda-function calls.
// This may be used in mix with named arguments.
//

// WARNING: NOT USED ANYWHERE! MIGHT HAVE TO REMOVE THIS! --> Check this!

public class FunctionRegularArgument extends AbstractNode implements Node {
    private Node argValue;
    
    public FunctionRegularArgument(Statement stmnt) {
        super(stmnt);
    }
    
    public void setArgument(Node arg) {
        this.argValue = arg;
    }
    
    public Node getArgument() {
        return this.argValue;
    }

    @Override
    public String toString() {
        return "FunctionRegularArgument";
    }
}