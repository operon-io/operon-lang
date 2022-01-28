/** OPERON-LICENSE **/
package io.operon.runner;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

import io.operon.runner.statement.FromStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.test.AssertComponent;
import io.operon.runner.model.test.MockComponent;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * Context -class acts as a container for the test elements.
 * 
 */
public class OperonTestsContext extends BaseContext implements Context, java.io.Serializable {
    private static Logger log = LogManager.getLogger(OperonTestsContext.class);
    
    private FromStatement fromStatement;
    
    //
    // String: ID ':' ID, i.e. signature for the component
    // Allow multiple asserts per component,
    // therefore: Map<String, List<AssertComponent>> componentAsserts
    //
    Map<String, List<AssertComponent>> componentAsserts;
    Map<String, MockComponent> componentMocks;
    private List<AssertComponent> failedComponents;
    
    public OperonTestsContext() throws IOException {
        this.componentAsserts = new HashMap<String, List<AssertComponent>>();
        this.componentMocks = new HashMap<String, MockComponent>();
        this.failedComponents = new ArrayList<AssertComponent>();
    }

    public List<AssertComponent> getFailedComponents() {
        return this.failedComponents;
    }

    public void setComponentAsserts(Map<String, List<AssertComponent>> map) {
        this.componentAsserts = map;
    }
    
    public Map<String, List<AssertComponent>> getComponentAsserts() {
        return this.componentAsserts;
    }
    
    public void setComponentMocks(Map<String, MockComponent> map) {
        this.componentMocks = map;
    }
    
    public Map<String, MockComponent> getComponentMocks() {
        return this.componentMocks;
    }

    public void setFromStatement(FromStatement from) {
        this.fromStatement = from;
    }
    
    public FromStatement getFromStatement() {
        return this.fromStatement;
    }

}