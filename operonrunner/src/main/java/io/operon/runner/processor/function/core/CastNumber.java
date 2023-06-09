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

package io.operon.runner.processor.function.core;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * Cast String to Number or set precision for number
 *
 */
public class CastNumber extends BaseArity1 implements Node, Arity1 {

    public CastNumber(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "number", "options");
        this.setNs(Namespaces.CORE);
    }

    public NumberType evaluate() throws OperonGenericException {        
        OperonValue currentValue = (OperonValue) this.getStatement().getCurrentValue();
        currentValue = (OperonValue) currentValue.evaluate();
        
        if (currentValue instanceof StringType) {
            try {
                NumberType result = new NumberType(this.getStatement());
                String stringValue = ((StringType) currentValue).getJavaStringValue();
                byte precision = -1;
                
                if (this.getParam1() != null) {
                    OperonValue options = this.getParam1().evaluate();
                    if (options instanceof NumberType) {
                        precision = (byte) ((NumberType) this.getParam1().evaluate()).getDoubleValue();
                    }
                    else {
                        if (((ObjectType) options).hasKey("\"precision\"")) {
                            NumberType precisionNum = (NumberType) ((ObjectType) options).getByKey("precision").evaluate();
                            precision = (byte) precisionNum.getDoubleValue();
                        }
                        if (((ObjectType) options).hasKey("\"decimalSeparator\"")) {
                            StringType separatorStr = (StringType) ((ObjectType) options).getByKey("decimalSeparator").evaluate();
                            stringValue = stringValue.replaceAll(separatorStr.getJavaStringValue(), ".");
                        }
                        // groupSeparator:
                        //   E.g. 1,000,000.00 -> "," or 1 000 000,00 -> " "
                        //
                        if (((ObjectType) options).hasKey("\"groupSeparator\"")) {
                            StringType separatorStr = (StringType) ((ObjectType) options).getByKey("groupSeparator").evaluate();
                            stringValue = stringValue.replaceAll(separatorStr.getJavaStringValue(), "");
                        }
                    }
                }
                else {
                    precision = NumberType.getPrecisionFromStr(stringValue);
                }
                result.setDoubleValue(Double.parseDouble(stringValue));
                result.setPrecision( precision );
                return result;
            } catch(Exception e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), e.getMessage());
                return null;
            }
        }
        
        else if (currentValue instanceof NumberType) {
            if (this.getParam1() != null) {
                OperonValue options = this.getParam1().evaluate();
                if (options instanceof NumberType) {
                    byte precision = (byte) ((NumberType) this.getParam1().evaluate()).getDoubleValue();
                    ((NumberType) currentValue).setPrecision(precision);
                }
                else {
                    if (((ObjectType) options).hasKey("\"precision\"")) {
                        NumberType precisionNum = (NumberType) ((ObjectType) options).getByKey("precision").evaluate();
                        byte precision = (byte) precisionNum.getDoubleValue();
                        ((NumberType) currentValue).setPrecision(precision);
                    }
                    // Separator or trim are not applicable here!
                }
            }
            return (NumberType) currentValue;
        }
        
        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "core:" + this.getFunctionName(), "Could not cast value to number.");
        return null;
    }

}