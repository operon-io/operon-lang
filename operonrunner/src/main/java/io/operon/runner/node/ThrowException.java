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

import io.operon.runner.statement.Statement;
import io.operon.runner.ExceptionHandler;
import io.operon.runner.node.Node;
import io.operon.runner.node.ValueRef;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

//
// Throws the Java Exception with given JSON-value
//   Select Error ("oops")
// 
public class ThrowException extends AbstractNode implements Node {
     // no logger 
    
    private Node exceptionValue;
    
    public ThrowException(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        //:OFF:log.debug("ENTER ThrowException.evaluate(). Stmt: " + this.getStatement().getId());
        OperonValue currentValue = this.getStatement().getCurrentValue();
        //System.out.println("ThrowException :: evaluate() :: CV :: " + this.getStatement().getCurrentValue());
        
        // E.g. ' Throw {"message": @} ', which must be evaluated
        OperonValue evaluatedExceptionOperonValue = (OperonValue) this.getExceptionValue().evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        OperonGenericException oge = new OperonGenericException(evaluatedExceptionOperonValue);
        
        //System.out.println("ThrowException :: evaluate() :: CV after eval :: " + this.getStatement().getCurrentValue());
        //System.out.println("ThrowException :: evaluate() :: errorJson :: " + evaluatedExceptionOperonValue);
        
        if (oge.getValueBeforeError() == null) {
            //System.out.println("ThrowException :: setValueBeforeError :: " + this.getStatement().getCurrentValue());
            oge.setValueBeforeError(this.getStatement().getCurrentValue());
        }

        oge.setCurrentValue(evaluatedExceptionOperonValue);
        oge.setErrorJson(evaluatedExceptionOperonValue);
        ErrorValue errorValue = ExceptionHandler.createErrorValue(this.getStatement(), oge);
        errorValue.setErrorJson(evaluatedExceptionOperonValue);
        oge.setErrorValue(errorValue);
        
        this.getStatement().getOperonContext().setErrorValue(errorValue);
        this.getStatement().getOperonContext().setException(oge);
        
        //:OFF:log.debug("ThrowException :: throw OperonGenericException");
        throw oge;
    }
    
    public void setExceptionValue(Node ev) {
        this.exceptionValue = ev;
    }

    public Node getExceptionValue() {
        return this.exceptionValue;
    }

    public String toString() {
        return "Throw";
    }
}