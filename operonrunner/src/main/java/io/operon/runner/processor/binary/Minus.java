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
import io.operon.runner.model.exception.OperonGenericException;

/**
 * 
 * 
 * 
 */
public class Minus extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    private String binaryOperator = "-";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            NumberType result = new NumberType(statement);
            
            NumberType lhsResultJN = (NumberType) lhsResult;
            NumberType rhsResultJN = (NumberType) rhsResult;
            
            result.setDoubleValue(lhsResultJN.getDoubleValue() - rhsResultJN.getDoubleValue());

            if (lhsResultJN.getPrecision() == -1) {
                lhsResultJN.resolvePrecision();
            }
            if (rhsResultJN.getPrecision() == -1) {
                rhsResultJN.resolvePrecision();
            }
            
            byte precisionLhs = lhsResultJN.getPrecision();
            byte precisionRhs = rhsResultJN.getPrecision();
            byte resultPrecision = -1;
            if (precisionLhs <= precisionRhs) {
                resultPrecision = precisionRhs;
            }
            else {
                resultPrecision = precisionLhs;
            }
            result.setPrecision(resultPrecision);
            
            statement.setCurrentValue(result);
            return result;
        }
        
        else {
            log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "MINUS", "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType);
        }
    }

}