/** OPERON-LICENSE **/
package io.operon.runner.processor.unary;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.processor.UnaryNodeProcessor;
import io.operon.runner.processor.BaseUnaryNodeProcessor;
import io.operon.runner.statement.Statement;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 * 
 * 
 * 
 */
public class Negate extends BaseUnaryNodeProcessor implements UnaryNodeProcessor {

    public OperonValue process(Statement statement, Node node) throws OperonGenericException {
        this.preprocess(statement, node); // unbox and copy bindings
        
        String unaryOperator = "Negate";
        
        if ( customBindingCheck(nodeResult, unaryOperator) ) {
            return doCustomBinding(statement, nodeResult, unaryOperator);
        }
        
        else if (nodeResult instanceof NumberType) {
            double value = ((NumberType) nodeResult).getDoubleValue();
            ((NumberType) nodeResult).setDoubleValue(-value);
            return nodeResult;
        }
        
        else {
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "NEGATE", "Not a number: " + nodeResult.getClass().getName());
        }
        
    }

}