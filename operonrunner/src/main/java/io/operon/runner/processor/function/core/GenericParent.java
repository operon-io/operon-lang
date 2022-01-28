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
import io.operon.runner.processor.function.core.path.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class GenericParent extends BaseArity0 implements Node, Arity0 {
    
    public GenericParent(Statement statement) {
        super(statement);
        this.setFunctionName("parent");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            Path currentPath = this.getStatement().getCurrentPath();
            //System.out.println("Current path :: " + currentPath);
            OperonValue value = currentPath.getObjLink();
            //System.out.println("ObjLink :: " + value);
            Path parentPath = PathParentPath.getParentPath(currentPath);
            if (value == null) {
                return new NullType(this.getStatement());
            }
            parentPath.setObjLink(value);
            //System.out.println("Parent path :: " + parentPath);
            //this.getStatement().setCurrentPath(parentPath); --> Do not set this!
            OperonValue result = PathValue.get(value, parentPath);
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

}