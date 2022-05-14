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
public class Multiplicate extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    private String binaryOperator = "*";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            NumberType result = new NumberType(statement);
            
            NumberType lhsResultJN = (NumberType) lhsResult;
            NumberType rhsResultJN = (NumberType) rhsResult;
            
            double doubleResult = lhsResultJN.getDoubleValue() * rhsResultJN.getDoubleValue();
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
        
        // TODO: define for arrays:
        // [1,2,3] * [4,5,6] = [[1,4], [2,5], [3,6]]
        
        else {
            //:OFF:log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement,
                "OPERATOR", 
                "MULTIPLICATE", 
                "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType +
                    ", at line #" + this.getSourceCodeLineNumber() +
                    ". lhs value: " + lhs.toString() + ", rhs value: " + rhs.toString()
            
            );
        }
        
    }

}