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
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Date;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.BaseContext;
import io.operon.runner.Context;
import io.operon.runner.OperonContext;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.core.date.DateNow;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DateLastDayOfMonth extends BaseArity1 implements Node, Arity1 {
    
    private static TimeZone timeZone = null;
    private static Calendar cal = null;

    public DateLastDayOfMonth(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "lastDayOfMonth", "date");
        this.setNs(Namespaces.DATE);
    }

    public NumberType evaluate() throws OperonGenericException {        
        try {
            if (this.getParam1() == null) {
                Date inputDate = DateNow.getDateFromDateObj((ObjectType) this.getStatement().getCurrentValue());
                int lastDay = lastDayOfMonth(inputDate);
                NumberType result = NumberType.create(this.getStatement(), (double) lastDay, (byte) 0);
                return result;
            }
            else {
                ObjectType dateObj = (ObjectType) this.getParam1().evaluate();
                Date inputDate = DateNow.getDateFromDateObj(dateObj);
                int lastDay = lastDayOfMonth(inputDate);
                NumberType result = NumberType.create(this.getStatement(), (double) lastDay, (byte) 0);
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static int lastDayOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        return lastDay;
    }

}