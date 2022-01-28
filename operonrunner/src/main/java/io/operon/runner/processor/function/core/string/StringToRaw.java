/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.string;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class StringToRaw extends BaseArity0 implements Node, Arity0 {
    
    public StringToRaw(Statement statement) {
        super(statement);
        this.setFunctionName("toRaw");
    }

    public RawValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StringType str = (StringType) currentValue.evaluate();
            String strValue = str.getJavaStringValue();
            RawValue result = new RawValue(this.getStatement());
            result.setValue(StringToRaw.stringToBytes(strValue, true));
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }

    public static byte[] stringToBytes(String strValue, boolean unescape) {
        if (unescape) {
            //
            // NOTE: the replaces must be run in correct order.
            //
            strValue = StringToRaw.unescapeString(strValue);
            return strValue.getBytes();
        }
        else {
            return strValue.getBytes();
        }
    }
    
    //
    // NOTE: see raw:rawToStringType for escaping string.
    //
    public static String unescapeString(String strValue) {
        return strValue.replaceAll("\\\\\"", "\"") // replace double-quotes
                        .replaceAll("\\\\n", "\n") // replace new-lines
                        .replaceAll("\\\\r", "\r") // replace carriage-return
                        .replaceAll("\\\\t", "\t") // replace tab
                        .replaceAll("\\\\f", "\f") // replace form-feed
                        .replaceAll("\\\\b", "\b") // replace backspace
                        .replaceAll("\\\\\\\\", "\\\\"); // replace backslash: "\\foo" -> "\foo"
    }
}