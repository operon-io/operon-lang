/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Wait by given milliseconds amount
 *
 */
public class Wait extends BaseArity1 implements Node, Arity1 {
    private static Logger log = LogManager.getLogger(Wait.class);
    
    public Wait(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "wait", "millis");
    }

    public OperonValue evaluate() throws OperonGenericException {
        OperonValue cv = this.getStatement().getCurrentValue();
        long millis = (long) ((NumberType) this.getParam1().evaluate()).getDoubleValue();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), "Interrupted.");
        }
        return cv;
    }

}