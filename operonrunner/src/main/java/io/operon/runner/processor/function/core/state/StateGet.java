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
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.State;

public class StateGet extends BaseArity2 implements Node, Arity2 {
    
    public StateGet(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        // param: value :: the value to get
        // param: initialValue :: the value to set and get if the state was not already set
        this.setParams(params, "get", "value", "initialValue");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            Node sKeyNode = this.getParam1().evaluate();
            String stateKey = ((StringType) sKeyNode.evaluate()).getJavaStringValue();
            this.getStatement().setCurrentValue(currentValue);
            OperonValue param2Value = null;
            
            if (this.getParam2() != null) {
                param2Value = this.getParam2().evaluate();
            }
            
            this.getStatement().setCurrentValue(currentValue);
            OperonContext ctx = ((OperonContext) this.getStatement().getOperonContext());
            State state = ctx.getState();
            OperonValue result = state.getStateValueByKey(stateKey, param2Value);            
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "state", e.getMessage());
        }
    }

}