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

package io.operon.runner.processor.binary.logical;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.NullType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.ArrayType;
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
public class Lt extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        
        String binaryOperator = "<";
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            if ( ((NumberType) lhsResult).getDoubleValue() < ((NumberType) rhsResult).getDoubleValue() ) {
                return new TrueType(statement);
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
/*
        else if (lhsResult instanceof StringType && rhsResult instanceof NumberType) {
            if ( ((StringType) lhsResult).getJavaStringValue().length() <  ((NumberType) rhsResult).getDoubleValue().intValue() ) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
*/
        else if (lhsResult instanceof StringType && rhsResult instanceof StringType) {
            if ( ((StringType) lhsResult).compareTo( ((StringType) rhsResult)) < 0) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }

            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ObjectType) {
            int lhsSize = ((ObjectType) lhsResult).getPairs().size();
            int rhsSize = ((ObjectType) rhsResult).getPairs().size();
            
            if (lhsSize < rhsSize) {
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
        else if (lhsResult instanceof ObjectType && rhsResult instanceof NumberType) {
            int lhsSize = ((ObjectType) lhsResult).getPairs().size();
            int rhsCount = ((NumberType) rhsResult).getDoubleValue().intValue();
            
            if (lhsSize < rhsCount) {
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
            int lhsSize = ((ArrayType) lhsResult).getValues().size();
            int rhsSize = ((ArrayType) rhsResult).getValues().size();
            
            if (lhsSize < rhsSize) {
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
        else if (lhsResult instanceof ArrayType && rhsResult instanceof NumberType) {
            int lhsSize = ((ArrayType) lhsResult).getValues().size();
            int rhsCount = ((NumberType) rhsResult).getDoubleValue().intValue();
            
            if (lhsSize < rhsCount) {
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
            if (pathLhs.getPathParts().size() < rhsNumber.getDoubleValue().intValue()) {
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
            log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "LT", "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType);
        }

    }

}