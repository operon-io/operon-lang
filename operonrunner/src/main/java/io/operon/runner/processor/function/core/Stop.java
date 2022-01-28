/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.BreakSelect;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Stop the flow, return the current-value
 *
 */
public class Stop extends BaseArity0 implements Node, Arity0 {
    
    public Stop(Statement statement) {
        super(statement);
        this.setFunctionName("stop");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        BreakSelect breakSelect = new BreakSelect(currentValue);
        breakSelect.setBreakType((short) 1);
        throw breakSelect;
    }

}