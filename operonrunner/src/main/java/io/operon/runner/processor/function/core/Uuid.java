/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Generate UUID
 *
 */
public class Uuid extends BaseArity0 implements Node, Arity0 {
    private static Logger log = LogManager.getLogger(Uuid.class);
    
    public Uuid(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("uuid");
    }

    public StringType evaluate() throws OperonGenericException {
        UUID uuid = UUID.randomUUID();
        StringType result = new StringType(this.getStatement());
        result.setFromJavaString(uuid.toString());
        return result;
    }

}