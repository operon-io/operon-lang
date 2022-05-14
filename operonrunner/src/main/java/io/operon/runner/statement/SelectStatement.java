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

package io.operon.runner.statement;

import java.util.Map;
import java.util.HashMap;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.ExceptionHandler;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.exception.BreakSelect;

import org.apache.logging.log4j.LogManager;

public class SelectStatement extends BaseStatement implements Statement {
     // no logger 
    
    private Node configs;
    private boolean configsResolved = false;
    
    public SelectStatement(Context ctx) {
        super(ctx);
        this.setId("SelectStatement");
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("Select-statement :: evaluate()");
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
    	    // Evaluate the statement
    	    // 
            result = this.getNode().evaluate();
        } 
        catch (BreakSelect bs) {
            //System.out.println("Caught BreakSelect");
            throw bs;
        }
        catch (OperonGenericException e) {
            //System.out.println("Caught Exception: " + e);
            if (this.getExceptionHandler() != null) {
                //
                // Apply ExceptionHandler:
                //
                
                //System.out.println("SelectStatement :: apply ExceptionHandler: " + e.getMessage());
                //System.out.println("  CV: " + e.getCurrentValue());
                //:OFF:log.debug("SelectStatement :: apply ExceptionHandler: " + e.getMessage());
                ExceptionHandler eh = this.getExceptionHandler();
                eh.setException(e);
                result = eh.evaluate(e);
                //:OFF:log.debug("SelectStatement :: applied ExceptionHandler");
            }
            else {
                //:OFF:log.debug("SelectStatement :: exceptionHandler missing, throw to upper-level");
                //System.out.println("error is null? " + e);
                //:OFF:log.debug("  >> error: " + e.getClass().getName() + ", message: " + e.getMessage());
                //
                // Throw the exception to upper-level, since no ExceptionHandler was found:
                //
                throw e;
            }
        }
        this.setEvaluatedValue(result);
        
        if (this.getOperonValueConstraint() != null) {
            // Apply constraint-check:
            OperonValueConstraint c = this.getOperonValueConstraint();
            OperonValueConstraint.evaluateConstraintAgainstOperonValue(this.getEvaluatedValue(), c);
        }
        ////:OFF:log.debug("Select statement return :: " + this.getEvaluatedValue());
        return this.getEvaluatedValue();
    }

    public void setConfigs(Node conf) {
        this.configs = conf;
    }
    
    public ObjectType getConfigs() throws OperonGenericException {
        if (this.configs == null) {
            return new ObjectType(this);
        }
        this.configs = (ObjectType) this.configs.evaluate();
        return (ObjectType) this.configs;
    }

    public void resolveConfigs() throws OperonGenericException {
        //:OFF:log.debug("Select, resolveConfigs");
        ObjectType conf = this.getConfigs();
        if (conf == null) {
            return;
        }
        for (PairType pair : conf.getPairs()) {
            String key = pair.getKey();
            switch (key.toLowerCase()) {
                case "\"prettyprint\"":
                    break;
                default:
                    break;
            }
        }
        this.configsResolved = true;
        // resolving configs changes the currentValue, therefore we set it back to null,
        // assuming that resolveConfigs is done prior to evalute() -call.
        this.setCurrentValue(null);
    }

}