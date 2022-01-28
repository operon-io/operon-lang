/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.raw;

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

//
// Same as 'binaryvalue' => core:string() # use string-casting
//
public class RawToStringType extends BaseArity0 implements Node, Arity0 {
    
    public RawToStringType(Statement statement) {
        super(statement);
        this.setFunctionName("toString");
    }

    public StringType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StringType result = RawToStringType.rawToStringType(this.getStatement(), (RawValue) currentValue.evaluate(), true);
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static StringType rawToStringType(Statement stmt, RawValue bv, Boolean escape) throws OperonGenericException {
        String rawStr = new String(bv.getBytes());
        if (escape) {
            rawStr = RawToStringType.sanitizeForStringType(rawStr);
        }
        StringType result = new StringType(stmt);
        // TODO: check if escape: 
        //     this is a larger problem: how to propagate escape-flag to all functions?
        //
        // --> escape should be global flag?
        //    set escape-flag globally? => options:set/add({"escape": false}) => string:replaceFirst() => binary({"unescape": false})
        //
        //  Another option: duplicate the string-functions for Binary-types.
        result.setFromJavaString(rawStr);
        return result;
    }
    
    public static String sanitizeForStringType(String strValue) {
        //
        // NOTE: the replaces must be run in correct order.
        //
        strValue = strValue.replaceAll("\\\\", "\\\\\\\\") // replace backslash: "\foo" -> "\\foo"
                            .replaceAll("\b", "\\\\b") // replace backspace
                            .replaceAll("\f", "\\\\f") // replace form-feed
                            .replaceAll("\t", "\\\\t") // replace tab
                            .replaceAll("\r", "\\\\r") // replace carriage-return
                            .replaceAll("\n", "\\\\n") // replace new-lines
                            .replaceAll("\"", "\\\\\""); // replace double-quotes
        return strValue;
    }
}