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
 * Remove module from OperonContext
 *
 */
public class ModuleRemove extends BaseArity1 implements Node, Arity1 {
    
    public ModuleRemove(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "remove", "namespace");
    }

    public OperonValue evaluate() throws OperonGenericException {
        OperonValue currentValue = this.getStatement().getCurrentValue();
        String namespace = ((StringType) this.getParam1().evaluate()).getJavaStringValue();
        
        OperonContext ctx = (OperonContext) this.getStatement().getOperonContext();
        if (ctx.getModules().get(namespace) != null) {
            ctx.getModules().remove(namespace);
        }
        return currentValue;
    }

}