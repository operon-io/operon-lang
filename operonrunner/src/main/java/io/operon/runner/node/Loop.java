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

package io.operon.runner.node; 
 
import java.util.List; 
import java.util.ArrayList; 
import java.util.stream.Collectors;
import java.io.IOException;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.model.exception.BreakLoopException;
import io.operon.runner.model.exception.ContinueLoopException;
import io.operon.runner.model.streamvaluewrapper.*;
import io.operon.runner.ModuleContext;
import io.operon.runner.processor.BinaryNodeProcessor; 
import io.operon.runner.statement.Statement; 
import io.operon.runner.statement.LetStatement; 
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
/** 
 *  Loop (CONST_ID ':' expr) ':' expr END
 *  Example: 0 Loop ($i: [1...3]): @ + $i; #&gt; 6
 *  
 *  NOTE: Loop-pattern does not set any attributes.
 * 
 */ 
public class Loop extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(Loop.class); 

    private String valueRef;
    private Node loopExpr;
    private Node loopIteratorExpr;
    private Node configs;

    public Loop(Statement stmnt) {
        super(stmnt);
        //this.evaluated = false;
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("Loop :: evaluate()");
        assert (this.getLoopExpr() != null) : "Loop.evaluate() : loopExpr was null";
        
        OperonValue currentValue = this.getStatement().getPreviousStatement().getCurrentValue();
        
        //System.out.println(">> CV=" + currentValue);
        
        //System.out.println(">> CV COPIED");
        currentValue = currentValue.evaluate();

        //System.out.println("Loop :: evaluate() :: cv :: " + currentValue);

        Info info = this.resolveConfigs();

        OperonValue result = currentValue;
        
        OperonValue iterator = this.getLoopIteratorExpr().evaluate();
        
        if (iterator.getUnboxed() == false) {
            iterator = iterator.unbox();
        }
        
        if (iterator instanceof NumberType) {
            result = this.handleNumberIterator((NumberType) iterator, currentValue);
        }
        
        else if (iterator instanceof Range) {
            result = this.handleRangeIterator((Range) iterator, currentValue);
        }
        
        else if (iterator instanceof ArrayType) {
            result = this.handleArrayIterator((ArrayType) iterator, currentValue);
        }
        
        else if (iterator instanceof ObjectType) {
            result = this.handleObjectIterator((ObjectType) iterator, currentValue);
        }
        
        else if (iterator instanceof FunctionRef) {
            result = this.handleFunctionRefIterator((FunctionRef) iterator, currentValue);
        }
        
        else if (iterator instanceof LambdaFunctionRef) {
            result = this.handleLambdaFunctionRefIterator((LambdaFunctionRef) iterator, currentValue);
        }
        
        else if (iterator instanceof StreamValue) {
            result = this.handleStreamIterator((StreamValue) iterator, currentValue);
        }
        
        else {
            System.err.println("Could not iterate :: " + iterator.getClass().getName());
        }

        this.getStatement().getPreviousStatement().setCurrentValue(result);
        return result;
    }

    // 
    // Select [] Loop ($i: 3): @ + $i;
    // #> [1, 2, 3]
    // 
    public OperonValue handleNumberIterator(NumberType it, OperonValue currentValue) throws OperonGenericException {
        Node loopExpression = this.getLoopExpr();
        Double itValue = it.getDoubleValue();
        OperonValue result = currentValue;
        //System.out.println("CV=" + currentValue);
        
        for (int i = 1; i <= itValue.intValue(); i ++) {
            NumberType nextValue = new NumberType(currentValue.getStatement());
            nextValue.setDoubleValue((double) i);
            nextValue.setPrecision((byte) 0);
            loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextValue);
            loopExpression.getStatement().setCurrentValue(result);
            
            //
            // Eager-evaluate Let-statements:
            //
            this.eagerEvaluateLetStatements();
            
            try {
                result = loopExpression.evaluate();
                //System.out.println("result=" + result);
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
            this.synchronizeState();
        }
        return result;
    }

    // 
    // Select [] Loop ($i: 2...3): @ + $i;
    // #> [2, 3]
    // 
    public OperonValue handleRangeIterator(Range it, OperonValue currentValue) throws OperonGenericException {
        Node loopExpression = this.getLoopExpr();
        int rangeLhs = it.getEvaluatedLhs();
        int rangeRhs = it.getEvaluatedRhs();
        OperonValue result = currentValue;
        
        int steps = 0;
        if (rangeLhs >= rangeRhs) {
            // direction = -1;
            for (int i = rangeLhs; i >= rangeRhs; i --) {
                NumberType nextValue = new NumberType(currentValue.getStatement());
                nextValue.setDoubleValue((double) i);
                nextValue.setPrecision((byte) 0);
                loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextValue);
                loopExpression.getStatement().setCurrentValue(result);
                
                //
                // Eager-evaluate Let-statements:
                //
                this.eagerEvaluateLetStatements();
                
                try {
                    result = loopExpression.evaluate();
                    //System.out.println("result=" + result);
                } catch (BreakLoopException ble) {
                    break;
                } catch (ContinueLoopException cle) {
                    this.synchronizeState();
                    continue;
                }
                this.synchronizeState();
            }
        }
        else {
            for (int i = rangeLhs; i <= rangeRhs; i ++) {
                NumberType nextValue = new NumberType(currentValue.getStatement());
                nextValue.setDoubleValue((double) i);
                nextValue.setPrecision((byte) 0);
                loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextValue);
                loopExpression.getStatement().setCurrentValue(result);
                
                //
                // Eager-evaluate Let-statements:
                //
                this.eagerEvaluateLetStatements();
                
                try {
                    result = loopExpression.evaluate();
                    //System.out.println("result=" + result);
                } catch (BreakLoopException ble) {
                    break;
                } catch (ContinueLoopException cle) {
                    this.synchronizeState();
                    continue;
                }
                this.synchronizeState();
            }
        }
        
        return result;
    }

    // 
    // Select [1, true] Loop ($i: ["foo", 2, false]): @ + $i;
    // #> [1, true, "foo", 2, false]
    // 
    public OperonValue handleArrayIterator(ArrayType it, OperonValue currentValue) throws OperonGenericException {
        //System.out.println("Loop :: handleArrayIterator");
        Node loopExpression = this.getLoopExpr();
        List<Node> itValues = it.getValues();
        OperonValue result = currentValue;
        
        for (int i = 0; i < itValues.size(); i ++) {
            OperonValue nextValue = itValues.get(i).evaluate();
            loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextValue);
            // NOTE: original currentValue is not sent, because Loop-pattern should modify it with each iteration.
            //System.out.println("  Loop :: set the CV :: " + result);
            loopExpression.getStatement().setCurrentValue(result);
            
            //
            // Eager-evaluate Let-statements:
            //
            this.eagerEvaluateLetStatements();
            
            try {
                result = loopExpression.evaluate();
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
            this.synchronizeState();
        }
        return result;
    }

    public OperonValue handleObjectIterator(ObjectType it, OperonValue currentValue) throws OperonGenericException {
        Node loopExpression = this.getLoopExpr();
        List<PairType> itPairs = it.getPairs();
        OperonValue result = currentValue;
        
        for (int i = 0; i < itPairs.size(); i ++) {
            PairType nextPair = (PairType) itPairs.get(i).evaluate();
            ObjectType nextObj = new ObjectType(it.getStatement());
            nextObj.addPair(nextPair);
            
            loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextObj);
            loopExpression.getStatement().setCurrentValue(result);
            
            //
            // Eager-evaluate Let-statements:
            //
            this.eagerEvaluateLetStatements();
            
            try {
                result = loopExpression.evaluate();
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
            this.synchronizeState();
        }
        return result;
    }

    public OperonValue handleFunctionRefIterator(FunctionRef it, OperonValue currentValue) throws OperonGenericException {
        //System.out.println("handleFunctionRefIterator :: start");
        Node loopExpression = this.getLoopExpr();
        OperonValue result = currentValue;
        //System.out.println("CV=" + currentValue);

        it.setCurrentValueForFunction(result);
        OperonValue nextValue = it.invoke();
        
        //System.out.println("handleFunctionRefIterator :: nv = " + nextValue);
        
        while (nextValue != null && (nextValue instanceof EndValueType == false)) {
            loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextValue);
            loopExpression.getStatement().setCurrentValue(result);
            
            //
            // Eager-evaluate Let-statements:
            //
            this.eagerEvaluateLetStatements();
            
            try {
                result = loopExpression.evaluate();
                //System.out.println("result=" + result);
                
                //it.getParams().clear();
                // TODO: add args
                //it.getParams().add();
                it.setCurrentValueForFunction(result);
                nextValue = it.invoke();
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
            this.synchronizeState();
        }
        return result;
    }

    public OperonValue handleLambdaFunctionRefIterator(LambdaFunctionRef it, OperonValue currentValue) throws OperonGenericException {
        //System.out.println("handleLambdaFunctionRefIterator :: start");
        Node loopExpression = this.getLoopExpr();
        OperonValue result = currentValue;
        //System.out.println("CV=" + currentValue);

        it.setCurrentValueForFunction(result);
        OperonValue nextValue = it.invoke();
        
        //System.out.println("handleFunctionRefIterator :: nv = " + nextValue);
        
        while (nextValue != null && (nextValue instanceof EndValueType == false)) {
            loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextValue);
            loopExpression.getStatement().setCurrentValue(result);
            
            //
            // Eager-evaluate Let-statements:
            //
            this.eagerEvaluateLetStatements();
            
            try {
                result = loopExpression.evaluate();
                //System.out.println("result=" + result);
                
                //it.getParams().clear();
                // TODO: add args
                //it.getParams().add();
                it.setCurrentValueForFunction(result);
                nextValue = it.invoke();
            } catch (BreakLoopException ble) {
                break;
            } catch (ContinueLoopException cle) {
                this.synchronizeState();
                continue;
            }
            this.synchronizeState();
        }
        return result;
    }

    // 
    // Select $streamFile: -> readfile:{"fileName": "data.json"}; [] Loop ($i: $streamValue): @ + $i;
    // 
    // 
    public OperonValue handleStreamIterator(StreamValue it, OperonValue currentValue) throws OperonGenericException {
        Node loopExpression = this.getLoopExpr();
        StreamValueWrapper svw = it.getStreamValueWrapper();
        if (svw == null) {
            System.err.println("ERROR :: StreamValueWrapper was null!");
        }
        OperonValue result = currentValue;
        
        if (svw.supportsJson()) {
            log.debug("Stream supportsJson");
            OperonValue nextValue = null;
            do {
                nextValue = svw.readJson();
                if (nextValue instanceof EndValueType) {
                    break;
                }
                loopExpression.getStatement().getRuntimeValues().put(this.getValueRef(), nextValue);
                loopExpression.getStatement().setCurrentValue(result);
                
                //
                // Eager-evaluate Let-statements:
                //
                this.eagerEvaluateLetStatements();
                
                try {
                    result = loopExpression.evaluate();
                } catch (BreakLoopException ble) {
                    break;
                } catch (ContinueLoopException cle) {
                    this.synchronizeState();
                    continue;
                }
                this.synchronizeState();
            } while (nextValue != null && nextValue instanceof EndValueType == false);
        }
        
        else {
            System.out.println("Stream does NOT support Json");
            result = ErrorUtil.createErrorValue(currentValue.getStatement(), "Loop", "Stream", "Stream is not JSON-iterable.");
        }
        
        return result;
    }

    private void eagerEvaluateLetStatements() throws OperonGenericException {
        for (java.util.Map.Entry<String, LetStatement> entry : this.getStatement().getLetStatements().entrySet()) {
		    LetStatement letStatement = (LetStatement) entry.getValue();
		    letStatement.resolveConfigs();
		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
		        letStatement.evaluate();
		    }
	    }
    }

    private void synchronizeState() {
        for (LetStatement lstmnt : this.getStatement().getLetStatements().values()) {
            if (lstmnt.getResetType() == LetStatement.ResetType.AFTER_SCOPE) {
                lstmnt.reset();
            }
        }
    }

    public void setValueRef(String valueRef) {
        this.valueRef = valueRef;
    }

    public String getValueRef() {
        return this.valueRef;
    }

    public void setLoopIteratorExpr(Node fiExpr) {
        this.loopIteratorExpr = fiExpr;
    }
    
    public Node getLoopIteratorExpr() {
        return this.loopIteratorExpr;
    }

    public void setLoopExpr(Node fExpr) {
        this.loopExpr = fExpr;
    }
    
    public Node getLoopExpr() {
        return this.loopExpr;
    }

    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this.getStatement());
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

    public Info resolveConfigs() throws OperonGenericException {
        Info info = new Info();
        
        if (this.configs == null) {
            return info;
        }
        
        for (PairType pair : this.getConfigs().getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                // Check the pair's evaluatedValue
                default:
                    break;
            }
        }
        return info;
    }

    private class Info {

    }

}