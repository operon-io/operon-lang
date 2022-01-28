/** OPERON-LICENSE **/
package io.operon.runner.model.path;
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class KeyPathPart implements PathPart { 
    private String key;
    public KeyPathPart() {}
    public KeyPathPart(String k) {
        this.key = k;
    }
    
    public void setKey(String k) {
        this.key = k;
    }
    
    public String getKey() {
        return this.key;
    }
 
    public KeyPathPart copy() {
        KeyPathPart kpp = new KeyPathPart();
        kpp.setKey(new String(this.getKey()));
        return kpp;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        KeyPathPart kpp2 = (KeyPathPart) obj;
        return this.getKey().equals(((KeyPathPart) kpp2).getKey());
    }
    
    @Override
    public String toString() {
        return "." + this.key;
    }
    
}