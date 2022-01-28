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
public class Or extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    @Override
    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        
        String binaryOperator = "Or";
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof TrueType && rhsResult instanceof TrueType) {
            TrueType resultTrue = new TrueType(statement);
            statement.setCurrentValue(resultTrue);
            return resultTrue;
        }

        else if (lhsResult instanceof TrueType && rhsResult instanceof FalseType) {
            TrueType resultTrue = new TrueType(statement);
            statement.setCurrentValue(resultTrue);
            return resultTrue;
        }

        else if (lhsResult instanceof FalseType && rhsResult instanceof TrueType) {
            TrueType resultTrue = new TrueType(statement);
            statement.setCurrentValue(resultTrue);
            return resultTrue;
        }

        else if (lhsResult instanceof FalseType && rhsResult instanceof FalseType) {
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }
        
        else {
            log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "OR", "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType);
        }
        
    }

}