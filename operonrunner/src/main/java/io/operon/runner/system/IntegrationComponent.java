/** OPERON-LICENSE **/
package io.operon.runner.system;

import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.model.exception.OperonComponentException;

// 
// IntegrationComponents are used (e.g.) to write the final or intermediary results into
// different kinds of endpoints.
// 
// IntegrationComponent may wait for return value (blocking), or work with "fire-and-forget" -pattern,
// depending on the given implementation, guided by given JsonConfiguration.
// 
public interface IntegrationComponent {

    public void setComponentId(String cid);
    public String getComponentId();
    public void setComponentName(String cName);
    public String getComponentName();
    public OperonValue produce(OperonValue value) throws OperonComponentException;
    
    public void setJsonConfiguration(ObjectType jsonConfig);

}