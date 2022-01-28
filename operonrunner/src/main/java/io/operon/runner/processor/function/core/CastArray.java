/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.core.path.PathParts;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

/**
 *
 * Cast value to Array
 *
 */
public class CastArray extends BaseArity0 implements Node, Arity0 {
    private static Logger log = LogManager.getLogger(CastArray.class);
    
    public CastArray(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("array");
    }

    public ArrayType evaluate() throws OperonGenericException {
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        ArrayType result = new ArrayType(this.getStatement());

        if (currentValue instanceof ObjectType) {
            ObjectType obj = (ObjectType) currentValue;
            for (PairType pair : obj.getPairs()) {
                ObjectType objWithSinglePair = new ObjectType(this.getStatement());
                objWithSinglePair.addPair(pair);
                result.getValues().add(objWithSinglePair);
            }
        }
        
        else if (currentValue instanceof Path) {
            result = PathParts.getPathParts((Path) currentValue);
        }

        else if (currentValue instanceof ArrayType) {
            result = (ArrayType) currentValue;
        }

        else {
            result.getValues().add(currentValue);
        }
        
        return result;
    }

}