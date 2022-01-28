/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class EndValueType extends OperonValue implements Node, AtomicOperonValue {
    private static Logger log = LogManager.getLogger(EndValueType.class);
    
    public EndValueType(Statement stmnt) {
        super(stmnt);
    }

    public EndValueType evaluate() throws OperonGenericException {
        log.debug("EndValueType :: Evaluate");
        this.setUnboxed(true);
        return this;
    }

    @Override
    public String toString() {
        return "end";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "end";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "end";
    }

}