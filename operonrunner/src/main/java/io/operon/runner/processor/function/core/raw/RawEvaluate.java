/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.raw;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.OperonRunner;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// This allows to dynamically write Operon-expr and evaluate it.
//
public class RawEvaluate extends BaseArity1 implements Node, Arity1 {
    
    public RawEvaluate(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "evaluate", "expr");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            if (this.getParam1() == null) {
                OperonValue currentValue = this.getStatement().getCurrentValue();
                RawValue exprBv = (RawValue) currentValue.evaluate();
                String expr = new String(exprBv.getBytes());
                OperonValue result = RawEvaluate.evaluate(this.getStatement(), expr, null);
                return result;
            }
            
            else {
                //System.out.println("RawEvaluate :: evaluate param, CV = " + this.getStatement().getCurrentValue());
                OperonValue currentValueCopy = this.getStatement().getCurrentValue();
                RawValue exprBv = (RawValue) this.getParam1().evaluate();
                this.getStatement().setCurrentValue(currentValueCopy); // set back the cv after param1 evaluation (which changed the cv)
                
                String expr = new String(exprBv.getBytes());
                OperonValue result = RawEvaluate.evaluate(this.getStatement(), expr, currentValueCopy);
                return result;
            }
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw:" + this.getFunctionName(), e.getMessage());
        }
    }

    public static OperonValue evaluate(Statement stmt, String expr, OperonValue currentValueForExpr) throws OperonGenericException, Exception {
        Node node = OperonRunner.compileExpr(stmt, expr);
        //System.out.println("RawEvaluate :: evaluate :: " + node.getStatement().getRuntimeValues());
        //System.out.println("RawEvaluate :: evaluate CV for expression :: " + currentValueForExpr);
        //System.out.println("RawEvaluate :: evaluate :: node previousStatement: " + node.getStatement());
        if (currentValueForExpr != null) {
            node.getStatement().setCurrentValue(currentValueForExpr);
        }
        OperonValue result = node.evaluate();
        //System.out.println("RawEvaluate :: evaluate :: result statement: " + result.getStatement());
        //System.out.println("RawEvaluate :: evaluate :: result previousStatement: " + result.getStatement().getPreviousStatement().getLetStatements());
        return result;
    }

}