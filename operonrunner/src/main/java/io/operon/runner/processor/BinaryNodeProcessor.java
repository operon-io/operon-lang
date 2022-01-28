/** OPERON-LICENSE **/
package io.operon.runner.processor;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.model.exception.OperonGenericException;

/**
 * 
 * Processor interface for binary nodes
 * 
 */
public interface BinaryNodeProcessor {

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException;

}