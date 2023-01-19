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
import java.util.ArrayList;

import io.operon.runner.ModuleContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.ExceptionHandler;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

import io.operon.runner.IrTypes;
import com.google.gson.annotations.Expose;

public class Choice extends AbstractNode implements Node {
     // no logger 
    
    @Expose private byte t = IrTypes.CHOICE;
    
    @Expose private List<Node> whens;
    @Expose private List<Node> thens;
    @Expose private Node otherwise;
    
    public Choice(Statement stmnt) {
        super(stmnt);
        this.whens = new ArrayList<Node>();
        this.thens = new ArrayList<Node>();
    }

    public void addWhen(Node when) {
        this.whens.add(when);
    }
    
    public void addThen(Node then) {
        this.thens.add(then);
    }
    
    public void setOtherwise(Node otherwise) {
        this.otherwise = otherwise;
    }
    
    public Node getOtherwise() {
        return this.otherwise;
    }
    
    public List<Node> getWhens() {
        return this.whens;
    }
    
    public List<Node> getThens() {
        return this.thens;
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER Choice.evaluate()");
        this.setEvaluatedValue(null); // Intialize the value, especially between map-operator calls.
        
        //
        // Eager-evaluate Let-statements:
        //
        for (java.util.Map.Entry<String, LetStatement> entry : this.getStatement().getLetStatements().entrySet()) {
		    LetStatement letStatement = (LetStatement) entry.getValue();
		    letStatement.resolveConfigs();
		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
		        letStatement.evaluate();
		    }
	    }

        OperonValue currentValue = null;
        currentValue = this.getStatement().getPreviousStatement().getCurrentValue();
        //System.out.println("CHOICE CV=" + currentValue + ", parentKey=" + currentValue.getParentKey() + ", stmt=" + this.getStatement());
        
        OperonValue currentValueCopy = currentValue;
        List<Node> whensList = this.getWhens();
        OperonValue result = null;
        try {
            for (int i = whensList.size() - 1; i >= 0; i --) {
                Node when = whensList.get(i);
                this.getStatement().setCurrentValue(currentValueCopy);
                
                OperonValue whenEvaluated = when.evaluate();
                //System.out.println("When evaluated=" + whenEvaluated);
                if (whenEvaluated instanceof TrueType) {
                    //System.out.println("--> true --> run Then");
                    Node then = this.getThens().get(i);
                    // Set current value for Then -expression.
                    if (currentValueCopy != null) {
                        this.getStatement().getRuntimeValues().put("@", currentValueCopy);
                    }
                    result = then.evaluate();
                    this.setEvaluatedValue(result);
                    break;
                }
            }
            //System.out.println("Result 1: " + result);
    
            //
            // If none of the When -conditions match, then execute Otherwise -expression.
            //
            if (this.getEvaluatedValue() == null && this.getOtherwise() != null) {
                //System.out.println("Choice: evaluate Otherwise");
                Node other = this.getOtherwise();
                this.getStatement().setCurrentValue(currentValueCopy);
                result = other.evaluate();
                //System.out.println("Otherwise result :: " + result);
                this.setEvaluatedValue(result);
            }
            
            else if (this.getEvaluatedValue() == null && this.getOtherwise() == null) {
                // NOTE: returns current-value to allow better chaining choices.
                this.setEvaluatedValue(currentValueCopy);
                result = currentValueCopy;
            }
        } catch (OperonGenericException e) {
            //System.out.println("Caught an Exception from Then-stmt!");
            
            if (this.getStatement().getExceptionHandler() != null) {
                //System.out.println("Apply ExceptionHandler");
                //
                // Apply ExceptionHandler:
                //
                ExceptionHandler eh = this.getStatement().getExceptionHandler();
                result = eh.evaluate(e);
            }
            else {
                //System.out.println("Throw to upper-level");
                //:OFF:log.debug("Choice :: exceptionHandler missing, throw to upper-level");
                //
                // Throw the exception to upper-level, since no ExceptionHandler was found:
                //
                throw e;
            }
        }
        
        this.synchronizeState();
        this.getStatement().getPreviousStatement().setCurrentValue(result);
        return result;
        
        
    }

    public void synchronizeState() {
        for (LetStatement lstmnt : this.getStatement().getLetStatements().values()) {
            if (lstmnt.getResetType() == LetStatement.ResetType.AFTER_SCOPE) {
                lstmnt.reset();
            }
        }
    }

    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}