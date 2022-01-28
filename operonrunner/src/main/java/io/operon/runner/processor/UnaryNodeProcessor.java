/** OPERON-LICENSE **/
package io.operon.runner.processor;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.model.exception.OperonGenericException;

/**
 * 
 * Processor interface for unary nodes
 * 
 */
public interface UnaryNodeProcessor {

    public OperonValue process(Statement statement, Node node) throws OperonGenericException;

}