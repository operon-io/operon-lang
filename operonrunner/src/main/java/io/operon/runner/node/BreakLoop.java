/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.operon.runner.statement.Statement;
import io.operon.runner.model.exception.BreakLoopException;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class BreakLoop extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(BreakLoop.class);

    public BreakLoop(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER BreakLoop.evaluate()");
        throw new BreakLoopException();
    }

}
