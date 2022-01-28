/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.operon.runner.statement.Statement;
import io.operon.runner.model.exception.ContinueLoopException;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ContinueLoop extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(ContinueLoop.class);

    public ContinueLoop(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER ContinueLoop.evaluate()");
        throw new ContinueLoopException();
    }

}
