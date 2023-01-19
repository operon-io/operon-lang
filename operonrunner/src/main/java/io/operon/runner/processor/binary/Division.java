/*
 *   Copyright 2022-2023, operon.io
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
import java.util.ArrayList;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.BaseBinaryNodeProcessor;
import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.processor.function.core.path.PathRetain;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.binary.logical.Eq;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

import com.google.gson.annotations.Expose;

/**
 * 
 * 
 * 
 */
public class Division extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    @Expose private String binaryOperator = "/";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            NumberType result = new NumberType(statement);
            
            NumberType lhsResultJN = (NumberType) lhsResult;
            NumberType rhsResultJN = (NumberType) rhsResult;
            double doubleResult = lhsResultJN.getDoubleValue() / rhsResultJN.getDoubleValue();
            if (Double.isInfinite(doubleResult)) {
                return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "DIVISION", "Division by zero");
            }
            String strResult = Double.toString(doubleResult);
            result.setDoubleValue(doubleResult);
            
            //
            // TODO: This could be unoptimal due to usage of String. However, this could 
            //       be more precise (no rounding errors) -> This solution should be evaluated.
            //
            if (lhsResultJN.getPrecision() != -1 && rhsResultJN.getPrecision() != -1
                && lhsResultJN.getPrecision() == 0 && rhsResultJN.getPrecision() == 0
                && strResult.split("\\.")[1].charAt(0) == '0') {
                result.setPrecision((byte) 0);
            }
            else {
                byte precision = NumberType.getPrecisionFromStr(strResult);
                result.setPrecision(precision);
            }
            
            statement.setCurrentValue(result);
            return result;
        }
        
        // intersection (with EQ-op)
        else if (lhsResult instanceof ArrayType && rhsResult instanceof ArrayType) {
            ArrayType lhsArray = (ArrayType) lhsResult;
            ArrayType rhsArray = (ArrayType) rhsResult;
            
            List<Node> lhsValues = lhsArray.getValues();
            List<Node> rhsValues = rhsArray.getValues();
            List<Node> resultArray = new ArrayList<Node>();
            
            for (int i = 0; i < lhsValues.size(); i ++) {
                for (int j = 0; j < rhsValues.size(); j ++) {
                    Eq eqOp = new Eq();
                    try {
                        Node eqOpResult = eqOp.process(statement, lhsValues.get(i), rhsValues.get(j));
                        if (eqOpResult instanceof TrueType) {
                            resultArray.add(lhsValues.get(i));
                        }
                    } catch (OperonGenericException oge) {
                        // Some EQ-operations are not defined.
                    }
                }
            }

            ArrayType result = new ArrayType(statement);
            result.getValues().addAll(resultArray);
            return result;
        }
        
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ArrayType) {
            // Run PathRetain
            ObjectType obj = (ObjectType) lhsResult;
            ArrayType pathsArray = (ArrayType) rhsResult;
            if (pathsArray.getValues().size() > 0) {
                Path p0 = (Path) pathsArray.getValues().get(0);
                p0.setObjLink(obj);
                statement.setCurrentValue(pathsArray);
                PathRetain pathRetain = new PathRetain(statement, new ArrayList<Node>());
                OperonValue result = pathRetain.evaluate();
                return result;
            }
            else {
                return lhsResult;
            }
        }
        
        // intersection based on any single value.
        // rhs may be any value (other than ArrayType)
        else if (lhsResult instanceof ArrayType) {
            ArrayType lhsArray = (ArrayType) lhsResult;
            List<Node> lhsValues = lhsArray.getValues();
            List<Node> resultArray = new ArrayList<Node>();
            
            for (int i = 0; i < lhsValues.size(); i ++) {
                Eq eqOp = new Eq();
                try {
                    Node eqOpResult = eqOp.process(statement, lhsValues.get(i), rhsResult);
                    if (eqOpResult instanceof TrueType) {
                        resultArray.add(lhsValues.get(i));
                    }
                } catch (OperonGenericException oge) {
                    // Some EQ-operations are not defined.
                }
            }

            ArrayType result = new ArrayType(statement);
            result.getValues().addAll(resultArray);
            return result;
        }
        
        // lhs may be any value (other than ArrayType),
        // in this case semantically same as checking if
        // the value exists in rhs-array
        else if (rhsResult instanceof ArrayType) {
            OperonValue lhsValue = (OperonValue) lhsResult;
            ArrayType rhsArray = (ArrayType) rhsResult;
            
            List<Node> rhsValues = rhsArray.getValues();
            List<Node> resultArray = new ArrayList<Node>();
            
            for (int i = 0; i < rhsValues.size(); i ++) {
                Eq eqOp = new Eq();
                try {
                    Node eqOpResult = eqOp.process(statement, lhsValue, rhsValues.get(i));
                    if (eqOpResult instanceof TrueType) {
                        //System.out.println("Equality found: " + lhsValue + " = " + rhsValues.get(i));
                        return new TrueType(statement);
                    }
                } catch (OperonGenericException oge) {
                    // Some EQ-operations are not defined.
                }
            }

            return new FalseType(statement);
        }
        
        else {
            //:OFF:log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement,
                "OPERATOR", 
                "DIVISION", 
                "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType +
                    ", at line #" + this.getSourceCodeLineNumber() +
                    ". lhs value: " + lhs.toString() + ", rhs value: " + rhs.toString()
            
            );
        }
        
    }

}