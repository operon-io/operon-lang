/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Deepcopy the input-value
 *
 */
public class Copy extends BaseArity0 implements Node, Arity0 {
    private static Logger log = LogManager.getLogger(Copy.class);
    
    public Copy(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("copy");
    }

    public OperonValue evaluate() throws OperonGenericException {
        OperonValue currentValue = this.getStatement().getCurrentValue();
        OperonValue deepCopy = JsonUtil.copyOperonValueWithArray(currentValue);
        return deepCopy;
    }

}