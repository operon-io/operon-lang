/** OPERON-LICENSE **/
package io.operon.runner;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

import io.operon.runner.util.RandomUtil;
import io.operon.runner.model.InputSource;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.FromStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.Operator;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * Context -class acts as a container for the query elements.
 * 
 */
public class ModuleContext extends BaseContext implements Context {
    private static Logger log = LogManager.getLogger(ModuleContext.class);
    
    public ModuleContext() throws IOException {
        super();
    }

    @Override
    public synchronized void setIsReady(boolean rdy, String message) {
        this.getParentContext().setIsReady(rdy, message);
    }
    
    @Override
    public void setException(OperonGenericException e) {
        this.getParentContext().setException(e);
    }

    @Override
    public void setErrorValue(ErrorValue err) {
        this.getParentContext().setErrorValue(err);
    }

}