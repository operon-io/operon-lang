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
import java.util.concurrent.TimeUnit;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.BaseContext;
import io.operon.runner.Context;
import io.operon.runner.OperonContext;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.processor.function.core.date.DateNow;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DateDaysBetween extends BaseArity2 implements Node, Arity2 {
    
    private static TimeZone timeZone = null;
    private static Calendar cal = null;

    public DateDaysBetween(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "daysBetween", "date1", "date2");
        this.setNs(Namespaces.DATE);
    }

    public NumberType evaluate() throws OperonGenericException {        
        try {
            ObjectType dateObj1 = (ObjectType) this.getParam1().evaluate();
            Date inputDate1 = DateNow.getDateFromDateObj(dateObj1);
            //System.out.println("date1 :: " + inputDate1);
            
            ObjectType dateObj2 = (ObjectType) this.getParam2().evaluate();
            Date inputDate2 = DateNow.getDateFromDateObj(dateObj2);
            //System.out.println("date2 :: " + inputDate2);
            
            long daysBetweenCount = daysBetweenCount(inputDate1, inputDate2);
            
            NumberType result = NumberType.create(this.getStatement(), (double) daysBetweenCount, (byte) 0);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static long daysBetweenCount(Date startDate, Date endDate) {
        long diffInMillies = endDate.getTime() - startDate.getTime();
        //System.out.println("Diff in millies :: " + diffInMillies);
        return TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

}