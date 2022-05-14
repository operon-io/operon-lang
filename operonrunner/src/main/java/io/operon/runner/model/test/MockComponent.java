/*
 *   Copyright 2022, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.operon.runner.model.test;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class MockComponent {
     // no logger 
    
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