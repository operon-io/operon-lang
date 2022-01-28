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

package io.operon.runner.processor.function.core;

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
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.StringUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class DurationToMillis extends BaseArity0 implements Node, Arity0 {
    
    private ObjectType date = null;
    
    public DurationToMillis(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("durationToMillis");
    }

    public NumberType evaluate() throws OperonGenericException {
        OperonValue currentValue = this.getStatement().getCurrentValue();
        
        currentValue = currentValue.evaluate();

        if (currentValue instanceof ObjectType) {
            ObjectType durationObj = normalize((ObjectType) currentValue);
            long millis = toMillis(durationObj);
            return NumberType.create(this.getStatement(), (double) millis, (byte) 0);
        }
        
        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "durationToMillis", "invalid input. Expected Object.");
        return null;
    }

    public static ObjectType normalize(ObjectType input) throws OperonGenericException {
        List<String> mapFromStarts = new ArrayList<String>();
        mapFromStarts.add("ms");
        mapFromStarts.add("mil");
        mapFromStarts.add("s");
        mapFromStarts.add("min");
        mapFromStarts.add("h");
        mapFromStarts.add("d");
        
        List<String> mapTo = new ArrayList<String>();
        mapTo.add("ms");
        mapTo.add("ms");
        mapTo.add("s");
        mapTo.add("min");
        mapTo.add("h");
        mapTo.add("d");
        
        input.renameKeyStartsWith(mapFromStarts, mapTo, true); // from is case insensitive
        return input;
    }

    //
    // expected shape after normalize: {ms, s, min, h, d}
    //
    public static long toMillis(ObjectType duration) throws OperonGenericException {
        double cumulativeMilliseconds = 0;
        if (duration.hasKey("\"ms\"")) {
            NumberType msNum = (NumberType) duration.getByKey("ms").evaluate();
            cumulativeMilliseconds += msNum.getDoubleValue();
        }
        if (duration.hasKey("\"s\"")) {
            NumberType sNum = (NumberType) duration.getByKey("s").evaluate();
            cumulativeMilliseconds += sNum.getDoubleValue() * 1000;
        }
        if (duration.hasKey("\"min\"")) {
            NumberType minNum = (NumberType) duration.getByKey("min").evaluate();
            cumulativeMilliseconds += minNum.getDoubleValue() * 1000 * 60;
        }
        if (duration.hasKey("\"h\"")) {
            NumberType hNum = (NumberType) duration.getByKey("h").evaluate();
            cumulativeMilliseconds += hNum.getDoubleValue() * 1000 * 60 * 60;
        }
        if (duration.hasKey("\"d\"")) {
            NumberType dNum = (NumberType) duration.getByKey("d").evaluate();
            cumulativeMilliseconds += dNum.getDoubleValue() * 1000 * 60 * 60 * 24;
        }
        return (long) cumulativeMilliseconds;
    }

}