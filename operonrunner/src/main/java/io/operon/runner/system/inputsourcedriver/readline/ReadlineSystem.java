/** OPERON-LICENSE **/
package io.operon.runner.system.inputsourcedriver.readline;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import io.operon.runner.OperonContext;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.system.BaseSystem;
import io.operon.runner.statement.FromStatement;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ReadlineSystem implements InputSourceDriver {
    private static Logger log = LogManager.getLogger(ReadlineSystem.class);

    private ObjectType jsonConfiguration; // optional: json-configuration for the component
    private boolean isRunning;
    private long pollCounter = 0L;
    private OperonContextManager ocm;
    
    public ReadlineSystem() {}
    
    public boolean isRunning() {
        return this.isRunning;
    }
    
    public OperonContextManager getOperonContextManager() {
        return this.ocm;
    }
    
    public void setOperonContextManager(OperonContextManager o) {
        this.ocm = o;
    }
    
    public void start(OperonContextManager o) {
        OperonContext ctx = null;
        try {
            Info info = this.resolve();
            this.isRunning = true;
            if (this.getOperonContextManager() == null && o != null) {
                ocm = o;
                ctx = ocm.resolveContext("");
            }
            else if (o == null) {
                ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, info.contextManagement);
            }
            
            System.out.println(info.promptMessage);
            
            handleFrame(ocm.resolveContext("correlationId"), info);
        } catch (OperonGenericException e) {
            log.error("Exception :: " + e.toString());
            ctx.setException(e);
        } catch (Exception ex) {
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
        }
    }
    
    public void handleFrame(OperonContext ctx, Info info) throws OperonGenericException, IOException {
        OperonValue initialValue = new EmptyType(new DefaultStatement(ctx));
        
        BufferedReader obj = new BufferedReader(new InputStreamReader(System.in));   
        String str;
        do {
            str = obj.readLine();
            StringType jstr = new StringType(new DefaultStatement(ctx));
            jstr.setFromJavaString(str);
            ctx.setInitialValue(jstr);
            OperonValue result = ctx.evaluateSelectStatement();
            ctx.outputResult(result);
        } while(!str.equals(info.stopWord));
        this.isRunning = false;
    }
    
    public void requestNext() {}
    
    public void stop() {
        this.isRunning = false;
        log.info("Stopped");
    }
    
    public void setJsonConfiguration(ObjectType jsonConfig) { this.jsonConfiguration = jsonConfig; }
    public ObjectType getJsonConfiguration() { return this.jsonConfiguration; }
    public long getPollCounter() { return this.pollCounter; }
    
    private Info resolve() throws OperonGenericException {
        List<PairType> jsonPairs = this.getJsonConfiguration().getPairs();
        String iStopWord = "";
        String iContextManagementStr = "";
        
        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"stopword\"":
                    iStopWord = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.stopWord = iStopWord;
                    break;
                case "\"promptmessage\"":
                    String iPromptMessage = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.promptMessage = iPromptMessage;
                    break;
                case "\"contextmanagement\"":
                    iContextManagementStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW) {
                        info.contextManagement = OperonContextManager.ContextStrategy.ALWAYS_CREATE_NEW;
                    }
                    else if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID) {
                        info.contextManagement = OperonContextManager.ContextStrategy.REUSE_BY_CORRELATION_ID;
                    }
                    else if (OperonContextManager.ContextStrategy.valueOf(iContextManagementStr.toUpperCase()) 
                          == OperonContextManager.ContextStrategy.SINGLETON) {
                        info.contextManagement = OperonContextManager.ContextStrategy.SINGLETON;
                    }
                    break;
                default:
                    System.out.println("Isd-template: no mapping for configuration key: " + key);
            }
        }
        return info;
    }
    
    private class Info {
        private String stopWord = "stop";
        private String promptMessage = "Enter lines of text. Enter '" + this.stopWord + "' to quit.";
        private OperonContextManager.ContextStrategy contextManagement = OperonContextManager.ContextStrategy.SINGLETON;
    }
}