/** OPERON-LICENSE **/
package io.operon.runner.processor.binary.logical;

import java.util.List;

import io.operon.runner.node.Node;
import io.operon.runner.node.Operator;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.BaseBinaryNodeProcessor;
import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.statement.Statement;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.StringUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * 
 * 
 */
public class Eq extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {
    private static Logger log = LogManager.getLogger(Eq.class);
    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        
        log.debug(" EQ :: LHS bindings size :: " + lhs.getBindings().size());
        log.debug(" EQ :: LHS-pp bindings size :: " + lhsResult.getBindings().size());
        log.debug(" EQ :: RHS-pp bindings size :: " + rhsResult.getBindings().size());
        log.debug(">>>> LHS-binding-mode :: " + lhs.getDoBindings());
        
        String binaryOperator = "=";
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            if ( ((NumberType) lhsResult).getDoubleValue() == ((NumberType) rhsResult).getDoubleValue() ) {
                log.debug("EQ : Return TrueType");
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }
            log.debug("EQ : Return FalseType 1 :: " + ((NumberType) lhsResult).getDoubleValue() + ", " + ((NumberType) rhsResult).getDoubleValue());
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if (lhsResult instanceof StringType && rhsResult instanceof StringType) {
            String lhsResultStr = ((StringType) lhsResult).getStringValue();
            String rhsResultStr = ((StringType) rhsResult).getStringValue();
            if ( lhsResultStr.equals( rhsResultStr ) ) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }

        else if (lhsResult instanceof RawValue && rhsResult instanceof RawValue) {
            byte[] lhsResultBytes = ((RawValue) lhsResult).getBytes();
            byte[] rhsResultBytes = ((RawValue) rhsResult).getBytes();
            if ( StringUtil.byte_array_equals(lhsResultBytes, rhsResultBytes)) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }

        /*
        else if (lhsResult instanceof StringType && rhsResult instanceof NumberType) {
            if ( ((StringType) lhsResult).getJavaStringValue().length() ==  ((NumberType) rhsResult).getDoubleValue().intValue() ) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        */
        
        else if (lhsResult instanceof NullType && rhsResult instanceof NullType) {
            if ( ((NullType) lhsResult).toString().equals( ((NullType) rhsResult).toString()) ) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ObjectType) {
            //
            // TODO: first sort the keys (obj's PairType lists). Avoid mutating the order.
            //
            // Create a copy of values because otherwise the expressions would get re-evaluated
            // in the JsonUtil's isIdentical, causing possible wrong results (e.g. when we have
            // a current-value in an array or object)
            if (JsonUtil.isIdentical((ObjectType) lhsResult.copy(), (ObjectType) rhsResult.copy())) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }

        /*
        else if (lhsResult instanceof ObjectType && rhsResult instanceof NumberType) {
            int lhsSize = ((ObjectType) lhsResult).getPairs().size();
            int rhsCount = ((NumberType) rhsResult).getDoubleValue().intValue();
            
            if (lhsSize == rhsCount) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }
            
            else {
                FalseType resultFalse = new FalseType(statement);
                statement.setCurrentValue(resultFalse);
                return resultFalse;
            }
        }
        */
        
        else if (lhsResult instanceof ArrayType && rhsResult instanceof ArrayType) {
            // Create a copy of values because otherwise the expressions would get re-evaluated
            // in the JsonUtil's isIdentical, causing possible wrong results (e.g. when we have
            // a current-value in an array or object)
            if (JsonUtil.isIdentical((ArrayType) lhsResult.copy(), (ArrayType) rhsResult.copy())) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }

        /*
        else if (lhsResult instanceof ArrayType && rhsResult instanceof NumberType) {
            int lhsSize = ((ArrayType) lhsResult).getValues().size();
            int rhsCount = ((NumberType) rhsResult).getDoubleValue().intValue();
            
            if (lhsSize == rhsCount) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }
            
            else {
                FalseType resultFalse = new FalseType(statement);
                statement.setCurrentValue(resultFalse);
                return resultFalse;
            }
        }
        */
        
        else if (lhsResult instanceof TrueType && rhsResult instanceof TrueType) {
            log.debug("EQ : Return TrueType");
            TrueType resultTrue = new TrueType(statement);
            statement.setCurrentValue(resultTrue);
            return resultTrue;
        }

        else if (lhsResult instanceof FalseType && rhsResult instanceof TrueType) {
            log.debug("EQ : Return FalseType 2");
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if (lhsResult instanceof TrueType && rhsResult instanceof FalseType) {
            log.debug("EQ : Return FalseType 3");
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if (lhsResult instanceof FalseType && rhsResult instanceof FalseType) {
            log.debug("EQ : Return TrueType");
            TrueType resultTrue = new TrueType(statement);
            statement.setCurrentValue(resultTrue);
            return resultTrue;
        }
        
        else if (lhsResult instanceof EmptyType && rhsResult instanceof EmptyType) {
            TrueType resultTrue = new TrueType(statement);
            statement.setCurrentValue(resultTrue);
            return resultTrue;
        }
        
        else if (lhsResult instanceof EmptyType && (rhsResult instanceof EmptyType == false)) {
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if ((lhsResult instanceof EmptyType == false) && rhsResult instanceof EmptyType) {
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if ((lhsResult instanceof io.operon.runner.node.type.Path) && rhsResult instanceof io.operon.runner.node.type.Path) {
            io.operon.runner.node.type.Path pathLhs = (io.operon.runner.node.type.Path) lhsResult;
            io.operon.runner.node.type.Path pathRhs = (io.operon.runner.node.type.Path) rhsResult;
            if (pathLhs.equals(pathRhs)) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }
            else {
                FalseType resultFalse = new FalseType(statement);
                statement.setCurrentValue(resultFalse);
                return resultFalse;
            }
        }
        
        /*
        else if ((lhsResult instanceof io.operon.runner.node.type.Path) && rhsResult instanceof StringType) {
            io.operon.runner.node.type.Path pathLhs = (io.operon.runner.node.type.Path) lhsResult;
            StringType rhsString = (StringType) rhsResult;
            if (pathLhs.toString().equals(rhsString.getStringValue())) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }
            else {
                FalseType resultFalse = new FalseType(statement);
                statement.setCurrentValue(resultFalse);
                return resultFalse;
            }
        }
        */
        
        /*
        else if ((lhsResult instanceof io.operon.runner.node.type.Path) && rhsResult instanceof NumberType) {
            io.operon.runner.node.type.Path pathLhs = (io.operon.runner.node.type.Path) lhsResult;
            NumberType rhsNumber = (NumberType) rhsResult;
            if (pathLhs.getPathParts().size() == rhsNumber.getDoubleValue().intValue()) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }
            else {
                FalseType resultFalse = new FalseType(statement);
                statement.setCurrentValue(resultFalse);
                return resultFalse;
            }
        }
        */
        
        else {
            // When comparing dynamically calculated result,
            // OperonValue and NumberType
            // TODO: if OperonValue, then should compute the correct type first (both: lhs and rhs)
            log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            //System.out.println("Create error: lhsType: " + lhsType + ", rhsType: " + rhsType);
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "EQ", "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType);
        }
        
    }

}