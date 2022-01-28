/** OPERON-LICENSE **/
package io.operon.runner.model;

import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.FunctionArguments;

//
// Used in the ObjArrayAccess
//
public class ObjAccessArgument {

    // accessKeyId is mutually exclusive to expr
    private String accessKeyId; // ID, i.e. value without quotes

    private Node expr;

    public ObjAccessArgument() {}

    public void setAccessKeyId(String akid) {
        this.accessKeyId = akid;
    }

    public String getAccessKeyId() {
        return this.accessKeyId;
    }
    
    public void setExpr(Node exp) {
        this.expr = exp;
    }
    
    public Node getExpr() {
        return this.expr;
    }
}