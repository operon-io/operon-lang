/** OPERON-LICENSE **/
package io.operon.runner;

import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 * 
 * Interface for OperonFunctions that are used in call -component.
 * 
 */
public interface OperonFunction {

    public OperonValue execute(Statement stmt, OperonValue params) throws OperonGenericException;

}