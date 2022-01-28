/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core;

import java.util.List;
import java.util.Random;

import io.operon.runner.OperonContext;
import io.operon.runner.ModuleContext;
import io.operon.runner.Context;
import io.operon.runner.OperonTestsContext;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.TrueType;
import io.operon.runner.node.type.FalseType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.model.test.MockComponent;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.Main;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class Mock extends BaseArity1 implements Node, Arity1 {
    private static Logger log = LogManager.getLogger(Mock.class);

    // Not used at the moment
    private MockComponent mockComponent;
    
    public Mock(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "mock", "value");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        log.debug("EVALUATE Mock");
        //System.out.println("Mock :: evaluate");
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            OperonValue currentValueCopy = currentValue.copy();

            Node mockExpr = this.getParam1();
            OperonValue result = null;

            if (mockExpr != null) {
                mockExpr.getStatement().setCurrentValue(currentValue);
                
                result = (OperonValue) mockExpr.evaluate();
            }
            
            //System.out.println("Mock evaluated. Start returning.");
            if (result != null) {
                //System.out.println(">> Got result");
                //System.out.println("  >> " + result);
                if (this.getStatement() == null) {
                    System.out.println("WARNING statement was null in Mock");
                }
                //System.out.println(">> Set CV for statement: " + this.getStatement().getId());
                //if (this.getStatement().getPreviousStatement() != null) {
                //    this.getStatement().getPreviousStatement().setCurrentValue(result);
                //}
                //System.out.println(">> Return result");
                return result;
            }
            else {
                //System.out.println(">> Did NOT get result, returning copy of currentValue");
                //System.out.println("  >> " + result);
                if (this.getStatement() == null) {
                    System.out.println("WARNING statement was null in Mock");
                }
                if (currentValueCopy == null) {
                    System.out.println("WARNING CV was null in Mock");
                }
                return currentValueCopy;
            }
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
        }
    }

    public void setMockComponent(MockComponent mc) {
        this.mockComponent = mc;
    }
    
    public MockComponent getMockComponent() {
        return this.mockComponent;
    }

}