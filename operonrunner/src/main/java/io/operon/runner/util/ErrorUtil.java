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

package io.operon.runner.util;

import java.io.IOException;

import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ErrorValue;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.Main;
import io.operon.runner.model.exception.OperonGenericException;

public class ErrorUtil {

    //
    // NOTE: this is the main-method to create error-values
    // NOTE: if the ErrorValue must be thrown, then use createErrorValueAndThrow -method.
    //
    public static ErrorValue createErrorValue(Statement stmt, String type, String code, String message) {
        if (stmt == null) {
            try {
              stmt = new DefaultStatement(new OperonContext());
            } catch (IOException ioe) {
                
            }
        }
        ErrorValue errorValue = new ErrorValue(stmt);
        errorValue.setType(type);
        errorValue.setCode(code);
        if (message != null && message.isEmpty() == false) {
            message = message.replaceAll("\"","\\\\\"");
            message = ErrorUtil.sanitizeValue(message);
        }
        else {
            message = "";
        }
        errorValue.setMessage(message);
        return errorValue;
    }
    
    public static ErrorValue createErrorValue(Statement stmt, String type, String code, String message, OperonValue json) {
        if (stmt == null) {
            try {
              stmt = new DefaultStatement(new OperonContext());
            } catch (IOException ioe) {
                
            }
        }
        ErrorValue errorValue = new ErrorValue(stmt);
        errorValue.setType(type);
        errorValue.setCode(code);
        errorValue.setErrorJson(json);
        if (message != null && message.isEmpty() == false) {
            message = message.replaceAll("\"","\\\\\"");
            message = ErrorUtil.sanitizeValue(message);
        }
        else {
            message = "";
        }
        errorValue.setMessage(message);
        return errorValue;
    }
    
    //
    // NOTE: this should be the main-method to create exceptions and also throw them
    //
    public static ErrorValue createErrorValueAndThrow(Statement stmt, String type, String code, String message) throws OperonGenericException {
        ErrorValue errorValue = ErrorUtil.createErrorValue(stmt, type, code, message);
        if (stmt != null) {
            stmt.getOperonContext().addStackTraceElement(errorValue);
        }
        OperonGenericException oge = new OperonGenericException(errorValue);
        throw oge;
    }
    
    public static String sanitizeValue(String value) {
        String result = ErrorUtil.convertToOperonTypesFromJavaObjects(value);
        result = ErrorUtil.cleanAllPackageNames(result);
        return result;
    }
    
    public static String convertToOperonTypesFromJavaObjects(String value) {
        String result = value.replaceAll("ArrayType", "Array")
            .replaceAll("ObjectType", "Object")
            .replaceAll("StringType", "String")
            .replaceAll("NumberType", "Number")
            .replaceAll("NullType", "Null")
            .replaceAll("EmptyType", "Empty")
            .replaceAll("EndValueType", "End")
            .replaceAll("TrueType", "True")
            .replaceAll("FalseType", "False")
            .replaceAll("RawValue", "Raw")
            .replaceAll("StreamValue", "Stream")
            .replaceAll("ErrorValue", "Error")
            .replaceAll("OperonValueConstraint", "Constraint");
        return result;
    }
    
    public static String cleanAllPackageNames(String value) {
        int currentLength = value.length();
        int oldLength = currentLength;
        do {
            value = ErrorUtil.cleanFirstPackageName(value);
            oldLength = currentLength;
            currentLength = value.length();
        } while (currentLength < oldLength);
        return value;
    }
    
    public static String cleanFirstPackageName(String value) {
        int startIndex = value.indexOf("io.operon.runner.");
        int endIndex = -1;
        
        if (startIndex >= 0) {
            for (int i = startIndex; i < value.length() - 1; i ++) {
                if (Character.isUpperCase(value.charAt(i))) {
                    endIndex = i;
                    break;
                }
            }
        }
        else {
            return value;
        }
        
        if (endIndex > 0) {
            StringBuilder result = new StringBuilder();
            result.append(value.substring(0, startIndex));
            result.append(value.substring(endIndex, value.length()));
            return result.toString();
        }
        
        else {
            return value; // not a valid package-name, ending with object.
        }
    }
    
    public static String mapTypeFromJavaClass(Object clazz) {
        if (clazz instanceof io.operon.runner.node.type.ArrayType) {
            return "Array";
        }
        else if (clazz instanceof io.operon.runner.node.type.ObjectType) {
            return "Object";
        }
        else if (clazz instanceof io.operon.runner.node.type.NumberType) {
            return "Number";
        }
        else if (clazz instanceof io.operon.runner.node.type.StringType) {
            return "String";
        }
        else if (clazz instanceof io.operon.runner.node.type.NullType) {
            return "Null";
        }
        else if (clazz instanceof io.operon.runner.node.type.TrueType) {
            return "True";
        }
        else if (clazz instanceof io.operon.runner.node.type.FalseType) {
            return "False";
        }
        else if (clazz instanceof io.operon.runner.node.type.EmptyType) {
            return "Empty";
        }
        else if (clazz instanceof io.operon.runner.node.type.EndValueType) {
            return "End";
        }
        else if (clazz instanceof io.operon.runner.node.type.RawValue) {
            return "Raw";
        }
        else if (clazz instanceof io.operon.runner.node.type.StreamValue) {
            return "Stream";
        }
        else if (clazz instanceof io.operon.runner.node.type.ErrorValue) {
            return "Error";
        }
        else if (clazz instanceof io.operon.runner.node.type.Path) {
            return "Path";
        }
        else {
            return "undefined json-value: " + clazz;
        }
    }
}