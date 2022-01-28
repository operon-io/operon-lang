/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.core.path.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.processor.function.core.array.ArrayGet;
import io.operon.runner.model.exception.OperonGenericException;

//
// => path:current() => path:previous() => path:value()
//  NOTE: returns Empty when no previous is available.
//        This is better than returning Null, because
//        null might be defined in the array/object,
//        but Empty is not.
public class GenericPrevious extends BaseArity0 implements Node, Arity0 {
    
    public GenericPrevious(Statement statement) {
        super(statement);
        this.setFunctionName("previous");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            MultiNode mn = new MultiNode(this.getStatement());
            PathCurrent pathCurrent = new PathCurrent(this.getStatement());
            PathPrevious pathPrevious = new PathPrevious(this.getStatement());
            PathValue pathValue = new PathValue(this.getStatement());
            
            //
            // Push in reverse order:
            //
            mn.addNode(pathValue);
            mn.addNode(pathPrevious);
            mn.addNode(pathCurrent);
            
            OperonValue result = mn.evaluate();
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

}