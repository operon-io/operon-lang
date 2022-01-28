/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.bool;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.Random;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class BooleanRandom extends BaseArity1 implements Node, Arity1 {
    
    public BooleanRandom(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "random", "options");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            long seed = 0;
            ObjectType options = null;
            
            if (this.getParam1() != null) {
                options = (ObjectType) this.getParam1().evaluate();
                
                if (options.hasKey("\"seed\"")) {
                    seed = (long) ((NumberType) options.getByKey("seed").evaluate()).getDoubleValue();
                }
            }
            
            Random r;
            
            if (seed != 0) {
                r = new Random(seed);
            }
            
            else {
                r = new Random();
            }
            
            boolean randomBoolean = r.nextBoolean();
            
            if (randomBoolean == true) {
                return new TrueType(this.getStatement());
            }
            else {
                return new FalseType(this.getStatement());
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "boolean:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}