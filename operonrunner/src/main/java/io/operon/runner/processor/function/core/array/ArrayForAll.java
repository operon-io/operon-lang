/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import io.operon.runner.model.exception.OperonGenericException;

// 
// Tests predicate-expression (given as functionRef) for each value in an array
// 
public class ArrayForAll extends BaseArity1 implements Node, Arity1, SupportsAttributes {
    private static Logger log = LogManager.getLogger(ArrayForAll.class);
    
    public ArrayForAll(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "forAll", "test");
    }

    public OperonValue evaluate() throws OperonGenericException {
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ArrayType arrayToTest = (ArrayType) currentValue.evaluate();
        if (arrayToTest.getValues().size() == 0) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), "Empty array is not supported.");
        }
        
        Path currentPath = this.getStatement().getCurrentPath();
        OperonValue objLink = currentPath.getObjLink();
        if (objLink == null) {
            objLink = arrayToTest;
        }
        
        Path resetPath = currentPath.copy();
        resetPath.setObjLink(objLink);
        
        try {
            for (int i = 0; i < arrayToTest.getValues().size(); i ++) {
                log.debug("loop, i == " + i);
                //
                // Set new PosPathPart:
                //
                Path currentPathCopy = currentPath.copy();
                PathPart pp = new PosPathPart(i + 1);
                currentPathCopy.getPathParts().add(pp);
                currentPathCopy.setObjLink(objLink);
                this.getStatement().setCurrentPath(currentPathCopy);
                
                OperonValue valueToTest = ArrayGet.baseGet(this.getStatement(), arrayToTest, i + 1);
                
                Node paramNode = this.getParam1();
                paramNode.getStatement().setCurrentValue(valueToTest);
                
                Node forallFunctionRefNode = paramNode.evaluate();
                
                OperonValue testValueResult = null;
                
                if (forallFunctionRefNode instanceof FunctionRef) {
                    FunctionRef testFnRef = (FunctionRef) forallFunctionRefNode;
                    
                    testFnRef.getParams().clear();
                    testFnRef.getParams().add(valueToTest);
                    testFnRef.setCurrentValueForFunction(arrayToTest); // ops. took out currentValue
                    testValueResult = (OperonValue) testFnRef.invoke();
                }
                
                else if (forallFunctionRefNode instanceof LambdaFunctionRef) {
                    LambdaFunctionRef forallFnRef = (LambdaFunctionRef) forallFunctionRefNode;
                    forallFnRef.getParams().clear();
                    // NOTE: we cannot guarantee the order of keys that Map.keySet() returns,
                    //       therefore we must assume that the keys are named in certain manner.
                    forallFnRef.getParams().put("$a", valueToTest);
                    forallFnRef.setCurrentValueForFunction(arrayToTest);
                    testValueResult = (OperonValue) forallFnRef.invoke();
                }
                
                else {
                    testValueResult = (OperonValue) forallFunctionRefNode;
                }
                
                if (testValueResult instanceof FalseType) {
                    FalseType resultFalse = new FalseType(this.getStatement());
                    this.getStatement().setCurrentPath(resetPath);
                    return resultFalse;
                }
            }
            
            this.getStatement().setCurrentPath(resetPath);
            // True forall
            TrueType resultTrue = new TrueType(this.getStatement());
            return resultTrue;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
        }
    }

}