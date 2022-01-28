/** OPERON-LICENSE **/
package io.operon.runner.model.pathmatch;
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class DynamicKeyPathMatchPart implements PathMatchPart { 
    private Node keyExpr;
    public DynamicKeyPathMatchPart() {}
    public DynamicKeyPathMatchPart(Node kExpr) {
        this.keyExpr = kExpr;
    }
    
    public void setKeyExpr(Node kExpr) {
        this.keyExpr = kExpr;
    }
    
    public Node getKeyExpr() {
        return this.keyExpr;
    }

    @Override
    public String toString() {
        return "." + this.getKeyExpr().getExpr();
    }

}