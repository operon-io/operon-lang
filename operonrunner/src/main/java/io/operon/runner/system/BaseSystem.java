/** OPERON-LICENSE **/
package io.operon.runner.system;

import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;

public abstract class BaseSystem {

    private String id;
    private ObjectType configuration;
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getId() {
        return this.id;
    }
    
    public void setConfiguration(ObjectType configuration) {
        this.configuration = configuration;
    }
    
    public ObjectType getConfiguration() {
        return this.configuration;
    }

}