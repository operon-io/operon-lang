/** OPERON-LICENSE **/
package io.operon.runner.model.test;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class MockComponent {
    private static Logger log = LogManager.getLogger(MockComponent.class);
    
    private String componentNamespace;
    private String componentName;
    private String componentIdentifier;
    private Node mockExpr;

    public MockComponent() {}

    public void setComponentNamespace(String ns) {
        this.componentNamespace = ns;
    }

    public String getComponentNamespace() {
        return this.componentNamespace;
    }

    public void setComponentName(String n) {
        this.componentName = n;
    }

    public String getComponentName() {
        return this.componentName;
    }

    public void setComponentIdentifier(String n) {
        this.componentIdentifier = n;
    }

    public String getComponentIdentifier() {
        return this.componentIdentifier;
    }

    public Node getMockExpr() {
        return this.mockExpr;
    }
    
    public void setMockExpr(Node me) {
        this.mockExpr = me;
    }

}