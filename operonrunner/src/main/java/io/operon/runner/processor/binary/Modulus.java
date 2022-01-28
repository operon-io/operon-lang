/** OPERON-LICENSE **/
package io.operon.runner.processor.binary;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.BaseBinaryNodeProcessor;
import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.processor.binary.logical.Eq;
import io.operon.runner.processor.function.core.path.PathReclude;
import io.operon.runner.statement.Statement;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 * 
 * 
 * 
 */
public class Modulus extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    private String binaryOperator = "%";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            NumberType result = new NumberType(statement);
            
            NumberType lhsResultJN = (NumberType) lhsResult;
            NumberType rhsResultJN = (NumberType) rhsResult;
            
            double doubleResult = lhsResultJN.getDoubleValue() % rhsResultJN.getDoubleValue();
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
        
        // complement of intersection (logically minus-operation) (done with EQ-op)
        else if (lhsResult instanceof ArrayType && rhsResult instanceof ArrayType) {
            ArrayType lhsArray = (ArrayType) lhsResult;
            ArrayType rhsArray = (ArrayType) rhsResult;
            
            //System.out.println("lhs=" + lhsArray);
            //System.out.println("rhs=" + rhsArray);
            
            List<Node> lhsValues = lhsArray.getValues();
            List<Node> rhsValues = rhsArray.getValues();
            List<Node> resultArray = new ArrayList<Node>();
            
            List<Integer> skipLhsPositions = new ArrayList<Integer>();
            List<Integer> skipRhsPositions = new ArrayList<Integer>();
            
            for (int i = 0; i < lhsValues.size(); i ++) {
                //System.out.println("=====================");
                boolean found = false;
                for (int j = 0; j < rhsValues.size(); j ++) {
                    Eq eqOp = new Eq();
                    try {
                        //System.out.println("compare: " + lhsValues.get(i) + " :: " + rhsValues.get(j));
                        Node eqOpResult = eqOp.process(statement, lhsValues.get(i), rhsValues.get(j));
                        if (eqOpResult instanceof TrueType) {
                            found = true;
                            skipRhsPositions.add(j);
                            //System.out.println("  --> FOUND");
                            break;
                        }
                        else {
                            //System.out.println("  - x");
                        }
                    } catch (OperonGenericException oge) {
                        // Some EQ-operations are not defined.
                        // In this case also complement.
                    }
                }
                if (found == false) {
                    //System.out.println("  --> ADDING");
                    skipLhsPositions.add(i);
                    resultArray.add(lhsValues.get(i));
                }
                else {
                    //System.out.println("  --> SKIPPING");
                }
            }

            for (int i = 0; i < rhsValues.size(); i ++) {
                //System.out.println("=====================");
                boolean found = false;
                if (skipRhsPositions.contains(i)) {
                    continue;
                }
                //System.out.println("* Check rhs :: " + i + " --> " + rhsValues.get(i));
                for (int j = 0; j < lhsValues.size(); j ++) {
                    if (skipLhsPositions.contains(j)) {
                        //System.out.println("  --> SKIP :: " + j + " --> " + lhsValues.get(j));
                        continue;
                    }
                    Eq eqOp = new Eq();
                    try {
                        //System.out.println("compare: " + rhsValues.get(i) + " :: " + lhsValues.get(j));
                        Node eqOpResult = eqOp.process(statement, rhsValues.get(i), lhsValues.get(j));
                        if (eqOpResult instanceof TrueType) {
                            found = true;
                            //System.out.println("  --> FOUND");
                            break;
                        }
                        else {
                            //System.out.println("  - x");
                        }
                    } catch (OperonGenericException oge) {
                        // Some EQ-operations are not defined.
                        // In this case also complement.
                    }
                }
                if (found == false) {
                    //System.out.println("  --> ADDING");
                    skipLhsPositions.add(i);
                    resultArray.add(rhsValues.get(i));
                }
                else {
                    //System.out.println("  --> SKIPPING");
                }
            }

            ArrayType result = new ArrayType(statement);
            result.getValues().addAll(resultArray);
            return result;
        }
        
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ArrayType) {
            // Run PathReclude
            ObjectType obj = (ObjectType) lhsResult;
            ArrayType pathsArray = (ArrayType) rhsResult;
            if (pathsArray.getValues().size() > 0) {
                Path p0 = (Path) pathsArray.getValues().get(0);
                p0.setObjLink(obj);
                statement.setCurrentValue(pathsArray);
                PathReclude pathReclude = new PathReclude(statement);
                OperonValue result = pathReclude.evaluate();
                return result;
            }
            else {
                return lhsResult;
            }
        }
        
        // complement of intersection based on any single value.
        // rhs may be any value (other than ArrayType)
        else if (lhsResult instanceof ArrayType) {
            ArrayType lhsArray = (ArrayType) lhsResult;
            OperonValue rhsValue = (OperonValue) rhs.evaluate();
            
            List<Node> lhsValues = lhsArray.getValues();
            List<Node> resultArray = new ArrayList<Node>();
            
            for (int i = 0; i < lhsValues.size(); i ++) {
                Eq eqOp = new Eq();
                boolean found = false;
                try {
                    Node eqOpResult = eqOp.process(statement, lhsValues.get(i), rhsValue);
                    if (eqOpResult instanceof TrueType) {
                        found = true;
                    }
                } catch (OperonGenericException oge) {
                    // Some EQ-operations are not defined.
                    // In this case also complement.
                }
                if (found == false) {
                    resultArray.add(lhsValues.get(i));
                }
            }

            ArrayType result = new ArrayType(statement);
            result.getValues().addAll(resultArray);
            return result;
        }
        
        else {
            log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement, "OPERATOR", "MODULUS", "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType);
        }
        
    }

}