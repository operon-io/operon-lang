/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.MultiNode;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.function.core.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.processor.function.core.array.ArrayGet;
import io.operon.runner.model.exception.OperonGenericException;

//
// next() returns the value of the next object-key-pair or array item.
//  NOTE: returns Empty when no next is available.
//        This is better than returning Null, because
//        null might be defined in the array/object,
//        but Empty is not.
public class GenericNext extends BaseArity0 implements Node, Arity0 {
    
    public GenericNext(Statement statement) {
        super(statement);
        this.setFunctionName("next");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            MultiNode mn = new MultiNode(this.getStatement());
            PathCurrent pathCurrent = new PathCurrent(this.getStatement());
            PathNext pathNext = new PathNext(this.getStatement());
            PathValue pathValue = new PathValue(this.getStatement());
            
            //
            // Push in reverse order:
            //
            mn.addNode(pathValue);
            mn.addNode(pathNext);
            mn.addNode(pathCurrent);
            
            OperonValue result = mn.evaluate();
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

}