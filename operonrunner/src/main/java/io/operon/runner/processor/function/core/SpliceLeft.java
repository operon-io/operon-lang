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
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// SpliceLeft is an inclusive operator.
//   Example: expression "[1, 2, 3] [:: 2]" will yield "[1, 2]"
//
public class SpliceLeft extends BaseArity1 implements Node, Arity1 {
     // no logger 
    
    private OperonValue valueToApplyAgainst;
    
    private Node spliceUntil; // expr, that is evaluated, and should yield NumberType
    
    public SpliceLeft(Statement stmnt, List<Node> params) throws OperonGenericException {
        super(stmnt);
        this.setParams(params, "spliceLeft", "until");
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER SpliceLeft.evaluate(). Stmt: " + this.getStatement().getId());

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
        
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceLeft", "Invalid input type.");
    }
    
    public ArrayType evaluateArray() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();
                
        if ((jsonValue instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: SpliceLeft expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceLeft", "Invalid input type. Expected Number.");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int arraySize = ((ArrayType) this.getValueToApplyAgainst()).getValues().size();
        
        if (intValue > arraySize) {
            intValue = arraySize;
        }
        
        ArrayType result = new ArrayType(this.getStatement());
        
        int endIndex = intValue;
        
        if (intValue < 0 && intValue > -arraySize) {
            endIndex = arraySize - (-intValue) + 1;
        }
        
        else if (intValue < 0 && intValue == -arraySize) {
            endIndex = 1;
        }
        
        else if (intValue < 0 && intValue < -arraySize) {
            endIndex = 0;
        }
        
        for (int i = 0; i < endIndex; i ++) {
            result.addValue( ((ArrayType) this.getValueToApplyAgainst()).getValues().get(i));
        }
        
        //:OFF:log.debug("    INT :: " + intValue);

        return result;
    }
    
    public ObjectType evaluateObj() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();
        
        if ((jsonValue instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: spliceUntil expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceLeft", "Invalid input type. Expected Number.");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int arraySize = ((ObjectType) this.getValueToApplyAgainst()).getPairs().size();
        
        if (intValue > arraySize) {
            intValue = arraySize;
        }
        
        ObjectType result = new ObjectType(this.getStatement());
        
        int endIndex = intValue;
        
        if (intValue < 0 && intValue > -arraySize) {
            endIndex = arraySize - (-intValue) + 1;
        }
        
        else if (intValue < 0 && intValue == -arraySize) {
            endIndex = 1;
        }
        
        else if (intValue < 0 && intValue < -arraySize) {
            endIndex = 0;
        }
        
        for (int i = 0; i < endIndex; i ++) {
            result.addPair( ((ObjectType) this.getValueToApplyAgainst()).getPairs().get(i));
        }
        
        //:OFF:log.debug("    INT :: " + intValue);
        
        return result;
    }

    public StringType evaluateString() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();

        if ((jsonValue instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: SpliceLeft expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceLeft", "Invalid input type. Expected Number.");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int strLength = ((StringType) this.getValueToApplyAgainst()).getJavaStringValue().length();
        
        if (intValue > strLength) {
            intValue = strLength;
        }
        
        StringType result = new StringType(this.getStatement());
        StringBuilder resultSb = new StringBuilder();

        int endIndex = intValue;

        if (intValue < 0 && intValue > -strLength) {
            endIndex = strLength - (-intValue) + 1;
        }
        
        else if (intValue < 0 && intValue == -strLength) {
            endIndex = 1;
        }

        else if (intValue < 0 && intValue < -strLength) {
            endIndex = 0;
        }

        for (int i = 0; i < endIndex; i ++) {
            resultSb.append(((StringType) this.getValueToApplyAgainst()).getJavaStringValue().charAt(i));
        }

        //:OFF:log.debug("    INT :: " + intValue);
        result.setFromJavaString(resultSb.toString());
        return result;
    }

    public Path evaluatePath() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();
                
        if ((jsonValue instanceof NumberType) == false ) {
            //:OFF:log.debug("ERROR: SpliceLeft expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceLeft", "Invalid input type. Expected Number.");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int arraySize = ((Path) this.getValueToApplyAgainst()).getPathParts().size();
        
        if (intValue > arraySize) {
            intValue = arraySize;
        }
        
        Path result = new Path(this.getStatement());
        
        int endIndex = intValue;
        
        if (intValue < 0 && intValue > -arraySize) {
            endIndex = arraySize - (-intValue) + 1;
        }
        
        else if (intValue < 0 && intValue == -arraySize) {
            endIndex = 1;
        }
        
        else if (intValue < 0 && intValue < -arraySize) {
            endIndex = 0;
        }
        
        for (int i = 0; i < endIndex; i ++) {
            result.getPathParts().add( ((Path) this.getValueToApplyAgainst()).getPathParts().get(i));
        }
        
        //:OFF:log.debug("    INT :: " + intValue);

        return result;
    }

    public void setValueToApplyAgainst(OperonValue value) {
        this.valueToApplyAgainst = value;
    }

    public OperonValue getValueToApplyAgainst() {
        return this.valueToApplyAgainst;
    }

}