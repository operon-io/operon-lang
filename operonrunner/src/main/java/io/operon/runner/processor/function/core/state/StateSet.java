/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.state;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.State;

public class StateSet extends BaseArity1 implements Node, Arity1 {
    
    public StateSet(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "set", "value");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            Node sKeyNode = this.getParam1().evaluate();
            String stateKey = ((StringType) sKeyNode.evaluate()).getJavaStringValue();
            State state = ((OperonContext) this.getStatement().getOperonContext()).getState();
            state.setStateKeyAndValue(stateKey, currentValue);
            
            // Do not modify the current-value
            return currentValue;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "state", e.getMessage());
        }
    }

}