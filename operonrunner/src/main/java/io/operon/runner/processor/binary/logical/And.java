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

import java.util.Map;

import io.operon.runner.node.Node;
import io.operon.runner.node.Operator;
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
public class And extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    private String binaryOperator = "And";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocessLhs(statement, lhs); // Only evaluate LHS. For logical And RHS must be evaluated only is LHS is true.
        if (customBindingCheck(lhs, rhs, binaryOperator)) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        // TODO: does not type-check RHS:
        else if (lhsResult instanceof TrueType) {
            this.preprocessRhs(statement, rhs);
            if (rhsResult instanceof TrueType) {
                TrueType resultTrue = new TrueType(statement);
                statement.setCurrentValue(resultTrue);
                return resultTrue;
            }
            else if (rhsResult instanceof FalseType) {
                FalseType resultFalse = new FalseType(statement);
                statement.setCurrentValue(resultFalse);
                return resultFalse;
            }
            else {
                //:OFF:log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
                
                String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
                String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
                return ErrorUtil.createErrorValueAndThrow(statement,
                    "OPERATOR", 
                    "AND", 
                    "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType +
                        ", at line #" + this.getSourceCodeLineNumber() +
                        ". lhs value: " + lhs.toString() + ", rhs value: " + rhs.toString()
                
                );
            }
        }
        
        else if (lhsResult instanceof FalseType) {
            FalseType resultFalse = new FalseType(statement);
            statement.setCurrentValue(resultFalse);
            return resultFalse;
        }

        else {
            //:OFF:log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement,
                "OPERATOR", 
                "AND", 
                "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType +
                    ", at line #" + this.getSourceCodeLineNumber() +
                    ". lhs value: " + lhs.toString() + ", rhs value: " + rhs.toString()
            
            );
        }
        
    }

    public void preprocessLhs(Statement statement, Node lhs) throws OperonGenericException {
        //:OFF:log.debug("BaseBinaryNodeProcessor :: preprocess");
        OperonValue initialValue = statement.getCurrentValue();
        // @ might be null when executing only select statement (from unit-tests)
        if (initialValue != null) {
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: initialValue was null");
            initialValue = initialValue.copy();
        }
        
        lhsResult = lhs.evaluate();
        //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult bindings size :: " + lhsResult.getBindings().size());

        if (lhsResult instanceof OperonValue) {
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult is OperonValue --> unboxing");
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult bindings size before unboxing :: " + lhsResult.getBindings().size());
            Map<String, Operator> bindings = lhsResult.getBindings(); // HACK: for passing the bindings after evaluating (unboxing) OperonValue
            
            // Related to OperatorTests#35, might remove if not needed!
            boolean lhsDoBindings = lhsResult.getDoBindings();
            // TODO: should we copy bindings for rhs aswell?!
            lhsResult = lhsResult.evaluate();
            lhsResult.getBindings().putAll(bindings);
            
            // Test#35
            lhsResult.setDoBindings(lhsDoBindings);
            //:OFF:log.debug("  BaseBinaryNodeProcessor :: lhsresult bindings size after unboxing :: " + lhsResult.getBindings().size());
        }
        // Fork the initial value for rhs:
        statement.setCurrentValue(initialValue);
    }

}