/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FunctionRefArgumentPlaceholder extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FunctionRefArgumentPlaceholder.class);
    
    public FunctionRefArgumentPlaceholder(Statement stmnt) {
        super(stmnt);
    }

    @Override
    public String toString() {
        return "FunctionRefArgumentPlaceholder";
    }
}