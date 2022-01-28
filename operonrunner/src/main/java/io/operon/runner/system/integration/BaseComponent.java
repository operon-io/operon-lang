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

package io.operon.runner.system.integration;

import io.operon.runner.OperonContext;
import io.operon.runner.util.RandomUtil;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.lang.reflect.InvocationTargetException;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.util.ErrorUtil;

import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;

import org.apache.logging.log4j.LogManager;


public abstract class BaseComponent implements IntegrationComponent {
    private static Logger log = LogManager.getLogger(BaseComponent.class);
    
    private String componentName;
    private String componentId;
    private ObjectType jsonConfiguration;
    
    // Default implementation
    public OperonValue produce(OperonValue value) throws OperonComponentException {
        //System.out.println(this.getComponentId() + " :: " + value.toString());
        return value;
    }
    
    public void setJsonConfiguration(ObjectType jsonConfig) {
        this.jsonConfiguration = jsonConfig;
    }

    public ObjectType getJsonConfiguration() {
        return this.jsonConfiguration;
    }

    public void setComponentName(String cName) {
        this.componentName = cName;
    }
    
    public String getComponentName() {
        return this.componentName;
    }

    public void setComponentId(String cid) {
        this.componentId = cid;
    }
    
    public String getComponentId() {
        return this.componentId;
    }
}