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

package io.operon.runner.statement;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.OperonValueConstraint;
import io.operon.runner.node.FunctionStatementParam;
import io.operon.runner.ExceptionHandler;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FunctionStatement extends BaseStatement implements Statement {
     // no logger 
    
    private List<FunctionStatementParam> params; // used to check the param order and their names
    
    public FunctionStatement(Context ctx) {
        super(ctx);
        this.params = new ArrayList<FunctionStatementParam>();
        this.setId("FunctionStatement");
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        //System.out.println("=== FunctionStatement :: prototype: " + this.isPrototype() + ": @=" + this.getCurrentValue());
        OperonValue result = null;
        try {
            //
            // Eager-evaluate Let-statements:
            //
            for (Map.Entry<String, LetStatement> entry : this.getLetStatements().entrySet()) {
    		    LetStatement letStatement = (LetStatement) entry.getValue();
    		    letStatement.resolveConfigs();
    		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
    		        letStatement.evaluate();
    		    }
    	    }
    	    //
    	    // Evaluate function-statement
    	    //
            if (this.getNode().getStatement().getCurrentValue() == null) {
                if (this.getCurrentValue() != null) {
                    this.getNode().getStatement().setCurrentValue(this.getCurrentValue());
                }
                else {
                    System.err.println("FunctionStatement :: currentValue missing");
                }
            }
            result = (OperonValue) this.getNode().evaluate();
            this.synchronizeState();
        } catch (OperonGenericException e) {
            if (this.getExceptionHandler() != null) {
                //
                // Apply ExceptionHandler:
                //
                ExceptionHandler eh = this.getExceptionHandler();
                result = eh.evaluate(e);
            }
            else {
                //:OFF:log.debug("FunctionStatement :: exceptionHandler missing, throw to upper-level");
                //
                // Throw the exception to upper-level, since no ExceptionHandler was found:
                //
                throw e;
            }
        }
        this.setEvaluatedValue(result);
        if (this.getOperonValueConstraint() != null) {
            //
            // Apply constraint-check (for the function output):
            //
            OperonValueConstraint jvc = this.getOperonValueConstraint();
            OperonValueConstraint.evaluateConstraintAgainstOperonValue(this.getEvaluatedValue(), jvc);
        }
        return this.getEvaluatedValue();
    }
    
    private void synchronizeState() {
        for (LetStatement lstmnt : this.getLetStatements().values()) {
            if (lstmnt.getResetType() == LetStatement.ResetType.AFTER_SCOPE) {
                lstmnt.reset();
            }
        }
    }
    
    public List<FunctionStatementParam> getParams() {
        return this.params;
    }
    
    private void setParams(List<FunctionStatementParam> p) {
        this.params = p;
    }

}