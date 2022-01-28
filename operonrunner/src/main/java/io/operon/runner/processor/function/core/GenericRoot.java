/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class GenericRoot extends BaseArity0 implements Node, Arity0 {
    
    public GenericRoot(Statement statement) {
        super(statement);
        this.setFunctionName("root");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            Path path = this.getStatement().getCurrentPath();
            //System.out.println("path=" + path);
            OperonValue result = path.getObjLink();
            
            // If no objLink, default to currentValue:
            if (result == null) {
                //System.out.println("No objLink");
                result = this.getStatement().getCurrentValue();
            }

            //Do not set new path!
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

}