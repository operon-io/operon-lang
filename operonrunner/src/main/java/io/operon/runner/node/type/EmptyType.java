/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class EmptyType extends OperonValue implements Node, AtomicOperonValue {
    private static Logger log = LogManager.getLogger(EmptyType.class);
    
    public EmptyType(Statement stmnt) {
        super(stmnt);
    }

    public EmptyType evaluate() throws OperonGenericException {
        log.debug("EmptyType :: Evaluate");
        this.setUnboxed(true);
        return this;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "";
    }

}