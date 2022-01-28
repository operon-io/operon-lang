/*
 *   Copyright 2022, operon.io
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
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.StringUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DateFromString extends BaseArity1 implements Node, Arity1 {
    
    private ObjectType date = null;
    
    public DateFromString(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "fromString", "pattern");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        StringType currentDateString = (StringType) currentValue.evaluate();
        
        Node datePatternNode = this.getParam1().evaluate();
        String datePattern = ((StringType) datePatternNode).getJavaStringValue();
        
        ObjectType result = DateFromString.createDateFromString(this.getStatement(), currentDateString.getJavaStringValue(), datePattern);
        return result;
    }

    public static ObjectType createDateFromString(Statement stmt, String dateStr, String datePattern) throws OperonGenericException {
        try {
            ObjectType result = new ObjectType(stmt);
            
            Date date = new SimpleDateFormat(datePattern).parse(dateStr);
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(date.getTime());
            
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int hour = cal.get(Calendar.HOUR);
            int minute = cal.get(Calendar.MINUTE);
            int second = cal.get(Calendar.SECOND);
            int millisecond = cal.get(Calendar.MILLISECOND);
    
            PairType yearPair = new PairType(stmt);
            NumberType yearNum = new NumberType(stmt);
            yearNum.setDoubleValue((double) year);
            yearPair.setPair("\"Year\"", yearNum);
            result.addPair(yearPair);
    
            PairType monthPair = new PairType(stmt);
            NumberType monthNum = new NumberType(stmt);
            monthNum.setDoubleValue((double) month);
            monthPair.setPair("\"Month\"", monthNum);
            result.addPair(monthPair);
    
            PairType dayPair = new PairType(stmt);
            NumberType dayNum = new NumberType(stmt);
            dayNum.setDoubleValue((double) day);
            dayPair.setPair("\"Day\"", dayNum);
            result.addPair(dayPair);
    
            PairType hourPair = new PairType(stmt);
            NumberType hourNum = new NumberType(stmt);
            hourNum.setDoubleValue((double) hour);
            hourPair.setPair("\"Hour\"", hourNum);
            result.addPair(hourPair);
            
            PairType minutePair = new PairType(stmt);
            NumberType minuteNum = new NumberType(stmt);
            minuteNum.setDoubleValue((double) minute);
            minutePair.setPair("\"Minute\"", minuteNum);
            result.addPair(minutePair);
            
            PairType secondPair = new PairType(stmt);
            NumberType secondNum = new NumberType(stmt);
            secondNum.setDoubleValue((double) second);
            secondPair.setPair("\"Second\"", secondNum);
            result.addPair(secondPair);
            
            PairType millisecondPair = new PairType(stmt);
            NumberType millisecondNum = new NumberType(stmt);
            millisecondNum.setDoubleValue((double) millisecond);
            millisecondPair.setPair("\"Millisecond\"", millisecondNum);
            result.addPair(millisecondPair);
    
            return result;
        } catch (Exception e) {
            // this throws, so return is never reached.
            ErrorUtil.createErrorValueAndThrow(stmt, "FUNCTION", "date:fromString", e.getMessage());
            return null;
        }
    }

}