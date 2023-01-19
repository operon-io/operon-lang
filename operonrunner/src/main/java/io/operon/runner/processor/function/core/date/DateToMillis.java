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

package io.operon.runner.processor.function.core.date;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.StringUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DateToMillis extends BaseArity0 implements Node, Arity0 {
    
    public DateToMillis(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("toMillis");
    }

    public NumberType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType dateObj = (ObjectType) currentValue.evaluate();
            NumberType result = dateObjToMillis(dateObj);
            return result;
        } catch (ClassCastException cce) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_INPUT", "date:" + this.getFunctionName(), cce.getMessage() + ". Line #" + this.getSourceCodeLineNumber());
            return null;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static NumberType dateObjToMillis(ObjectType dateObj) throws OperonGenericException {
        NumberType result = new NumberType(dateObj.getStatement());
        Date d = DateNow.getDateFromDateObj(dateObj);
        result.setDoubleValue((double) d.getTime());
        result.setPrecision((byte) 0);
        return result;
    }
}