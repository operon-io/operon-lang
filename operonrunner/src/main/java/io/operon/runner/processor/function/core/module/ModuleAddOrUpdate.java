/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.module;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import io.operon.runner.OperonContext;
import io.operon.runner.OperonTestsContext;
import io.operon.runner.Context;
import io.operon.runner.OperonRunner;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Add or update module (compiles and adds into OperonContext)
 *
 */
public class ModuleAddOrUpdate extends BaseArity1 implements Node, Arity1 {
    
    public ModuleAddOrUpdate(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "addOrUpdate", "namespace");
    }

    // String or Error
    public OperonValue evaluate() throws OperonGenericException {
        RawValue currentValue = null;
        try {
            currentValue = (RawValue) this.getStatement().getCurrentValue();
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "module:" + this.getFunctionName(), e.getMessage());
        }
        String namespace = ((StringType) this.getParam1().evaluate()).getJavaStringValue();
        boolean loadOk = true;
        String loadStatus = "success";
        try {
            OperonContext ctx = (OperonContext) this.getStatement().getOperonContext();
            String moduleAsString = new String(currentValue.getBytes());
            String moduleFilePath = "./";
            boolean allowUpdate = true;
            ModuleAdd.addModule(ctx, null, moduleAsString, moduleFilePath, namespace, allowUpdate);
        } catch (Exception e) {
            loadOk = false;
            loadStatus = e.getMessage();
        }
        OperonValue result = new StringType(this.getStatement());
        ((StringType) result).setFromJavaString(namespace);
        if (!loadOk) {
            result = new ErrorValue(this.getStatement());
            ((ErrorValue) result).setMessage(loadStatus);
            ((ErrorValue) result).setType("MODULE");
        }
        
        return result;
    }

}