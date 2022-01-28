/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.path;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class PathLinkRoot extends BaseArity1 implements Node, Arity1 {
    
    public PathLinkRoot(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "linkRoot", "value");
    }

    public Path evaluate() throws OperonGenericException {
        try {
            Path path = (Path) this.getStatement().getCurrentValue();
            
            OperonValue linkRoot = (OperonValue) this.getParam1().evaluate();
            //System.out.println("linkRoot :: evaluate()");
            //System.out.println("  rootValue :: " + linkRoot);
            
            //
            // NOTE: since no copy is made of the linked value, it could be
            // modified, e.g. when doing: $val: {some large obj}; [paths...] Map => path:linkRoot($val) => path:value() => object:remove("somekey");
            // The modification affects especially Arrays, which are not deep-copied by default.
            // 
            path.setObjLink(linkRoot);

            return path;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}