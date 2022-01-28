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

package io.operon.runner.node.type;

import java.text.NumberFormat;
import java.text.ParsePosition;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

public class StringType extends OperonValue implements Node, AtomicOperonValue, Comparable {

    private String value;

    public StringType(Statement stmnt) {
        super(stmnt);
    }

    public StringType evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    //
    // This assumes that the value has already been prepared
    // as valid JSON-string
    //
    public void setValue(String value) {
        this.value = value;
    }
    
    //
    // Prepare string by adding double-quotes, so it becomes a valid JSON-string.
    //
    // NOTE: this does NOT sanitize/escape the String (e.g. remove double-quotes, escape new-lines, etc.)
    //       but assumes that this is done by the caller. The function raw:rawToStringType has static
    //       function for all escapes:
    //
    //       public static String sanitizeForStringType(String strValue)
    //
    public void setFromJavaString(String value) {
        this.value = "\"" + value + "\"";
    }

    public static StringType create(Statement stmt, String value) {
        StringType result = new StringType(stmt);
        result.setValue("\"" + value + "\"");
        return result;
    }

    public OperonValue getValue() {
        return this;
    }

    //
    // Returns with double-quotes
    //
    public String getStringValue() {
        return this.value;
    }

    //
    // Double-quotes are removed
    //
    public String getJavaStringValue() {
        return this.value.substring(1, this.value.length() - 1);
    }

    public int compareTo(Object val) {
        if (val == null) {
            throw new NullPointerException();
        }
        if (val instanceof StringType == false) {
            throw new ClassCastException();
        }
        return this.getJavaStringValue().compareTo( ((StringType) val).getJavaStringValue() );
    }

    public static boolean isNumeric(String str) {
        ParsePosition pos = new ParsePosition(0);
        NumberFormat.getInstance().parse(str, pos);
        return str.length() == pos.getIndex();
    }

    @Override
    public String toString() {
        return this.getStringValue();
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return this.getStringValue();
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        String javaString = this.getJavaStringValue();
        if (javaString.startsWith("{")) {
            return this.getStringValue(); // double-quoted
        }
        else if (javaString.startsWith("[")) {
            return this.getStringValue(); // double-quoted
        }
        else if (StringType.isNumeric(javaString)) {
            return this.getStringValue(); // double-quoted
        }
        else {
            return javaString; // remove double-quotes
        }
    }

}
