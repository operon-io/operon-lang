/** OPERON-LICENSE **/
package io.operon.runner.system.inputsourcedriver.json;

import java.util.Map;
import java.util.HashMap;

import io.operon.runner.model.OperonConfigs;
import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.OperonContextManager;
import static io.operon.runner.OperonContextManager.ContextStrategy;
import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.system.BaseSystem;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.FromStatement;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class JsonSystem extends BaseSystem implements InputSourceDriver {
    private static Logger log = LogManager.getLogger(JsonSystem.class);
    
    private OperonValue initialValue;
    private ObjectType jsonConfiguration;
    private boolean isRunning = false;
    private OperonContextManager ocm;
    public JsonSystem() {}

    public boolean isRunning() {
        return this.isRunning;
    }

    public OperonContextManager getOperonContextManager() {
        return this.ocm;
    }
    
    public void setOperonContextManager(OperonContextManager o) {
        this.ocm = o;
    }

    //
    // JsonSystem is runned only once (not in a loop), and result is printed into an output-stream.
    //
    public void start(OperonContextManager o) {
        OperonContext ctx = null;
        
        try {
            this.isRunning = true;
            if (this.getOperonContextManager() == null && o != null) {
                ocm = o;
                ctx = ocm.getOperonContext();
            }
            else if (o == null) {
                ctx = new OperonContext();
                ocm = new OperonContextManager(ctx, OperonContextManager.ContextStrategy.SINGLETON);
            }
            //System.out.println("START JsonSystem");
            //System.out.println("   >> Setting inital-value :: " + this.getInitialValue());
            ctx.setInitialValue(this.getInitialValue());
            OperonValue result = ctx.evaluateSelectStatement();
            //System.out.println("JSON-System, ready to output result");
            ctx.outputResult(result); // TODO: select output-strategy
            this.isRunning = false;
        } catch (OperonGenericException e) {
            //System.out.println("OGE Exception :: " + e.toString());
            log.error("Exception :: " + e.toString());
            StackTraceElement [] st = e.getStackTrace();
            for (int i = 0; i < st.length; i ++) {
                log.error("  StackTrace :: " + st[i]);
            }
            ctx.setException(e);
            if (e.getErrorValue() != null) {
                ctx.setErrorValue(e.getErrorValue());
            }
            ctx.outputError();
            this.isRunning = false;
        } catch (Exception ex) {
            //System.out.println("Exception :: " + ex.toString());
            OperonGenericException oge = new OperonGenericException(ex.getMessage());
            oge.setErrorMessage(ex.getMessage());
            ctx.setException(oge);
            if (oge.getErrorValue() != null) {
                ctx.setErrorValue(oge.getErrorValue());
            }
            this.isRunning = false;
        }
    }
    
    public void stop() { }
    
    public void setInitialValue(OperonValue initialValue) {
        this.initialValue = initialValue;
    }
    
    public OperonValue getInitialValue() {
        return this.initialValue;
    }
    
    public void setJsonConfiguration(ObjectType jsonConfig) { this.jsonConfiguration = jsonConfig; }
    public ObjectType getJsonConfiguration() { return this.jsonConfiguration; }
    
}