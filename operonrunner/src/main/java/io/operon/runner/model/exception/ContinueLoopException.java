/** OPERON-LICENSE **/
package io.operon.runner.model.exception;

import io.operon.runner.node.type.EmptyType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.model.exception.OperonGenericException;

public class ContinueLoopException extends OperonGenericException {

    public ContinueLoopException() {
        super(new EmptyType(new DefaultStatement(null)));
        //System.out.println("Created Continue");
    }

    public ContinueLoopException(OperonValue value) {
        super(value);
    }

}