/** OPERON-LICENSE **/
package io.operon.runner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;

import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.node.ValueRef;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.ExceptionStatement;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ExceptionHandler {
    private static Logger log = LogManager.getLogger(ExceptionHandler.class);
    
    private Node exceptionHandlerExpr;
    private OperonGenericException exception;
    
    public ExceptionHandler() { }
    
    public void setExceptionHandlerExpr(Node ehe) {
        this.exceptionHandlerExpr = ehe;
    }
    
    public Node getExceptionHandlerExpr() {
        return this.exceptionHandlerExpr;
    }

    public void setException(OperonGenericException oge) {
        this.exception = oge;
    }

    public OperonGenericException getException() {
        return this.exception;
    }

    public OperonValue evaluate(OperonGenericException e) throws OperonGenericException {
        log.debug("ExceptionHandler :: evaluate()");
        Node errHandlerExpr = this.getExceptionHandlerExpr();
        errHandlerExpr.getStatement().getOperonContext().addStackTraceElement(errHandlerExpr);
        //System.out.println("Error: " + e.getErrorJson());
        ExceptionStatement exceptionStatement = new ExceptionStatement(e.getErrorJson().getStatement().getOperonContext());
        if (this.getException() != null) {
            ErrorValue errorValue = null;
            if (this.getException() instanceof OperonGenericException && this.getException().getErrorJson() instanceof ErrorValue) {
                errorValue = (ErrorValue) this.getException().getErrorJson();
            }
            else {
                errorValue = ExceptionHandler.createErrorValue(errHandlerExpr.getStatement(), this.getException());
            }
            errHandlerExpr.getStatement().getRuntimeValues().put("$error", errorValue);
        }
        exceptionStatement.setException(e);
        OperonValue cv = e.getCurrentValue();
        if (cv == null) {
            //System.out.println(">> 3 --> CV == null !");
            cv = e.getErrorJson();
        }
        else {
            //System.out.println(">> 3 --> CV: " + cv);
        }
        exceptionStatement.setCurrentValue(cv);
        exceptionStatement.setNode(errHandlerExpr);
        exceptionStatement.setId("ExceptionStatement");
        
        //
        // NOTE: this may throw again new error:
        //
        OperonValue result = (OperonValue) exceptionStatement.evaluate();
        log.debug("ExceptionHandler :: evaluate() done");
        return result;
    }
    
    //
    // This sets also the valueBeforeError
    //
    public static ErrorValue createErrorValue(Statement stmt, OperonGenericException oge) {
        log.debug("ExceptionHandler :: createErrorValue()");
        //System.out.println("ExceptionHandler :: createErrorValue :: 0");
        ErrorValue errorValue = new ErrorValue(stmt);
        errorValue.setCode("ERROR");
        errorValue.setMessage("");
        //
        // Create the error-json object
        //
        ObjectType exceptionObj = new ObjectType(stmt);
        //System.out.println("ExceptionHandler :: createErrorValue :: 1");
        if (oge.getCurrentValue() != null) {
            //System.out.println("ExceptionHandler :: createErrorValue :: 1.1");
            PairType eo_valueBeforeError = new PairType(stmt);
            //System.out.println("ExceptionHandler :: createErrorValue :: 1.2");
            OperonValue valueBeforeError = new EmptyType(stmt);
            if (oge.getValueBeforeError() != null) {
                valueBeforeError = oge.getValueBeforeError();
            }
            eo_valueBeforeError.setPair("\"valueBeforeError\"", valueBeforeError);
            //System.out.println("ExceptionHandler :: createErrorValue :: 1.3");
            exceptionObj.safeAddPair(eo_valueBeforeError);
            //System.out.println("ExceptionHandler :: createErrorValue :: 1.4");
        }
        //System.out.println("ExceptionHandler :: createErrorValue :: 2");
        errorValue.setErrorJson(exceptionObj);
        log.debug("ExceptionHandler :: createErrorValue() done");
        return errorValue;
    }
}