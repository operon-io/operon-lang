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

import java.io.IOException;

import io.operon.runner.node.type.*;
import io.operon.runner.node.Node;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class AssertComponent {
     // no logger 
    
    private String componentNamespace; // I.e. in which module this is defined in
    private String componentName; // The first ID of form ID ':' ID
    private String componentIdentifier; // The second ID of form ID ':' ID
    private boolean assertRunned = false;
    private boolean isResolved = false;
    
    private Node configs;
    private Info info;

    private Node assertExpr;

    public AssertComponent() {
        this.info = new Info();
    }

    public void setAssertName(String n) {
        this.info.assertName = n;
    }

    public String getAssertName() {
        if (this.isResolved ==  false) {
            try {
                this.resolveConfigs();
            } catch (OperonGenericException oge) {
                System.err.println("ERROR Assert: " + this.getComponentName() + " : cannot resolve configuration.");
            }
        }
        return this.info.assertName;
    }

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

    public Node getAssertExpr() {
        return this.assertExpr;
    }
    
    public void setAssertExpr(Node ae) {
        this.assertExpr = ae;
    }
    
    public void setAssertRunned(boolean r) {
        this.assertRunned = r;
    }
    
    public boolean isAssertRunned() {
        return this.assertRunned;
    }
    
    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs(Statement stmt) throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(stmt);
        }
        return (ObjectType) this.configs;
    }

    public Info resolveConfigs() throws OperonGenericException {
        Statement stmt = null;
        
        try {
            stmt = new DefaultStatement(new OperonContext());
        } catch (IOException ioe) {
            // noop
        }
        
        if (this.getConfigs(stmt).getPairs().size() == 0) {
            return this.info;
        }
        OperonValue currentValue = this.configs.getStatement().getCurrentValue();
        if (currentValue == null) {
            EmptyType ev = new EmptyType(stmt);
            currentValue = ev;
        }
        OperonValue currentValueCopy = null;
        for (PairType pair : this.getConfigs(stmt).getPairs()) {
            currentValueCopy = currentValue.copy();
            pair.getStatement().setCurrentValue(currentValueCopy);
            String key = pair.getKey();
            switch (key) {
                case "\"required\"":
                    Node requiredValue = pair.getValue().evaluate();
                    if (requiredValue instanceof FalseType) {
                        this.info.required = false;
                    }
                    else {
                        this.info.required = true;
                    }
                    break;
                case "\"name\"":
                    String name_value = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.assertName = name_value;
                    break;
                default:
                    break;
            }
        }
        this.isResolved = true;
        this.configs.getStatement().setCurrentValue(currentValue.copy());
        return this.info;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.getComponentNamespace() != null && this.getComponentNamespace().isEmpty() == false) {
            sb.append(this.getComponentNamespace() + ":" + this.getComponentName() + ":" + this.getComponentIdentifier());
        }
        else {
            sb.append(this.getComponentName() + ":" + this.getComponentIdentifier());
        }
        if (this.getAssertName() != null) {
            sb.append(" [" + this.getAssertName() + "]");
        }
        return sb.toString();
    }
    
    public boolean isRequired() throws OperonGenericException {
        if (this.isResolved ==  false) {
            this.resolveConfigs();
        }
        return this.info.required;
    }
    
    private class Info {
        public boolean required = true;
        public String assertName = null;
    }
}