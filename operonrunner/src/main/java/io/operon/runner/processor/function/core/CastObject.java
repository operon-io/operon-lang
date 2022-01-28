/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.core.array.ArrayGroupBy;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Cast Array to Object
 *
 */
public class CastObject extends BaseArity1 implements Node, Arity1 {
    private static Logger log = LogManager.getLogger(CastObject.class);
    
    public CastObject(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "object", "groupBy"); // TODO: expr | func (xor-param name)?
    }

    public ObjectType evaluate() throws OperonGenericException {
        List<Node> params = new ArrayList<Node>();
        params.add(this.getParam1());
        ArrayGroupBy agb = new ArrayGroupBy(this.getStatement(), params);
        ObjectType result = (ObjectType) agb.evaluate();
        return result;
    }

}