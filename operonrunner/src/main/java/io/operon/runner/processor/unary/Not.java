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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 * 
 * 
 */
public class Not extends BaseUnaryNodeProcessor implements UnaryNodeProcessor {
    protected static Logger log = LogManager.getLogger(Not.class);
    
    public OperonValue process(Statement statement, Node node) throws OperonGenericException {
        log.debug("Not :: enter process");
        this.preprocess(statement, node); // unbox and copy bindings
        
        String unaryOperator = "Not";
        
        if ( customBindingCheck(nodeResult, unaryOperator) ) {
            return doCustomBinding(statement, nodeResult, unaryOperator);
        }
        
        else if (nodeResult instanceof TrueType) {
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if (nodeResult instanceof FalseType) {
            TrueType resultTrue = new TrueType(statement);
            statement.setCurrentValue(resultTrue);
            return resultTrue;
        }
        
        else {
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "NEGATE", "Not a boolean-value: " + nodeResult.getClass().getName());
        }
        
    }

}