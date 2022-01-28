/** OPERON-LICENSE **/
package io.operon.runner.processor.binary;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.processor.BaseBinaryNodeProcessor;
import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.statement.Statement;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * 
 * 
 */
public class Power extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {
    private static Logger log = LogManager.getLogger(Power.class);

    private String binaryOperator = "^";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        log.debug("Power OP");
        this.preprocess(statement, lhs, rhs);
        log.debug("Power OP :: done preprocessing");
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            log.debug("Power OP :: custom binding detected");
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            log.debug("Power OP :: regular op");
            NumberType result = new NumberType(statement);
            
            NumberType lhsResultJN = (NumberType) lhsResult;
            NumberType rhsResultJN = (NumberType) rhsResult;
            
            double doubleResult = Math.pow( lhsResultJN.getDoubleValue(), rhsResultJN.getDoubleValue() );
            result.setDoubleValue(doubleResult);
            
            if (lhsResultJN.getPrecision() != -1 && rhsResultJN.getPrecision() != -1
                && lhsResultJN.getPrecision() == 0 && rhsResultJN.getPrecision() == 0) {
                result.setPrecision((byte) 0);
            }
            else {
                byte precision = NumberType.getPrecisionFromStr(Double.toString(doubleResult));
                result.setPrecision(precision);
            }
            
            statement.setCurrentValue(result);
            return result;
        }
        
        else {
            log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "POWER", "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType);
        }
        
    }

}