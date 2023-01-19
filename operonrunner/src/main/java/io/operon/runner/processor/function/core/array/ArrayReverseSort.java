/*
 *   Copyright 2022-2023, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import io.operon.runner.processor.function.Namespaces;
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
     // no logger 
    
    public ArrayReverseSort(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "reverseSort", "comparator");
        this.setNs(Namespaces.ARRAY);
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