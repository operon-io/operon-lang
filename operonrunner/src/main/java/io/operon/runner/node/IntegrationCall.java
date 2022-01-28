/*
 * License: Operon-license v1. https://operon.io/operon-license
 *
 */
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

package io.operon.runner.node;

import io.operon.runner.OperonContext;
import io.operon.runner.OperonRunner;
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

import io.operon.runner.Context;
import io.operon.runner.BaseContext;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.Statement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.function.core.raw.RawEvaluate;
import io.operon.runner.system.IntegrationComponent;
import io.operon.runner.system.integration.out.OutComponent;
import io.operon.runner.system.integration.file.FileComponent;
import io.operon.runner.system.integration.http.HttpComponent;
import io.operon.runner.system.integration.zip.ZipComponent;
import io.operon.runner.system.integration.exec.ExecComponent;
import io.operon.runner.system.integration.cipher.CipherComponent;
import io.operon.runner.system.integration.digest.DigestComponent;
import io.operon.runner.system.integration.publish.PublishComponent;
import io.operon.runner.system.integration.queue.QueueComponent;
import io.operon.runner.system.integration.call.CallComponent;
import io.operon.runner.system.integration.certificate.CertificateComponent;
import io.operon.runner.system.integration.socket.SocketComponent;
import io.operon.runner.system.integration.readfile.ReadfileComponent;
import io.operon.runner.system.integration.robot.RobotComponent;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import java.lang.reflect.Method;

import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.OperonComponentException;


public class IntegrationCall extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(IntegrationCall.class);

    private String componentName; // assigned from parsed static query
    private String componentId; // assigned from parsed static query
    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    
    //private boolean jsonConfigurationEvaluated = false;
    
    private String componentsFilePath; // path from where the "components.json" -file is loaded from
    
    // If the component is a .jar, then the following apply:
    private IntegrationComponent componentInstance = null; // The dynamically loaded component -instance. This has to be transient if expr is deep-copied by serialization.
    private Class componentClass = null; // This has to be transient if expr is deep-copied by serialization.
    
    private Boolean disabledComponent = null;
    
    public IntegrationCall(Statement statement) {
        super(statement);
    }

    //
    // TODO: when component is loaded, then cache it in the OperonContext? Now it is cached inside this call.
    //       The instance is also given it's own, unique configuration. Therefore it is justified to cache it
    //       inside the node, as it is currently done. This required recheck when thinking in the context
    //       of multiple modules... Each module might have own components.json -definition file.
    //
    public OperonValue evaluate() throws OperonGenericException {
        OperonValue currentValue = this.getStatement().getCurrentValue();
        log.debug("IntegrationCall :: Sending cv to component");
        log.debug("IntegrationCall :: cv :: " + currentValue);
        log.debug("IntegrationCall :: componentName :: " + this.getComponentName());
        log.debug("IntegrationCall :: componentId :: " + this.getComponentId());
        log.debug("Sending " + currentValue.toString() + " to >> " + this.getComponentName() + ":" + this.getComponentId());
        
        OperonValue result = null;
        
        //System.out.println("IntegrationCall evaluate()");
        
        if (this.disabledComponent == null) {
            Context rootContext = BaseContext.getRootContextByStatement(this.getStatement());
            if (rootContext.getConfigs() != null && rootContext.getConfigs().getDisabledComponents().contains(this.getComponentName())) {
                this.disabledComponent = true;
                ErrorValue ev = new ErrorValue(this.getStatement());
                ev.setCode("COMPONENT_DISABLED");
                ev.setType("COMPONENT");
                ev.setMessage("Component disabled: " + this.getComponentName());
                result = ev;
                return result;
            }
            else {
                this.disabledComponent = false;
            }
        }
        
        else if (this.disabledComponent == true) {
            ErrorValue ev = new ErrorValue(this.getStatement());
            ev.setCode("COMPONENT_DISABLED");
            ev.setType("COMPONENT");
            ev.setMessage("Component disabled: " + this.getComponentName());
            result = ev;
            return result;
        }
        
        if (this.getComponentName().equals("out")) {
            IntegrationComponent outComponent = new OutComponent();
            outComponent.setComponentName(this.getComponentName());
            outComponent.setComponentId(this.getComponentId());
            outComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                outComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "OUT", oce.getMessage());
            }
            result = currentValue; // bypass out-component's output
        }
        
        else if (this.getComponentName().equals("file")) {
            IntegrationComponent fileComponent = new FileComponent();
            fileComponent.setComponentName(this.getComponentName());
            fileComponent.setComponentId(this.getComponentId());
            fileComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = fileComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "FILE", oce.getMessage());
            }
        }
        
        else if (this.getComponentName().equals("http")) {
            log.debug("IntegrationCall :: http");
            IntegrationComponent httpComponent = new HttpComponent();
            httpComponent.setComponentName(this.getComponentName());
            httpComponent.setComponentId(this.getComponentId());
            httpComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = httpComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "HTTP", oce.getMessage());
            }
        }
        
        else if (this.getComponentName().equals("readfile")) {
            IntegrationComponent readfileComponent = new ReadfileComponent();
            readfileComponent.setComponentName(this.getComponentName());
            readfileComponent.setComponentId(this.getComponentId());
            readfileComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = readfileComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "READFILE", oce.getMessage());
            }
        }

        else if (this.getComponentName().equals("zip")) {
            IntegrationComponent zipComponent = new ZipComponent();
            zipComponent.setComponentName(this.getComponentName());
            zipComponent.setComponentId(this.getComponentId());
            zipComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = zipComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "ZIP", oce.getMessage());
            }
        }

        else if (this.getComponentName().equals("exec")) {
            ExecComponent execComponent = new ExecComponent();
            execComponent.setComponentName(this.getComponentName());
            execComponent.setComponentId(this.getComponentId());
            execComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = execComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "EXEC", oce.getMessage());
            }
        }

        else if (this.getComponentName().equals("cipher")) {
            CipherComponent cipherComponent = new CipherComponent();
            cipherComponent.setComponentName(this.getComponentName());
            cipherComponent.setComponentId(this.getComponentId());
            cipherComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = cipherComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "CIPHER", oce.getMessage());
            }
        }

        else if (this.getComponentName().equals("digest")) {
            DigestComponent digestComponent = new DigestComponent();
            digestComponent.setComponentName(this.getComponentName());
            digestComponent.setComponentId(this.getComponentId());
            digestComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = digestComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "DIGEST", oce.getMessage());
            }
        }
        
        else if (this.getComponentName().equals("publish")) {
            PublishComponent publishComponent = new PublishComponent();
            publishComponent.setComponentName(this.getComponentName());
            publishComponent.setComponentId(this.getComponentId());
            publishComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = publishComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "PUBLISH", oce.getMessage());
            }
        }
        
        else if (this.getComponentName().equals("queue")) {
            QueueComponent queueComponent = new QueueComponent();
            queueComponent.setComponentName(this.getComponentName());
            queueComponent.setComponentId(this.getComponentId());
            queueComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = queueComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "QUEUE", oce.getMessage());
            }
        }

        else if (this.getComponentName().equals("call")) {
            CallComponent callComponent = new CallComponent();
            callComponent.setComponentName(this.getComponentName());
            callComponent.setComponentId(this.getComponentId());
            callComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = callComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "CALL", oce.getMessage());
            }
        }
        
        else if (this.getComponentName().equals("certificate")) {
            CertificateComponent certificateComponent = new CertificateComponent();
            certificateComponent.setComponentName(this.getComponentName());
            certificateComponent.setComponentId(this.getComponentId());
            certificateComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = certificateComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "CERTIFICATE", oce.getMessage());
            }
        }

        else if (this.getComponentName().equals("operon")) {
            SocketComponent socketComponent = new SocketComponent();
            socketComponent.setComponentName(this.getComponentName());
            socketComponent.setComponentId(this.getComponentId());
            socketComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = socketComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "OPERON", oce.getErrorMessage());
            } catch (Exception e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "OPERON", e.getMessage());
            }
        }

        else if (this.getComponentName().equals("robot")) {
            RobotComponent robotComponent = new RobotComponent();
            robotComponent.setComponentName(this.getComponentName());
            robotComponent.setComponentId(this.getComponentId());
            robotComponent.setJsonConfiguration(this.getJsonConfiguration());
            try {
                result = robotComponent.produce(currentValue);
            } catch (OperonComponentException oce) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "ROBOT", oce.getErrorMessage());
            } catch (Exception e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", "ROBOT", e.getMessage());
            }
        }
        
        else {
            //System.out.println("IntegrationCall evaluate() --> ELSE :: " + this.getComponentName());
            
            Info info = this.resolveConfigs();
            
            //System.out.println("--> set configuration obj for the component");
            
            // When the component instance (a JAR) has been loaded, this will set the ObjectType-configuration
            // for that.
            if (this.getComponentInstance() != null) {
                this.setConfigurationForJarComponent(info);
            }
            
            if (info.componentExpr != null) {
                // TODO: if component is an expr, then we should inject the value $configs for that.
                //System.out.println("Component expr detected");
                //System.out.println("Configs: " + this.getJsonConfiguration());
                RawValue componentExpr = info.componentExpr;
                String exprStr = new String(componentExpr.getBytes());
                OperonValue componentExprResult = null;
                try {
                    OperonContext newCtx = new OperonContext(); // TODO: we should pass the OperonConfigs for this new Context
                    LetStatement configsLet = new LetStatement(newCtx);

                    ObjectType configsObj = this.getJsonConfiguration();
                    if (info.altConfigObj != null) {
                        configsObj.getPairs().addAll(info.altConfigObj.getPairs());
                    }
                    
                    configsLet.setNode(configsObj);
                    configsLet.setValueRefStr("$configs");
                    DefaultStatement newStmt = new DefaultStatement(newCtx);
                    OperonValue cvCopy = currentValue;
                    newStmt.setCurrentValue(cvCopy); // CHECK: copying value does not deep copy the statements...
                    newStmt.getLetStatements().put("$configs", configsLet);
                    //newStmt.getRuntimeValues().put("configs", this.getJsonConfiguration()); // This does not resolve the value (ValueRef)
                    componentExprResult = RawEvaluate.evaluate(newStmt, exprStr, cvCopy);
                } catch (Exception e) {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", this.getComponentName(), e.getMessage());
                }
                
                //
                // NOTE: FunctionRef is not supported because we require an expression here,
                //       and FunctionRef is not such an expr.
                //
                
                if (componentExprResult instanceof LambdaFunctionRef) {
                    LambdaFunctionRef componentExprAsLambdaRef = (LambdaFunctionRef) componentExprResult;
                    componentExprAsLambdaRef.getParams().clear();
                    // NOTE: we cannot guarantee the order of keys that Map.keySet() returns,
                    //       therefore we must assume that the keys are named in certain manner.
                    
                    ObjectType configsObj = this.getJsonConfiguration();
                    if (info.altConfigObj != null) {
                        configsObj.getPairs().addAll(info.altConfigObj.getPairs());
                    }
                    
                    componentExprAsLambdaRef.getParams().put("$configs", configsObj);
                    componentExprAsLambdaRef.setCurrentValueForFunction(currentValue);
                    result = componentExprAsLambdaRef.invoke();
                }
                
                else {
                    //System.out.println("Component was expr which was evaluated");
                    result = componentExprResult;
                }
            }
            
            else if (this.getComponentInstance() == null 
                || this.getComponentClass() == null) {

                // Load the component from .jar -file:
                //System.out.println("Component is a jar");
                this.loadJarComponent();
                try {
                    // Get the main method from the loaded class and invoke it
                    Method method = this.getComponentClass().getMethod("produce", OperonValue.class);
                    //System.out.println("Invoking...");
                    result = (OperonValue) method.invoke(componentInstance, (Object) currentValue); // Convert Object to OperonValue
                    //System.out.println("Invoke DONE, return result :: " + result);
                } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", this.getComponentName(), e.getMessage());
                }
            }
        }
        // update the currentValue from the statement
        this.getStatement().setCurrentValue(result);
        
        log.debug("IntegrationCall result :: " + result);
        return result;
    }

    public void loadJarComponent() throws OperonGenericException {
        Info info = this.resolveConfigs();
        String JAR_URL = "jar:" + info.componentLink + "!/";
        Class loadedComponentClass = ComponentSystemUtil.loadComponent(JAR_URL, info.componentLink);
        this.setComponentClass(loadedComponentClass);

        try {
            this.setComponentInstance( (IntegrationComponent) componentClass.getDeclaredConstructor().newInstance() );
        } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException | InstantiationException | IllegalAccessException e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", this.getComponentName(), e.getMessage());
        }

        this.setConfigurationForJarComponent(info);
        
        this.getComponentInstance().setComponentName(this.getComponentName());
        this.getComponentInstance().setComponentId(this.getComponentId());
    }
    
    //
    // Set the "configurationObj" for the integration-component:
    //
    //      The last-part of this grammar-expr is set: -> componentName:componentId:configurationObj
    //
    // If the components.json contains also configuration, then these configurations
    // are also added into the obj, by overriding those that were specified in the query.
    //
    public void setConfigurationForJarComponent(Info info) throws OperonGenericException {
        ObjectType configsObj = this.getJsonConfiguration();
        if (configsObj != null && configsObj.getPairs().size() > 0) {
            if (info.altConfigObj != null && info.altConfigObj.getPairs().size() > 0) {
                configsObj.getPairs().addAll(info.altConfigObj.getPairs());
                this.getComponentInstance().setJsonConfiguration(configsObj);
            }
            else {
                this.getComponentInstance().setJsonConfiguration(configsObj);
            }
        }
        
        else if (info.altConfigObj != null && info.altConfigObj.getPairs().size() > 0) {
            this.getComponentInstance().setJsonConfiguration(info.altConfigObj);
        }
    }

    public Info resolveConfigs() throws OperonGenericException {
        log.debug("Load component :: " + this.getComponentName() + ", definition file :: " + this.getComponentsDefinitionFilePath());
        
        Info info = new Info();
        
        // load component dynamically:
        ObjectType integrationComponentObj = 
            ComponentSystemUtil.loadIntegrationComponentDefinition(this.getComponentName(), this.getComponentsDefinitionFilePath());
        
        log.debug(">> Component definition object loaded");
        
        if (integrationComponentObj == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "INTEGRATION", this.getComponentName(), "Could not load component definition object for component");
        }
        
        log.debug(" >> Definition pairs size ::" + integrationComponentObj.getPairs().size());
        
        ObjectType altConfigObj = null;
        ObjectType altConfigObjTop = null;
        String componentLink = "";
        
        for (PairType pair : integrationComponentObj.getPairs()) {
            String key = pair.getKey().toLowerCase();
            log.debug("Key :: " + key);
            
            if (key.equals("\"componentexpr\"")) {
                RawValue componentExpr = (RawValue) pair.getValue().evaluate();
                info.componentExpr = componentExpr;
            }
            
            if (key.equals("\"resolveuri\"")) {
                OperonValue resolveUriValue = pair.getValue().evaluate();
                // resolveUri might also be null
                if (resolveUriValue instanceof StringType) {
                    componentLink = ((StringType) resolveUriValue).getJavaStringValue();
                    info.componentLink = componentLink;
                    log.debug("Linking component :: " + componentLink);
                }
            }
            
            //
            // The alternative-configuration from components.json -file
            // which extends already given configuration:
            //
            if (key.equals("\"configuration\"")) {
                altConfigObjTop = (ObjectType) pair.getValue().evaluate();
                for (PairType confPair : altConfigObjTop.getPairs()) {
                    String confKey = confPair.getKey().toLowerCase();
                    if (this.getComponentId() != null && confKey.equals("\"" + this.getComponentId().toLowerCase() + "\"")) {
                        altConfigObj = (ObjectType) confPair.getValue().evaluate();
                    }
                }
                info.altConfigObj = altConfigObj;
            }
        }

        return info;
    }

    public void setComponentsDefinitionFilePath(String componentsFilePath) {
        this.componentsFilePath = componentsFilePath;
    }
    public String getComponentsDefinitionFilePath() {
        if (this.componentsFilePath != null) {
            return this.componentsFilePath;
        }
        else {
            return ComponentSystemUtil.DEFAULT_COMPONENTS_DEFINITION_FILE;
        }
    }
    public void setComponentName(String cName) { this.componentName = cName; }
    public void setComponentId(String cid) { this.componentId = cid; }
    public void setJsonConfiguration(ObjectType jsonConfig) { this.jsonConfiguration = jsonConfig; }
    
    public ObjectType getJsonConfiguration() throws OperonGenericException {
        if (this.jsonConfiguration == null) {
            return new ObjectType(this.getStatement());
        }
        
        //
        // The initial jsonConfiguration -object might hold
        // unevaluated expressions, which must first be evaluated
        // before sending to integrationComponents.
        //
        
        //if (this.jsonConfigurationEvaluated == false) {
            this.jsonConfiguration = this.jsonConfiguration.evaluate();
        //    jsonConfigurationEvaluated = true;
        //}
        return this.jsonConfiguration;
    }
    
    public String getComponentName() { return this.componentName; }
    public String getComponentId() { return this.componentId; }
    
    private IntegrationComponent getComponentInstance() {
        return this.componentInstance;
    }
    
    private void setComponentInstance(IntegrationComponent ci) {
        this.componentInstance = ci;
    }
    
    private Class getComponentClass() {
        return this.componentClass;
    }
    
    private void setComponentClass(Class cc) {
        this.componentClass = cc;
    }
    
    private class Info {
        // If component is given as a JAR-file, then this
        // points into that file:
        public String componentLink = "";
        
        // The configuration that extends the configuration given
        // in the query:
        public ObjectType altConfigObj = null;
        
        // The component may be given as an expr (e.g. a Lambda-function, which
        // implements the component's logic):
        public RawValue componentExpr = null;
    }
}