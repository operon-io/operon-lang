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

package io.operon.runner.processor.function.core;

import java.util.List;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// 
//
public class SpliceRange extends BaseArity2 implements Node, Arity2 {
     // no logger 
    
    private OperonValue valueToApplyAgainst;
    
    public SpliceRange(Statement stmnt, List<Node> params) throws OperonGenericException {
        super(stmnt);
        this.setParams(params, "spliceRange", "start", "count");
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER SpliceRange.evaluate(). Stmt: " + this.getStatement().getId());
        
        if (this.getValueToApplyAgainst() == null) {
            this.setValueToApplyAgainst(this.getStatement().getCurrentValue());
        }
        
        if (this.getValueToApplyAgainst() instanceof ArrayType) {
            return this.evaluateArray();
        }
        
        else if (this.getValueToApplyAgainst() instanceof ObjectType) {
            return this.evaluateObj();
        }
        
        else if (this.getValueToApplyAgainst() instanceof StringType) {
            return this.evaluateString();
        }

        else if (this.getValueToApplyAgainst() instanceof Path) {
            return this.evaluatePath();
        }
        
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type.");
    }
    
    public ArrayType evaluateArray() throws OperonGenericException {
        OperonValue jsonValueLhs = this.getParam1().evaluate();
        OperonValue jsonValueRhs = this.getParam2().evaluate();

        if ((jsonValueLhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange lhs expr :: " + jsonValueLhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for left-hand side.");
            return null;
        }
        
        if ((jsonValueRhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange rhs expr :: " + jsonValueRhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for right-hand side.");
            return null;
        }
        
        int intValueLhs = (int) ((NumberType) jsonValueLhs).getDoubleValue();
        int intValueRhs = (int) ((NumberType) jsonValueRhs).getDoubleValue();
        int arraySize = ((ArrayType) this.getValueToApplyAgainst()).getValues().size();

        //System.out.println("intValueLhs :: " + intValueLhs);
        //System.out.println("intValueRhs :: " + intValueRhs);
        //System.out.println("arraySize :: " + arraySize);

        if (intValueLhs == 0) {
            //System.out.println("if :: 1");
            intValueLhs = 1;
        }

        else if (intValueLhs > arraySize) {
            //System.out.println("if :: 2");
            intValueLhs = arraySize;
        }
        
        else if (intValueLhs < 0 && intValueLhs > -arraySize) {
            // Compute the actual position
            //System.out.println("if :: 3 :: " + intValueLhs + ", " + arraySize);
            intValueLhs = arraySize + intValueLhs + 1;
        }
        
        else if (intValueLhs < -arraySize) {
            //System.out.println("if :: 4");
            intValueLhs = 1;
        }
        
        //System.out.println("intValueLhs :: " + intValueLhs);
        //System.out.println("intValueRhs :: " + intValueRhs);
        
        // calculate how many steps can be taken,
        //   case: steps go over the end of an array:
        if (intValueLhs + intValueRhs > arraySize) {
            //System.out.println("intValueRhs :: if 1");
            intValueRhs = arraySize - intValueLhs + 1;
        }
        
        //   case: steps go over the start of and array:
        else if (intValueRhs < 0 && (intValueLhs + intValueRhs) < 0) {
            //System.out.println("intValueRhs :: if 2");
            intValueRhs = -(arraySize - intValueLhs);
        }
        //System.out.println("intValueRhs :: " + intValueRhs);
        
        ArrayType result = new ArrayType(this.getStatement());
        
        int direction = 1;
        
        if (intValueRhs < 0) {
            direction = -1;
            intValueRhs = -intValueRhs;
        }
        
        // intValueRhs is the number of steps to take.
        for (int i = 0; i < intValueRhs; i ++) {
            // intValueLhs is the start position
            // direction*i is the offset-counter
            result.addValue( ((ArrayType) this.getValueToApplyAgainst()).getValues().get(intValueLhs + direction*i - 1));
        }
        
        return result;
    }
    
    public ObjectType evaluateObj() throws OperonGenericException {
        OperonValue jsonValueLhs = this.getParam1().evaluate();
        OperonValue jsonValueRhs = this.getParam2().evaluate();

        if ((jsonValueLhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange lhs expr :: " + jsonValueLhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for left-hand side.");
            return null;
        }
        
        if ((jsonValueRhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange rhs expr :: " + jsonValueRhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for right-hand side.");
            return null;
        }
        
        int intValueLhs = (int) ((NumberType) jsonValueLhs).getDoubleValue();
        int intValueRhs = (int) ((NumberType) jsonValueRhs).getDoubleValue();
        int arraySize = ((ObjectType) this.getValueToApplyAgainst()).getPairs().size();

        if (intValueLhs == 0) {
            intValueLhs = 1;
        }

        else if (intValueLhs > arraySize) {
            intValueLhs = arraySize;
        }
        
        else if (intValueLhs < 0 && intValueLhs > -arraySize) {
            // Compute the actual position
            intValueLhs = arraySize + intValueLhs + 1;
        }
        
        else if (intValueLhs < -arraySize) {
            intValueLhs = 1;
        }
        
        if (intValueLhs + intValueRhs > arraySize) {
            intValueRhs = arraySize - intValueLhs + 1;
        }
        
        else if (intValueRhs < 0 && (intValueLhs + intValueRhs) < 0) {
            intValueRhs = -(arraySize - intValueLhs);
        }
        
        ObjectType result = new ObjectType(this.getStatement());
        
        int direction = 1;
        
        if (intValueRhs < 0) {
            direction = -1;
            intValueRhs = -intValueRhs;
        }
        
        for (int i = 0; i < intValueRhs; i ++) {
            //:OFF:log.debug(">>>> i = " + i);
            result.addPair( ((ObjectType) this.getValueToApplyAgainst()).getPairs().get(intValueLhs + direction*i - 1));
        }
        
        return result;
    }

    public StringType evaluateString() throws OperonGenericException {
        OperonValue jsonValueLhs = this.getParam1().evaluate();
        OperonValue jsonValueRhs = this.getParam2().evaluate();

        if ((jsonValueLhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange lhs expr :: " + jsonValueLhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for left-hand side.");
            return null;
        }
        
        if ((jsonValueRhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange rhs expr :: " + jsonValueRhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for right-hand side.");
            return null;
        }
        
        int intValueLhs = (int) ((NumberType) jsonValueLhs).getDoubleValue();
        int intValueRhs = (int) ((NumberType) jsonValueRhs).getDoubleValue();
        int strLength = ((StringType) this.getValueToApplyAgainst()).getJavaStringValue().length();

        StringType result = new StringType(this.getStatement());
        StringBuilder resultSb = new StringBuilder();
        
        if (intValueLhs == 0) {
            intValueLhs = 1;
        }

        else if (intValueLhs > strLength) {
            intValueLhs = strLength;
        }
        
        else if (intValueLhs < 0 && intValueLhs > -strLength) {
            // Compute the actual position
            intValueLhs = strLength + intValueLhs + 1;
        }
        
        else if (intValueLhs < -strLength) {
            intValueLhs = 1;
        }
        
        if (intValueLhs + intValueRhs > strLength) {
            intValueRhs = strLength - intValueLhs + 1;
        }
        
        else if (intValueRhs < 0 && (intValueLhs + intValueRhs) < 0) {
            intValueRhs = -(strLength - intValueLhs);
        }
        
        int direction = 1;
        
        if (intValueRhs <= 0) {
            direction = -1;
            intValueRhs = -intValueRhs;
        }
        
        for (int i = 0; i < intValueRhs; i ++) {
            resultSb.append( ((StringType) this.getValueToApplyAgainst()).getJavaStringValue().charAt(intValueLhs + direction*i - 1));
        }
        result.setFromJavaString(resultSb.toString());
        return result;
    }

    public Path evaluatePath() throws OperonGenericException {
        OperonValue jsonValueLhs = this.getParam1().evaluate();
        OperonValue jsonValueRhs = this.getParam2().evaluate();

        if ((jsonValueLhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange lhs expr :: " + jsonValueLhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for left-hand side.");
            return null;
        }
        
        if ((jsonValueRhs instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceRange rhs expr :: " + jsonValueRhs.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRange", "Invalid input type. Expected Number for right-hand side.");
            return null;
        }
        
        int intValueLhs = (int) ((NumberType) jsonValueLhs).getDoubleValue();
        int intValueRhs = (int) ((NumberType) jsonValueRhs).getDoubleValue();
        int arraySize = ((Path) this.getValueToApplyAgainst()).getPathParts().size();

        if (intValueLhs == 0) {
            //System.out.println("if :: 1");
            intValueLhs = 1;
        }

        else if (intValueLhs > arraySize) {
            //System.out.println("if :: 2");
            intValueLhs = arraySize;
        }
        
        else if (intValueLhs < 0 && intValueLhs > -arraySize) {
            // Compute the actual position
            intValueLhs = arraySize + intValueLhs + 1;
        }
        
        else if (intValueLhs < -arraySize) {
            intValueLhs = 1;
        }
        
        // calculate how many steps can be taken,
        //   case: steps go over the end of an array:
        if (intValueLhs + intValueRhs > arraySize) {
            intValueRhs = arraySize - intValueLhs + 1;
        }
        
        //   case: steps go over the start of and array:
        else if (intValueRhs < 0 && (intValueLhs + intValueRhs) < 0) {
            intValueRhs = -(arraySize - intValueLhs);
        }
        
        Path result = new Path(this.getStatement());
        
        int direction = 1;
        
        if (intValueRhs < 0) {
            direction = -1;
            intValueRhs = -intValueRhs;
        }
        
        // intValueRhs is the number of steps to take.
        for (int i = 0; i < intValueRhs; i ++) {
            // intValueLhs is the start position
            // direction*i is the offset-counter
            result.getPathParts().add( ((Path) this.getValueToApplyAgainst()).getPathParts().get(intValueLhs + direction*i - 1));
        }
        
        return result;
    }

    public void setValueToApplyAgainst(OperonValue value) {
        this.valueToApplyAgainst = value;
    }
    
    public OperonValue getValueToApplyAgainst() {
        return this.valueToApplyAgainst;
    }
    
}