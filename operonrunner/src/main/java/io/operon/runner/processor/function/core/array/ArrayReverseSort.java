/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.array;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.SupportsAttributes;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// 
// Does not support attributes!
// 
public class ArrayReverseSort extends BaseArity1 implements Node, Arity1 /*, SupportsAttributes*/ {
    private static Logger log = LogManager.getLogger(ArrayReverseSort.class);
    
    public ArrayReverseSort(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "reverseSort", "comparator");
    }

    @SuppressWarnings("unchecked")
    public ArrayType evaluate() throws OperonGenericException {
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ArrayType arrayToSort = (ArrayType) currentValue.evaluate();
        if (arrayToSort.getValues().size() == 0) {
            return arrayToSort;
        }
        
        List<Node> resultList = new ArrayList<Node>();
        
        try {
            // comparator was not given:
            if (this.getParam1() == null) {
                OperonValue ev1 = (OperonValue) arrayToSort.getValues().get(0).evaluate();
                if (ev1 instanceof NumberType) {
                    List<NumberType> numbers = new ArrayList<NumberType>();
                    for (Node n : arrayToSort.getValues()) {
                        numbers.add( (NumberType) n.evaluate() );
                    }
                    Collections.sort(numbers, Collections.reverseOrder());
                    for (NumberType num : numbers) {
                        resultList.add(num);
                    }
                }
                else if (ev1 instanceof StringType) {
                    List<StringType> strings = new ArrayList<StringType>();
                    for (Node n : arrayToSort.getValues()) {
                        strings.add( (StringType) n.evaluate() );
                    }
                    Collections.sort(strings, Collections.reverseOrder());
                    for (StringType string : strings) {
                        resultList.add(string);
                    }
                }
                arrayToSort.setValues(resultList);
            }
            // comparator was given. Evaluate it.
            else {
                ArrayType.ArrayComparator ac = new ArrayType.ArrayComparator();
                ac.setCompareExpr(this.getParam1());
                Collections.sort(arrayToSort.getValues(), ac);
            }
            return arrayToSort;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "array:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}