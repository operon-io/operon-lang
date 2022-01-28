/** OPERON-LICENSE **/
package io.operon.runner.processor.binary;

import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.BaseBinaryNodeProcessor;
import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.statement.Statement;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * Plus-operator '+' and its semantics.
 * 
 * 
 */
public class Plus extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {
    private static Logger log = LogManager.getLogger(Plus.class);

    private String binaryOperator = "+";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        log.debug("OP PLUS");
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof StringType && rhsResult instanceof StringType) {
            StringType result = new StringType(statement);
            result.setFromJavaString(((StringType) lhsResult).getJavaStringValue() + ((StringType) rhsResult).getJavaStringValue());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            NumberType result = new NumberType(statement);
            
            NumberType lhsResultJN = (NumberType) lhsResult;
            NumberType rhsResultJN = (NumberType) rhsResult;
            
            result.setDoubleValue(lhsResultJN.getDoubleValue() + rhsResultJN.getDoubleValue());

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
            
            //log.debug("    >> Both NumberType. Result :: " + result);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof StringType) {
            StringType result = new StringType(statement);
            
            byte precision = ((NumberType) lhsResult).getPrecision();
            if (precision == -1) {
                ((NumberType) lhsResult).resolvePrecision();
            }
            result.setFromJavaString(((NumberType) lhsResult).toString() +  ((StringType) rhsResult).getJavaStringValue());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof StringType && rhsResult instanceof NumberType) {
            StringType result = new StringType(statement);
            
            byte precision = ((NumberType) rhsResult).getPrecision();
            if (precision == -1) {
                ((NumberType) rhsResult).resolvePrecision();
            }
            result.setFromJavaString(((StringType) lhsResult).getJavaStringValue() + ((NumberType) rhsResult).toString());
            statement.setCurrentValue(result);
            return result;
        }
        
        // 
        // Add any value into an array
        // 
        else if (lhsResult instanceof ArrayType) {
            ArrayType result = new ArrayType(statement);
            result = (ArrayType) lhsResult.copy();
            result.addValue((OperonValue) rhsResult);
            statement.setCurrentValue(result);
            return result;
        }
        
        //
        // {} + {}
        //
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ObjectType) {
            ObjectType result = new ObjectType(statement);
            result = (ObjectType) lhsResult.copy();
            
            ObjectType addFrom = new ObjectType(statement);
            addFrom = (ObjectType) rhsResult.copy();
            
            List<PairType> pairs = addFrom.getPairs();
            
            for (PairType pair : pairs) {
                result.addPair(pair);
            }
            statement.setCurrentValue(result);
            return result;
        }
        
        //
        // {} + [{}]
        //
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ArrayType) {
            ObjectType result = new ObjectType(statement);
            result = (ObjectType) lhsResult.copy();
            
            ArrayType addFromList = (ArrayType) rhsResult.copy();
            
            for (Node n : addFromList.getValues()) {
                // unbox OperonValue
                if (n instanceof OperonValue) {
                    n = n.evaluate();
                }
                if (n instanceof ObjectType) {
                    ObjectType addFrom = new ObjectType(statement);
                    addFrom = (ObjectType) ((ObjectType) n).copy();
                    List<PairType> pairs = addFrom.getPairs();
                    
                    for (PairType pair : pairs) {
                        result.addPair(pair);
                    }
                }
            }
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof RawValue && rhsResult instanceof RawValue) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((RawValue) lhsResult).getBytes();
            byte[] rhsBytes = ((RawValue) rhsResult).getBytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Raw");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof RawValue && rhsResult instanceof StringType) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((RawValue) lhsResult).getBytes();
            byte[] rhsBytes = ((StringType) rhsResult).getJavaStringValue().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and String");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof RawValue && rhsResult instanceof NumberType) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((RawValue) lhsResult).getBytes();
            byte[] rhsBytes = ((NumberType) rhsResult).toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Number");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof StringType && rhsResult instanceof TrueType) {
            StringType result = new StringType(statement);
            result.setFromJavaString(((StringType) lhsResult).getJavaStringValue() + ((TrueType) rhsResult).toString());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof StringType && rhsResult instanceof FalseType) {
            StringType result = new StringType(statement);
            result.setFromJavaString(((StringType) lhsResult).getJavaStringValue() + ((FalseType) rhsResult).toString());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof StringType && rhsResult instanceof NullType) {
            StringType result = new StringType(statement);
            result.setFromJavaString(((StringType) lhsResult).getJavaStringValue() + ((NullType) rhsResult).toString());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof EmptyType == false && rhsResult instanceof EmptyType) {
            statement.setCurrentValue(lhsResult);
            return lhsResult;
        }
        
        else if (lhsResult instanceof EmptyType && rhsResult instanceof EmptyType == false) {
            statement.setCurrentValue(rhsResult);
            return rhsResult;
        }
        
        else if (lhsResult instanceof EmptyType && rhsResult instanceof EmptyType) {
            statement.setCurrentValue(lhsResult);
            return rhsResult;
        }
        
        else if (lhsResult instanceof TrueType && rhsResult instanceof StringType) {
            StringType result = new StringType(statement);
            result.setFromJavaString(((TrueType) lhsResult).toString() + ((StringType) rhsResult).getJavaStringValue());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof FalseType && rhsResult instanceof StringType) {
            StringType result = new StringType(statement);
            result.setFromJavaString(((FalseType) lhsResult).toString() + ((StringType) rhsResult).getJavaStringValue());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof NullType && rhsResult instanceof StringType) {
            StringType result = new StringType(statement);
            result.setFromJavaString(((NullType) lhsResult).toString() + ((StringType) rhsResult).getJavaStringValue());
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof StringType && rhsResult instanceof RawValue) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((StringType) lhsResult).getJavaStringValue().getBytes(StandardCharsets.UTF_8);
            byte[] rhsBytes = ((RawValue) rhsResult).getBytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding String and Raw");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof RawValue && rhsResult instanceof TrueType) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((RawValue) lhsResult).getBytes();
            byte[] rhsBytes = ((TrueType) rhsResult).toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Boolean");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof RawValue && rhsResult instanceof FalseType) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((RawValue) lhsResult).getBytes();
            byte[] rhsBytes = ((FalseType) rhsResult).toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Boolean");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof RawValue && rhsResult instanceof NullType) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((RawValue) lhsResult).getBytes();
            byte[] rhsBytes = ((NullType) rhsResult).toString().getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Null");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof RawValue) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((NumberType) lhsResult).toString().getBytes(StandardCharsets.UTF_8);
            byte[] rhsBytes = ((RawValue) rhsResult).getBytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Number and Raw");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof TrueType && rhsResult instanceof RawValue) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((TrueType) lhsResult).toString().getBytes(StandardCharsets.UTF_8);
            byte[] rhsBytes = ((RawValue) rhsResult).getBytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Boolean and Raw");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof FalseType && rhsResult instanceof RawValue) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((FalseType) lhsResult).toString().getBytes(StandardCharsets.UTF_8);
            byte[] rhsBytes = ((RawValue) rhsResult).getBytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Boolean and Raw");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else if (lhsResult instanceof NullType && rhsResult instanceof RawValue) {
            RawValue result = new RawValue(statement);
            byte[] lhsBytes = ((NullType) lhsResult).toString().getBytes(StandardCharsets.UTF_8);
            byte[] rhsBytes = ((RawValue) rhsResult).getBytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            try {
                outputStream.write(lhsBytes);
                outputStream.write(rhsBytes);
            } catch (IOException ioe) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Null and Raw");
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else {
            log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType);
        }
        
    }

}