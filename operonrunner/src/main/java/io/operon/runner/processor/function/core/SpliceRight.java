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

package io.operon.runner.processor.function.core;

import java.util.List;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Splicing -node may only be used for ArrayType.
//
public class SpliceRight extends BaseArity1 implements Node, Arity1 {
    private static Logger log = LogManager.getLogger(SpliceRight.class);
    
    private OperonValue valueToApplyAgainst;
    
    public SpliceRight(Statement stmnt, List<Node> params) throws OperonGenericException {
        super(stmnt);
        this.setParams(params, "spliceRight", "start");
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER SpliceRight.evaluate(). Stmt: " + this.getStatement().getId());
        
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
        
        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRight", "Invalid input type");
        return null;
    }
    
    public ArrayType evaluateArray() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();
        
        if ((jsonValue instanceof NumberType) == false ) {
            log.debug("ERROR: spliceUntil expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRight", "Number expected");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int arraySize = ((ArrayType) this.getValueToApplyAgainst()).getValues().size();
        int startIndex = intValue;
        
        if (intValue > 0) {
            startIndex = intValue - 1;
        }
        
        if (intValue < 0 && intValue > -arraySize) {
            startIndex = arraySize + intValue;
        }
        
        else if (intValue < 0 && intValue <= -arraySize) {
            startIndex = 0;
        }
        
        ArrayType result = new ArrayType(this.getStatement());
        
        for (int i = startIndex; i < arraySize; i ++) {
            result.addValue( ((ArrayType) this.getValueToApplyAgainst()).getValues().get(i));
        }
        
        log.debug("    INT :: " + intValue);
        
        return result;
    }
    
    public ObjectType evaluateObj() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();
        
        if ((jsonValue instanceof NumberType) == false ) {
            log.debug("ERROR: spliceUntil expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRight", "Number expected");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int arraySize = ((ObjectType) this.getValueToApplyAgainst()).getPairs().size();
        int startIndex = intValue;
        
        if (intValue > 0) {
            startIndex = intValue - 1;
        }
        
        if (intValue < 0 && intValue > -arraySize) {
            startIndex = arraySize + intValue;
        }
        
        else if (intValue < 0 && intValue <= -arraySize) {
            startIndex = 0;
        }
        
        ObjectType result = new ObjectType(this.getStatement());
        
        for (int i = startIndex; i < arraySize; i ++) {
            result.addPair( ((ObjectType) this.getValueToApplyAgainst()).getPairs().get(i));
        }
        
        log.debug("    INT :: " + intValue);
        
        return result;
    }
    
    public StringType evaluateString() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();

        if ((jsonValue instanceof NumberType) == false ) {
            log.debug("ERROR: SpliceRight expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRight", "Number expected");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int strLength = ((StringType) this.getValueToApplyAgainst()).getJavaStringValue().length();

        StringType result = new StringType(this.getStatement());
        StringBuilder resultSb = new StringBuilder();
        int startIndex = intValue;
        
        if (intValue > 0) {
            startIndex = intValue - 1;
        }
        
        if (intValue < 0 && intValue > -strLength) {
            startIndex = strLength + intValue;
        }
        
        else if (intValue < 0 && intValue <= -strLength) {
            startIndex = 0;
        }
        
        for (int i = startIndex; i < strLength; i ++) {
            resultSb.append( ((StringType) this.getValueToApplyAgainst()).getJavaStringValue().charAt(i));
        }
        log.debug("    INT :: " + intValue);
        result.setFromJavaString(resultSb.toString());
        return result;
    }

    public Path evaluatePath() throws OperonGenericException {
        OperonValue jsonValue = this.getParam1().evaluate();
        
        if ((jsonValue instanceof NumberType) == false ) {
            log.debug("ERROR: spliceUntil expr :: " + jsonValue.getClass().getName());
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "spliceRight", "Number expected");
            return null;
        }
        
        int intValue = (int) ((NumberType) jsonValue).getDoubleValue();
        int arraySize = ((Path) this.getValueToApplyAgainst()).getPathParts().size();
        int startIndex = intValue;
        
        if (intValue > 0) {
            startIndex = intValue - 1;
        }
        
        if (intValue < 0 && intValue > -arraySize) {
            startIndex = arraySize + intValue;
        }
        
        else if (intValue < 0 && intValue <= -arraySize) {
            startIndex = 0;
        }
        
        Path result = new Path(this.getStatement());
        
        for (int i = startIndex; i < arraySize; i ++) {
            result.getPathParts().add( ((Path) this.getValueToApplyAgainst()).getPathParts().get(i));
        }
        
        log.debug("    INT :: " + intValue);
        
        return result;
    }

    public void setValueToApplyAgainst(OperonValue value) {
        this.valueToApplyAgainst = value;
    }
    
    public OperonValue getValueToApplyAgainst() {
        return this.valueToApplyAgainst;
    }
    
}