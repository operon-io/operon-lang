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
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.processor.function.Namespaces;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DateNow extends BaseArity0 implements Node, Arity0 {
    
    private static TimeZone timeZone = null;
    private static Calendar cal = null;
    
    public DateNow(Statement statement) {
        super(statement);
        this.setFunctionName("now");
        this.setNs(Namespaces.DATE);
    }

    public ObjectType evaluate() throws OperonGenericException {        
        try {
            // TODO: move this to constructor?
            
            if (DateNow.timeZone == null) {
                //
                // NOTE: this might not always be OperonContext, it can also be
                //       OperonTestsContext, therefore Context is required.
                //
                Context rootContext = BaseContext.getRootContextByStatement(this.getStatement());
                
                // 
                // Resolve timezone, if such was set when Operon was started (with --timezone / -tz option)
                // 
                if (rootContext.getConfigs() != null) {
                    OperonConfigs configs = rootContext.getConfigs();
                    if (configs.getTimezone() != null) {
                        TimeZone tz = TimeZone.getTimeZone(configs.getTimezone());
                        DateNow.timeZone = tz;
                    }
                }
            }
            
            if (DateNow.cal == null) {
                DateNow.cal = DateNow.getCalendar(DateNow.timeZone, 0);
            }
            DateNow.cal.setTime(new Date());
            ObjectType result = DateNow.getDateNowObjectType(this.getStatement(), DateNow.cal);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "date:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static Calendar getCalendar(TimeZone timeZone, long millis) {
        Calendar c = null;
        
        if (timeZone != null) {
            c = Calendar.getInstance(timeZone);
        }
        else {
            c = Calendar.getInstance(); // use system's default timeZone
        }
        if (millis != 0) {
            c.setTimeInMillis(millis);
        }
        return c;
    }

    public static NumberType getDateNowNumberType(Statement stmt, Calendar c) {
        NumberType result = NumberType.create(stmt, Double.valueOf(c.getTime().getTime()), (byte) 0);
        return result;
    }

    //
    // Example result: {"Year": 2021, "Month": 8, "Day": 13, "Hour": 10, "Minute": 8, "Second": 0, "Millisecond": 1, "TimeZone": {}}
    //
    public static ObjectType getDateNowObjectType(Statement stmt, Calendar c) {
        ObjectType result = new ObjectType(stmt);

        TimeZone timeZone = c.getTimeZone();
        
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH) + 1;
        int dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
        int hours = c.get(Calendar.HOUR_OF_DAY);
        int minutes = c.get(Calendar.MINUTE);
        int seconds = c.get(Calendar.SECOND);
        int millis = c.get(Calendar.MILLISECOND);

        NumberType y = new NumberType(stmt);
        NumberType m = new NumberType(stmt);
        NumberType d = new NumberType(stmt);
        NumberType h = new NumberType(stmt);
        NumberType min = new NumberType(stmt);
        NumberType s = new NumberType(stmt);
        NumberType ms = new NumberType(stmt);

        y.setDoubleValue((double) year);
        y.setPrecision((byte) 0);
        m.setDoubleValue((double) month);
        m.setPrecision((byte) 0);
        d.setDoubleValue((double) dayOfMonth);
        d.setPrecision((byte) 0);
        h.setDoubleValue((double) hours);
        h.setPrecision((byte) 0);
        min.setDoubleValue((double) minutes);
        min.setPrecision((byte) 0);
        s.setDoubleValue((double) seconds);
        s.setPrecision((byte) 0);
        ms.setDoubleValue((double) millis);
        ms.setPrecision((byte) 0);

        PairType yP = new PairType(stmt);
        PairType mP = new PairType(stmt);
        PairType dP = new PairType(stmt);
        PairType hP = new PairType(stmt);
        PairType minP = new PairType(stmt);
        PairType sP = new PairType(stmt);
        PairType msP = new PairType(stmt);
        
        ObjectType timeZoneObj = new ObjectType(stmt);
        PairType tzP = new PairType(stmt);
        PairType tzIdP = new PairType(stmt);
        StringType tzIdString = new StringType(stmt);
        tzIdString.setFromJavaString(timeZone.getID());
        tzIdP.setPair("\"id\"", tzIdString);
        timeZoneObj.safeAddPair(tzIdP);

        yP.setPair("\"Year\"", y);
        mP.setPair("\"Month\"", m);
        dP.setPair("\"Day\"", d);
        hP.setPair("\"Hour\"", h);
        minP.setPair("\"Minute\"", min);
        sP.setPair("\"Second\"", s);
        msP.setPair("\"Millisecond\"", ms);
        tzP.setPair("\"TimeZone\"", timeZoneObj);

        result.safeAddPair(yP);
        result.safeAddPair(mP);
        result.safeAddPair(dP);
        result.safeAddPair(hP);
        result.safeAddPair(minP);
        result.safeAddPair(sP);
        result.safeAddPair(msP);
        result.safeAddPair(tzP);
        
        return result;
    }
    
    public static Date getDateFromDateObj(ObjectType dateObj) throws OperonGenericException {
        Integer year = null;
        Integer month = null;
        Integer dayOfMonth = null;
        Integer hours = null;
        Integer minutes = null;
        Integer seconds = null;
        Integer millis = null;
        String timezone = null;
        
        for (PairType pair : dateObj.getPairs()) {
            OperonValue pairValue = (OperonValue) pair.getValue().evaluate();
            String key = pair.getKey();
            switch (key) {
                case "\"Year\"":
                    year = (int) ((NumberType) pairValue.getValue()).getDoubleValue();
                    break;
                case "\"Month\"":
                    month = (int) ((NumberType) pairValue.getValue()).getDoubleValue();
                    break;
                case "\"Day\"":
                    dayOfMonth = (int) ((NumberType) pairValue.getValue()).getDoubleValue();
                    break;
                case "\"Hour\"":
                    hours = (int) ((NumberType) pairValue.getValue()).getDoubleValue();
                    break;
                case "\"Minute\"":
                    minutes = (int) ((NumberType) pairValue.getValue()).getDoubleValue();
                    break;
                case "\"Second\"":
                    seconds = (int) ((NumberType) pairValue.getValue()).getDoubleValue();
                    break;
                case "\"Millisecond\"":
                    millis = (int) ((NumberType) pairValue.getValue()).getDoubleValue();
                    break;
                case "\"TimeZone\"":
                    ObjectType tz = (ObjectType) pairValue;
                    for (PairType p : tz.getPairs()) {
                        String tzKey = p.getKey();
                        switch (tzKey) {
                            case "\"Id\"":
                                timezone = ((StringType) p.getValue().evaluate()).getJavaStringValue();
                                break;
                            default:
                                break;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        TimeZone tz = null;
        if (timezone != null) {
            tz = TimeZone.getTimeZone(timezone);
        }
        Calendar c = null;
        if (tz != null) {
            c = Calendar.getInstance(tz);
        }
        else {
            c = Calendar.getInstance();
        }
        if (year != null) {
            c.set(Calendar.YEAR, year);
        }
        if (month != null) {
            c.set(Calendar.MONTH, month - 1);
        }
        else {
            c.set(Calendar.MONTH, 0);
        }
        if (dayOfMonth != null) {
            c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        }
        else {
            c.set(Calendar.DAY_OF_MONTH, 1);
        }
        if (hours != null) {
            c.set(Calendar.HOUR_OF_DAY, hours);
        }
        else {
            c.set(Calendar.HOUR_OF_DAY, 0);
        }
        if (minutes != null) {
            c.set(Calendar.MINUTE, minutes);
        }
        else {
            c.set(Calendar.MINUTE, 0);
        }
        if (seconds != null) {
            c.set(Calendar.SECOND, seconds);
        }
        else {
            c.set(Calendar.SECOND, 0);
        }
        if (millis != null) {
            c.set(Calendar.MILLISECOND, millis);
        }
        else {
            c.set(Calendar.MILLISECOND, 0);
        }
        
        Date result = c.getTime();
        return result; 
    }
}