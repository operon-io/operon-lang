/** OPERON-LICENSE **/
package io.operon.runner.system;

import io.operon.runner.node.type.ObjectType;
import io.operon.runner.OperonContextManager;

public interface InputSourceDriver {

    public void start(OperonContextManager ocm);
    
    public void stop();
    
    public boolean isRunning();
    
    public void setJsonConfiguration(ObjectType jsonConfig);
    
    public ObjectType getJsonConfiguration();
    
}