/*
 *   Copyright 2022, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
     // no logger 

    private String binaryOperator = "+";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        //:OFF:log.debug("OP PLUS");
        
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
            
            ////:OFF:log.debug("    >> Both NumberType. Result :: " + result);
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
        // TODO: if pairs have the same key, then we could use RHS to override the LHS.
        //
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ObjectType) {
            ObjectType result = new ObjectType(statement);
            result = (ObjectType) lhsResult.copy();
            
            ObjectType addFrom = new ObjectType(statement);
            addFrom = (ObjectType) rhsResult.copy();
            
            List<PairType> pairs = addFrom.getPairs();
            
            for (PairType pair : pairs) {
                result.addPair(pair); // TODO: we could use method that allows overriding the existing key.
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding String and Raw, line #" + this.getSourceCodeLineNumber());
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Boolean, line #" + this.getSourceCodeLineNumber());
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Boolean, line #" + this.getSourceCodeLineNumber());
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Raw and Null, line #" + this.getSourceCodeLineNumber());
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Number and Raw, line #" + this.getSourceCodeLineNumber());
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Boolean and Raw, line #" + this.getSourceCodeLineNumber());
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Boolean and Raw, line #" + this.getSourceCodeLineNumber());
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
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "PLUS", "Error occured while adding Null and Raw, line #" + this.getSourceCodeLineNumber());
            }
            byte resultBytes[] = outputStream.toByteArray();
            result.setValue(resultBytes);
            statement.setCurrentValue(result);
            return result;
        }
        
        else {
            //:OFF:log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement,
                "OPERATOR", 
                "PLUS", 
                "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType +
                    ", at line #" + this.getSourceCodeLineNumber() +
                    ". lhs value: " + lhs.toString() + ", rhs value: " + rhs.toString()
            
            );
        }
        
    }

}