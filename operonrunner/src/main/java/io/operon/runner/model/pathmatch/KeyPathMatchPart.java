/** OPERON-LICENSE **/
package io.operon.runner.model.pathmatch;
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class KeyPathMatchPart implements PathMatchPart { 
    private String key;
    public KeyPathMatchPart() {}
    public KeyPathMatchPart(String k) {
        this.key = k;
    }
    
    public void setKey(String k) {
        this.key = k;
    }
    
    public String getKey() {
        return this.key;
    }

    @Override
    public String toString() {
        return "." + this.getKey();
    }

}