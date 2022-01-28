/** OPERON-LICENSE **/
package io.operon.runner.model.path;
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
// marker interface
public interface PathPart { 
    public PathPart copy();
    public boolean equals(Object obj);
    public String toString();
}