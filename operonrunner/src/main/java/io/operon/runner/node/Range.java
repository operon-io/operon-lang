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

package io.operon.runner.node;

import java.util.List;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Range -node may only be used inside ArrayType.
//
public class Range extends OperonValue implements Node {
     // no logger 
    
    private Node lhs;
    private Node rhs;
    
    private int evaluatedLhs;
    private int evaluatedRhs;
    
    private boolean evaluated;
    
    public Range(Statement stmnt) {
        super(stmnt);
        this.evaluated = false;
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER Range.evaluate(). Stmt: " + this.getStatement().getId());
        
        if (this.isEvaluated()) {
            return this;
        }
        
        OperonValue lhsOperonValue = (OperonValue) this.getLhs().evaluate();
        
        //
        // Further unboxing, e.g.: ["f" => length() ... 3]
        // which evaluates the lhs-expr as OperonValue, but not NumberType.
        //
        if (lhsOperonValue.getUnboxed() == false) {
            lhsOperonValue = lhsOperonValue.unbox();
        }
        
        if ((lhsOperonValue instanceof NumberType) == false ) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "RANGE", "TYPE", "Left-hand side: number expected");
        }
        
        OperonValue rhsOperonValue = (OperonValue) this.getRhs().evaluate();
        
        // Further unboxing:
        if (rhsOperonValue.getUnboxed() == false) {
            rhsOperonValue = rhsOperonValue.unbox();
        }
        
        if ((rhsOperonValue instanceof NumberType) == false ) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "RANGE", "TYPE", "Right-hand side: number expected");
        }
        
        int lhsInt = (int) ((NumberType) lhsOperonValue).getDoubleValue();
        int rhsInt = (int) ((NumberType) rhsOperonValue).getDoubleValue();
        
        //:OFF:log.debug("LHS INT :: " + lhsInt);
        //:OFF:log.debug("RHS INT :: " + rhsInt);
        
        this.setEvaluatedLhs(lhsInt);
        this.setEvaluatedRhs(rhsInt);
        
        this.evaluated = true;
        this.setUnboxed(true);
        return this;
    }

    public static StringType filterString(StringType str, Range range) throws OperonGenericException {
        int rangeLhs = range.getEvaluatedLhs();
        int rangeRhs = range.getEvaluatedRhs();
        StringType result = new StringType(str.getStatement());
        
        int steps = 0;
        StringBuilder sb = new StringBuilder();
        String javastr = str.getJavaStringValue();
        if (rangeLhs >= rangeRhs) {
            steps = rangeLhs - rangeRhs + 1;
            // direction = -1;
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs - i;
                if (takeIndex < 0) {
                    takeIndex = javastr.length() + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                sb.append(javastr.charAt(takeIndex - 1));
            }
        }
        else {
            steps = rangeRhs - rangeLhs + 1;
            // direction = 1;
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs + i;
                if (takeIndex < 0) {
                    takeIndex = javastr.length() + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                sb.append(javastr.charAt(takeIndex - 1));
            }
        }
        result.setFromJavaString(sb.toString());
        return result;
    }
    
    public static ArrayType filterArray(ArrayType array, Range range) throws OperonGenericException {
        int rangeLhs = range.getEvaluatedLhs();
        int rangeRhs = range.getEvaluatedRhs();
        int arraySize = array.getValues().size();
        ArrayType result = new ArrayType(array.getStatement());
        
        int steps = 0;
        if (rangeLhs >= rangeRhs) {
            steps = rangeLhs - rangeRhs + 1;
            // direction = -1;
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs - i;
                if (takeIndex < 0) {
                    takeIndex = arraySize + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                if (takeIndex > arraySize) {
                    continue;
                }
                result.addValue(array.getValues().get(takeIndex - 1));
            }
        }
        else {
            steps = rangeRhs - rangeLhs + 1;
            // direction = 1;
            if (steps > arraySize) {
                steps = arraySize;
            }
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs + i;
                if (takeIndex < 0) {
                    takeIndex = arraySize + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                result.addValue(array.getValues().get(takeIndex - 1));
            }
        }
        //System.out.println("Range :: filterArray DONE :: " + result);
        return result;
    }
    
    public static ObjectType filterObj(ObjectType obj, Range range) throws OperonGenericException {
        int rangeLhs = range.getEvaluatedLhs();
        int rangeRhs = range.getEvaluatedRhs();
        int objSize = obj.getPairs().size();
        ObjectType result = new ObjectType(obj.getStatement());
        
        int steps = 0;
        if (rangeLhs >= rangeRhs) {
            steps = rangeLhs - rangeRhs + 1;
            // direction = -1;
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs - i;
                if (takeIndex < 0) {
                    takeIndex = objSize + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                result.addPair(obj.getPairs().get(takeIndex - 1));
            }
        }
        else {
            steps = rangeRhs - rangeLhs + 1;
            // direction = 1;
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs + i;
                if (takeIndex < 0) {
                    takeIndex = objSize + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                result.addPair(obj.getPairs().get(takeIndex - 1));
            }
        }
        return result;
    }
    
    public static Path filterPath(Path array, Range range) throws OperonGenericException {
        int rangeLhs = range.getEvaluatedLhs();
        int rangeRhs = range.getEvaluatedRhs();
        int arraySize = array.getPathParts().size();
        Path result = new Path(array.getStatement());
        
        int steps = 0;
        if (rangeLhs >= rangeRhs) {
            steps = rangeLhs - rangeRhs + 1;
            // direction = -1;
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs - i;
                if (takeIndex < 0) {
                    takeIndex = arraySize + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                if (takeIndex > arraySize) {
                    continue;
                }
                result.getPathParts().add(array.getPathParts().get(takeIndex - 1));
            }
        }
        else {
            steps = rangeRhs - rangeLhs + 1;
            // direction = 1;
            if (steps > arraySize) {
                steps = arraySize;
            }
            for (int i = 0; i < steps; i ++) {
                int takeIndex = rangeLhs + i;
                if (takeIndex < 0) {
                    takeIndex = arraySize + takeIndex + 1;
                }
                if (takeIndex <= 0) {
                    continue;
                }
                result.getPathParts().add(array.getPathParts().get(takeIndex - 1));
            }
        }
        //System.out.println("Range :: filterArray DONE :: " + result);
        return result;
    }
    
    public boolean isEvaluated() {
        return this.evaluated;
    }
    
    public void setLhs(Node lhs) {
        this.lhs = lhs;
    }
    
    public void setRhs(Node rhs) {
        this.rhs = rhs;
    }
    
    public Node getLhs() {
        return this.lhs;
    }
    
    public Node getRhs() {
        return this.rhs;
    }
    
    public void setEvaluatedLhs(int lhs) {
        this.evaluatedLhs = lhs;
    }
    
    public void setEvaluatedRhs(int rhs) {
        this.evaluatedRhs = rhs;
    }
    
    public int getEvaluatedLhs() {
        return this.evaluatedLhs;
    }
    
    public int getEvaluatedRhs() {
        return this.evaluatedRhs;
    }
    
    public String toString() {
        return String.valueOf(this.getEvaluatedLhs()) + "..." + String.valueOf(this.getEvaluatedRhs());
    }
}