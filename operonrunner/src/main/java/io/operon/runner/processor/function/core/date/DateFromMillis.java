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
import java.util.TimeZone;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.BaseContext;
import io.operon.runner.Context;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.StringUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DateFromMillis extends BaseArity0 implements Node, Arity0 {
    
    private ObjectType date = null;
    private static TimeZone timeZone = null;
    
    public DateFromMillis(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("fromMillis");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        
        NumberType currentDateTime = null;
        try {
            currentDateTime = (NumberType) currentValue.evaluate();
        } catch (ClassCastException cce) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "CAST", "Number", "Could not cast to Number, function date:fromMillis. Line #" + this.getSourceCodeLineNumber());
        }
        long millis = (long) currentDateTime.getDoubleValue();
        
        if (DateFromMillis.timeZone == null) {
            Context rootContext = BaseContext.getRootContextByStatement(this.getStatement());
            
            // 
            // Resolve timezone, if such was set when Operon was started (with --timezone / -tz option)
            // 
            if (rootContext.getConfigs() != null) {
                OperonConfigs configs = rootContext.getConfigs();
                if (configs.getTimezone() != null) {
                    TimeZone tz = TimeZone.getTimeZone(configs.getTimezone());
                    DateFromMillis.timeZone = tz;
                }
            }
        }

        Calendar c = DateNow.getCalendar(DateFromMillis.timeZone, millis);
        ObjectType result = DateNow.getDateNowObjectType(this.getStatement(), c);
        return result;
    }

}