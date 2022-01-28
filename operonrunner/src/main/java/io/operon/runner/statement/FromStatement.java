/** OPERON-LICENSE **/
package io.operon.runner.statement;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.model.InputSource;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.OperonValueConstraint;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FromStatement extends BaseStatement implements Statement {
    private static Logger log = LogManager.getLogger(FromStatement.class);
    
    private InputSource inputSource;

    public FromStatement(Context ctx) {
        super(ctx);
    }
    
    public OperonValue evaluate() throws OperonGenericException {
        //return this.getNode().evaluate();
        return null;
    }
    
    public void setInputSource(InputSource is) {
        this.inputSource = is;
    }
    
    public InputSource getInputSource() {
        return this.inputSource;
    }
}