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

package io.operon.runner.node.type;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.Node;
import io.operon.runner.node.Range;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ArrayType extends OperonValue implements Node {
     // no logger 
    
    private List<Node> values; // Refactor to use OperonValue
    private int arrayId;
    
    public ArrayType(Statement stmt) {
        super(stmt);
        this.values = new ArrayList<Node>(); // RF
    }

    public void addValue(Node value) throws OperonGenericException {
        if (value instanceof EmptyType) {
            return;
        }
        
        else if (value instanceof Range) {
            //:OFF:log.debug("ArrayType :: Range -element found");
            Range range = (Range) value;
            
            try {            
                if (range.isEvaluated() == false) {
                    range = (Range) range.evaluate();
                    int rangeLhs = range.getEvaluatedLhs();
                    int rangeRhs = range.getEvaluatedRhs();

                    //:OFF:log.debug("    ArrayType :: Range :: rangeLhs :: " + rangeLhs);
                    //:OFF:log.debug("    ArrayType :: Range :: rangeRhs :: " + rangeRhs);
                    
                    int steps = 0;
                    if (rangeLhs >= rangeRhs) {
                        steps = rangeLhs - rangeRhs + 1;
                        // direction = -1;
                        
                        for (int i = 0; i < steps; i ++) {
                            NumberType n = new NumberType(this.getStatement());
                            n.setDoubleValue((double) (rangeLhs - i));
                            n.setPrecision((byte) 0);
                            this.addValue(n);
                        }
                    }
                    else {
                        steps = rangeRhs - rangeLhs + 1;
                        // direction = 1;
                        
                        for (int i = 0; i < steps; i ++) {
                            NumberType n = new NumberType(this.getStatement());
                            n.setDoubleValue((double) (rangeLhs + i));
                            n.setPrecision((byte) 0);
                            this.addValue(n);
                        }
                    }
                    
                    //:OFF:log.debug("  ArrayType :: Range :: added values.");
            }
            
            } catch (Exception e) {
               String type = "ARRAY";
               String code = "ADD";
               String message = "Could not add value";
               ErrorUtil.createErrorValueAndThrow(value.getStatement(), type, code, message);
            }

        }
        
        else {
            this.getValues().add(value);
        }
    }

    public ArrayType evaluate() throws OperonGenericException {
        if (this.getUnboxed() == true && this.getPreventReEvaluation() == true) {
            return (ArrayType) this;
        }
        
        OperonValue currentValue = null;
        if (this.getStatement().getCurrentValue() != null) {
            currentValue = this.getStatement().getCurrentValue();
        }
        else {
            // currentValue was null, so assign current-value with self-copy.
            currentValue = this;
        }

        ArrayType result = new ArrayType(this.getStatement());
        //result.setParentObj(this.getParentObj());
        //result.setPosition(this.getPosition());
        //result.setParentKey(this.getParentKey());
        List<Node> resultList = result.getValues();
        
        this.setPreventReEvaluation(true);
        
        for (Node value : this.getValues()) {
            if (value instanceof AtomicOperonValue == false) {
                value.getStatement().setCurrentValue(currentValue);
                value = value.evaluate();
            }
            else {
                this.setPreventReEvaluation(false);
            }

            if (value instanceof EmptyType == false && value.isEmptyValue() == false) {
                resultList.add(value);
            }
            
            if (currentValue != null) {
                this.getStatement().setCurrentValue(currentValue);
            }
        }
        
        result.setUnboxed(true);
        this.getStatement().setCurrentValue(result);
        return result;
    }
    
    public List<Node> getValues() {
        return this.values;
    }
    
    public void setValues(List<Node> values) {
        this.values = values;
    }
    
    // Used by array:sort, min, max
    public static class ArrayComparator implements java.util.Comparator, java.io.Serializable {
        
        private Node compareExpr;
        
        @Override
        public int compare(Object o1, Object o2) {
            if (o1 instanceof Node == false || o1 instanceof OperonValue == false) {
                throw new ClassCastException();
            }
            if (o2 instanceof Node == false || o2 instanceof OperonValue == false) {
                throw new ClassCastException();
            }
            try {
                OperonValue ev1 = (OperonValue) ((Node) o1).evaluate();
                OperonValue ev2 = (OperonValue) ((Node) o2).evaluate();
                this.getCompareExpr().getStatement().getRuntimeValues().put("$a", ev1);
                this.getCompareExpr().getStatement().getRuntimeValues().put("$b", ev2);
                Node testFunctionRefNode = this.getCompareExpr().evaluate();
                
                OperonValue testValueResult = null;
                
                if (testFunctionRefNode instanceof FunctionRef) {
                    FunctionRef testFnRef = (FunctionRef) testFunctionRefNode;
                    
                    testFnRef.getParams().clear();
                    testFnRef.getParams().add(ev1);
                    testFnRef.getParams().add(ev2);
                    // no current value for sorting (could be eg. the arrayToSort)
                    //testFnRef.setCurrentValueForFunction(valueToTest); // ops. took out currentValue
                    testValueResult = (OperonValue) testFnRef.invoke();
                }
                
                else if (testFunctionRefNode instanceof LambdaFunctionRef) {
                    LambdaFunctionRef testFnRef = (LambdaFunctionRef) testFunctionRefNode;
                    testFnRef.getParams().clear();
                    // NOTE: we cannot guarantee the order of keys that Map.keySet() returns,
                    //       therefore we must assume that the keys are named in certain manner.
                    testFnRef.getParams().put("$a", ev1);
                    testFnRef.getParams().put("$b", ev2);
                    // no current value for sorting (could be eg. the arrayToSort)
                    //testFnRef.setCurrentValueForFunction(valueToTest);
                    testValueResult = (OperonValue) testFnRef.invoke();
                }
                
                else {
                    // This was already evaluated
                    testValueResult = (OperonValue) testFunctionRefNode;
                }
                
                if (testValueResult instanceof NumberType) {
                    int compareResult = (int) ((NumberType) testValueResult).getDoubleValue();
                    return compareResult;
                }
                else {
                    throw new ClassCastException("Comparison error: Expected Number -value as one of [-1, 0, 1]");
                }
            } catch (OperonGenericException oge) {
                throw new ClassCastException(oge.getMessage());
            }
        }
        
        public void setCompareExpr(Node n) {
            this.compareExpr = n;
        }
        
        public Node getCompareExpr() {
            return this.compareExpr;
        }
    }
    
    public void setArrayId(int id) {
        this.arrayId = id;
    }
    
    public int getArrayId() {
        return this.arrayId;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < this.getValues().size(); i ++) {
            Node arrayValue = this.getValues().get(i);
            //assert (arrayValue != null): "ArrayType :: toString :: index at " + i + ", was null.";
            
            String arrayValueStr = arrayValue.toString();

            // Check if EmptyValue
            if (arrayValueStr.isEmpty()) {
                continue;
            }

            sb.append(arrayValueStr);
            if (i < this.getValues().size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        String result = sb.toString();
        sb = null;
        return result;
    }
    
    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        StringBuilder sb = new StringBuilder();
        sb.append(ofmt.arrayStart + System.lineSeparator());
        ofmt.spaces = (short) (ofmt.spaces + ofmt.spacing);
        for (int i = 0; i < this.getValues().size(); i ++) {
            //System.out.println("<<< ArrayType, i=" + i + ", arrayId=" + this.getArrayId());
            Node arrayValue = this.getValues().get(i);
            //assert (arrayValue != null): "ArrayType :: toString :: index at " + i + ", was null.";
            
            String arrayValueStr = ofmt.spaces() + arrayValue.toFormattedString(ofmt);

            // Check if EmptyValue
            if (arrayValueStr.isEmpty()) {
                continue;
            }

            sb.append(arrayValueStr);
            if (i < this.getValues().size() - 1) {
                sb.append("," + System.lineSeparator());
            }
        }
        ofmt.spaces = (short) (ofmt.spaces - ofmt.spacing); // reset spacing
        sb.append(System.lineSeparator() + ofmt.spaces() + ofmt.arrayEnd);
        String result = sb.toString();
        sb = null;
        return result;
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        //System.out.println("ArrayType :: toYamlString, spaces = " + yf.spaces + ", arrayId=" + this.getArrayId());
        if (yf == null) {
            //System.out.println("ArrayType :: toYamlString :: yf was null, creating new YamlFormatter");
            yf = new YamlFormatter();
        }
        StringBuilder sb = new StringBuilder();

        boolean spacingIncreased = false;
        for (int i = 0; i < this.getValues().size(); i ++) {
            Node arrayValue = this.getValues().get(i); //.evaluate();
            String arrayValueStr = null;
            
            // Sub-array:
            if (arrayValue instanceof ArrayType) {
                arrayValueStr = yf.spaces() + yf.arrayStart;
                yf.spaces = (short) (yf.spaces + yf.spacing);
                spacingIncreased = true;
                arrayValueStr += System.lineSeparator() + arrayValue.toYamlString(yf);
            }

            else {
                String appendValue = arrayValue.toYamlString(yf); // "    value"
                int markPos = 0; // --> "   - value"
                for (int ix = 0; ix < appendValue.length(); ix ++) {
                    if (appendValue.charAt(ix) != ' ') {
                        markPos = ix;
                        break;
                    }
                }
                if (markPos >= 2) {
                    arrayValueStr = appendValue.substring(0, markPos - 2) +
                                    yf.arrayStart + " " +
                                    appendValue.substring(markPos, appendValue.length());
                }
                else {
                    arrayValueStr = yf.spaces() + yf.arrayStart + " " + arrayValue.toYamlString(yf);
                }
            }
            
            if (spacingIncreased) {
                yf.spaces = (short) (yf.spaces - yf.spacing); // reset spacing
                spacingIncreased = false;
            }
            
            // Check if EmptyValue
            if (arrayValueStr.isEmpty()) {
                continue;
            }

            sb.append(arrayValueStr);
            if (i < this.getValues().size() - 1) {
                sb.append(System.lineSeparator());
            }
        }
        //sb.append(System.lineSeparator());
        String result = sb.toString();
        //sb = null;
        //System.out.println("ArrayType :: toYamlString :: RESULT :: [[[" + result + "]]]");
        return result;
    }

    @Override
    public String toTomlString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        StringBuilder sb = new StringBuilder();
        sb.append(ofmt.arrayStart + System.lineSeparator());
        ofmt.spaces = (short) (ofmt.spaces + ofmt.spacing);
        for (int i = 0; i < this.getValues().size(); i ++) {
            //System.out.println("<<< ArrayType, i=" + i + ", arrayId=" + this.getArrayId());
            Node arrayValue = this.getValues().get(i);
            //assert (arrayValue != null): "ArrayType :: toString :: index at " + i + ", was null.";
            
            String arrayValueStr = ofmt.spaces() + arrayValue.toTomlString(ofmt);

            // Check if EmptyValue
            if (arrayValueStr.isEmpty()) {
                continue;
            }

            sb.append(arrayValueStr);
            if (i < this.getValues().size() - 1) {
                sb.append("," + System.lineSeparator());
            }
        }
        ofmt.spaces = (short) (ofmt.spaces - ofmt.spacing); // reset spacing
        sb.append(System.lineSeparator() + ofmt.spaces() + ofmt.arrayEnd);
        String result = sb.toString();
        sb = null;
        return result;
    }

}