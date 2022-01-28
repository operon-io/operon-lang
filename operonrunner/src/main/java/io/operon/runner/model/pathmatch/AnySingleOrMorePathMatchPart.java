/** OPERON-LICENSE **/
package io.operon.runner.model.pathmatch;
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 

// '?+' matches both object or array, which may be followed by any number of object or array
//
// Example: ~= ?+.foo
//
public class AnySingleOrMorePathMatchPart implements PathMatchPart { 

    public AnySingleOrMorePathMatchPart() {}
    
    @Override
    public String toString() {
        return "?+";
    }

}