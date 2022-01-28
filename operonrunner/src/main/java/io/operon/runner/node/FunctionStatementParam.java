/** OPERON-LICENSE **/
package io.operon.runner.node;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValueConstraint;

//
// This is used with the FunctionStatement, when the param names
// are collected. This list is used to check and match the argument
// order when calling the function.
//
// From the following example this models $param1 and $param2,
// and the constraint <Number> for the $param1:
//
//     Function foo($param1 <Number>, $param2) <String>: "Foo" End
//
public class FunctionStatementParam extends AbstractNode implements Node, java.io.Serializable {
    // Parameter name, e.g. $param1
    // Used to check the argument order and their names
    private String param;

    // Optional constraint
    private OperonValueConstraint constraint;
    
    public FunctionStatementParam(Statement stmnt) {
        super(stmnt);
    }
    
    public void setParam(String p) {
        this.param = p;
    }
    
    public String getParam() {
        return this.param;
    }
    
    public void setOperonValueConstraint(OperonValueConstraint c) {
        this.constraint = c;
    }

    public OperonValueConstraint getOperonValueConstraint() {
        return this.constraint;
    }
    
}