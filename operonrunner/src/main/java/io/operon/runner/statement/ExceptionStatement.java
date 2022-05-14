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
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.node.type.*;
import io.operon.runner.ExceptionHandler;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ExceptionStatement extends BaseStatement implements Statement {
     // no logger 
    
    private OperonGenericException exception;
    
    public ExceptionStatement(Context ctx) {
        super(ctx);
        this.setId("ExceptionStatement");
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("Exception-statement :: evaluate()");
        //System.out.println("Exception-statement :: evaluate()");

        Statement exceptionStatement = this.getNode().getStatement();

        //
        // Evaluate the exceptionHandlingExpr
        //
        if (exceptionStatement.getCurrentValue() == null) {
            exceptionStatement.setCurrentValue(this.getCurrentValue());
        }
        //System.err.println(">> cv :: " + this.getCurrentValue());
        
        //
        // Eager-evaluate Let-statements:
        //
        for (Map.Entry<String, LetStatement> entry : exceptionStatement.getLetStatements().entrySet()) {
		    LetStatement letStatement = (LetStatement) entry.getValue();
		    letStatement.resolveConfigs();
		    if (letStatement.getEvaluateType() == LetStatement.EvaluateType.EAGER) {
		        letStatement.evaluate();
		    }
	    }

	    //
	    // Evaluate Exception-statement (HandleException -node's expr)
	    //
	    Node exceptionHandlerExpr = this.getNode();
	    
	    if (exceptionHandlerExpr == null) {
	        //:OFF:log.debug(">> ExceptionStatement :: exceptionHandlerExpr was null!");
	    }
	    OperonValue exceptionValue = null;
	    try {
            exceptionValue = exceptionHandlerExpr.evaluate();
            this.synchronizeState();
	    } catch (OperonGenericException oge) {
	        //:OFF:log.debug("ExceptionStatement :: Catch OperonGenericException");
	        //System.err.println("Registered ExceptionHandler :: " + exceptionHandlerExpr.getExpr());
	        //System.err.println("oge msg :: " + oge.getMessage());
	        //System.err.println("oge errorValue :: " + oge.getErrorValue());
            
            //System.out.println("> Set error handled");
            this.setErrorHandled(exceptionHandlerExpr.getStatement().isErrorHandled());
    
            //
            // exceptionHandlingExpr may set the isErrorHandled to true
            // 
            if (this.isErrorHandled() == false) {
                //System.out.println("RETHROWING");
                OperonValue rethrowValue = null;
                if (oge.getErrorValue() instanceof ErrorValue) {
                    rethrowValue = oge.getErrorValue().getErrorJson();
                }
                else {
                    rethrowValue = oge.getErrorValue();
                }
                this.rethrow(rethrowValue);
            }
	    } catch (Exception e) {
	        //:OFF:log.debug("ExceptionStatement :: Catch Exception :: do nothing");
	        //System.err.println("Registered ExceptionHandler :: " + exceptionHandlerExpr.getExpr());
	        //System.err.println("ERROR SIGNAL :: " + e.getMessage());
            this.setErrorHandled(exceptionHandlerExpr.getStatement().isErrorHandled());
            
            ErrorValue errorValue = ErrorUtil.createErrorValue(exceptionStatement, "EXCEPTION", "UNRECOGNIZED", "Error occured");
            //
            // exceptionHandlingExpr may set the isErrorHandled to true
            // 
            if (this.isErrorHandled() == false) {
                this.rethrow(errorValue);
            }
	    }
	    //System.out.println("> Set evaluated value");
        this.setEvaluatedValue(exceptionValue);
        
        if (this.getOperonValueConstraint() != null) {
            //
            // Apply constraint-check:
            //
            OperonValueConstraint c = this.getOperonValueConstraint();
            OperonValueConstraint.evaluateConstraintAgainstOperonValue(this.getEvaluatedValue(), c);
        }
        //:OFF:log.debug("ExceptionStatement evaluatedValue :: " + this.getEvaluatedValue());

        //
        // Transfer state to this statement:
        //
        //   --> This was hard-to-find bug: this statement, and the statement for the node-executed are different.
        
        //System.out.println("TRANFER STATE:::::::");
        //System.out.println("===> exceptionValue: " + exceptionValue.getStatement().isErrorHandled());
        //System.out.println("===> this: " + this.isErrorHandled());
        //System.out.println("===> exceptionHandlerExpr: " + exceptionHandlerExpr.getStatement().isErrorHandled());
        //this.setErrorHandled(exceptionValue.getStatement().isErrorHandled());
        
        //System.out.println("> Set error handled");
        this.setErrorHandled(exceptionHandlerExpr.getStatement().isErrorHandled());

        //
        // exceptionHandlingExpr may set the isErrorHandled to true
        // 
        if (this.isErrorHandled() == false) {
            this.rethrow(exceptionValue);
        }
        //:OFF:log.debug("ExceptionStatement evaluate() done");
        return this.getEvaluatedValue();
    }

    private void rethrow(OperonValue exceptionValue) throws OperonGenericException {
        //:OFF:log.debug("ExceptionStatement :: rethrow");
        //System.out.println("ExceptionStatement :: rethrow :: " + exceptionValue);
        OperonGenericException e = null;
        if (this.getException() != null) {
            e = this.getException();
        }
        else {
            e = new OperonGenericException(exceptionValue);
        }
        
        ErrorValue errorValue = new ErrorValue(((OperonValue) exceptionValue).getStatement());
        errorValue.setErrorJson((OperonValue) exceptionValue);
        this.getOperonContext().setErrorValue(errorValue);
        this.getOperonContext().setException(e);
        //
        // Rethrow Exception only if this is not the final-errorHandler
        //
        if (this.getPreviousStatement() instanceof FromStatement == false) {
            //:OFF:log.debug("ExceptionStatement :: rethrow exception");
            e.setCurrentValue(exceptionValue);
            //:OFF:log.debug("ExceptionStatement :: rethrowing now");
            throw e;
        }
        else {
            System.err.println("Caught unhandled Error: " + e.getMessage());
        }
    }

    private void synchronizeState() {
        for (LetStatement lstmnt : this.getLetStatements().values()) {
            if (lstmnt.getResetType() == LetStatement.ResetType.AFTER_SCOPE) {
                lstmnt.reset();
            }
        }
    }

    public void setException(OperonGenericException oge) {
        this.exception = oge;
    }

    public OperonGenericException getException() {
        return this.exception;
    }

}