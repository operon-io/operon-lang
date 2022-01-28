/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectCreatePair extends BaseArity2 implements Node, Arity2 {
    
    public ObjectCreatePair(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "createPair", "key", "value");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            OperonValue p1 = (OperonValue) this.getParam1().evaluate();
            String keyStr = ((StringType) p1.evaluate()).getJavaStringValue();
            this.getStatement().setCurrentValue(currentValue);
            OperonValue jsonValue = (OperonValue) this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);

            Statement stmt = new DefaultStatement(OperonContext.emptyContext);
            ObjectType result = new ObjectType(stmt);
            //ObjectType result = new ObjectType(this.getStatement());
            List<PairType> pairs = new ArrayList<PairType>();
            PairType resultPair = new PairType(stmt);
            resultPair.setPair("\"" + keyStr + "\"", jsonValue);
            result.addPair(resultPair);
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public boolean ensuredCurrentValueSetBetweenParamEvaluations() {
        return true;
    }

}