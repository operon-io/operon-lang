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

package io.operon.runner.processor.binary;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Formatter;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.processor.BaseBinaryNodeProcessor;
import io.operon.runner.processor.BinaryNodeProcessor;
import io.operon.runner.processor.binary.logical.Eq;
import io.operon.runner.processor.function.core.path.PathReclude;
import io.operon.runner.processor.function.core.path.PathValue;
import io.operon.runner.processor.function.core.raw.RawEvaluate;
import io.operon.runner.processor.function.core.string.StringToRaw;
import io.operon.runner.statement.Statement;
import io.operon.runner.IrTypes;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

import com.google.gson.annotations.Expose;

/**
 * 
 * 
 * 
 */
public class Modulus extends BaseBinaryNodeProcessor implements BinaryNodeProcessor {

    @Expose private String binaryOperator = "%";

    public OperonValue process(Statement statement, Node lhs, Node rhs) throws OperonGenericException {
        this.preprocess(statement, lhs, rhs);
        //System.out.println("LHS: " + lhsResult.getClass().getName());
        //System.out.println("RHS: " + rhsResult.getClass().getName());
        if ( customBindingCheck(lhs, rhs, binaryOperator) ) {
            return doCustomBinding(statement, lhs, rhs, binaryOperator);
        }
        
        else if (lhsResult instanceof NumberType && rhsResult instanceof NumberType) {
            NumberType result = new NumberType(statement);
            
            NumberType lhsResultJN = (NumberType) lhsResult;
            NumberType rhsResultJN = (NumberType) rhsResult;
            
            double doubleResult = lhsResultJN.getDoubleValue() % rhsResultJN.getDoubleValue();
            result.setDoubleValue(doubleResult);
            
            if (lhsResultJN.getPrecision() != -1 && rhsResultJN.getPrecision() != -1
                && lhsResultJN.getPrecision() == 0 && rhsResultJN.getPrecision() == 0) {
                result.setPrecision((byte) 0);
            }
            else {
                byte precision = NumberType.getPrecisionFromStr(Double.toString(doubleResult));
                result.setPrecision(precision);
            }
            
            statement.setCurrentValue(result);
            return result;
        }
        
        //
        // Complement of intersection (logically minus-operation) (done with EQ-op)
        //
        // Warning: this is very inefficient solution and must be optimized by using
        //          the Of -method (use HashTables).
        //
        else if (lhsResult instanceof ArrayType && rhsResult instanceof ArrayType) {
            ArrayType lhsArray = (ArrayType) lhsResult;
            ArrayType rhsArray = (ArrayType) rhsResult;
            
            List<Node> lhsValues = lhsArray.getValues();
            List<Node> rhsValues = rhsArray.getValues();
            List<Node> resultArray = new ArrayList<Node>();
            
            //
            // Optimized modulus for StringType
            //
            if (lhsArray.getArrayValueType() == IrTypes.STRING_TYPE && rhsArray.getArrayValueType() == IrTypes.STRING_TYPE) {
                Map<String, Integer> lhsMap = new HashMap<String, Integer>();
                Map<String, Integer> rhsMap = new HashMap<String, Integer>();
                Map<String, Boolean> deleteMap = new HashMap<String, Boolean>();
                
                for (int i = 0; i < lhsValues.size(); i ++) {
                    String value = ((StringType) lhsValues.get(i).evaluate()).getJavaStringValue();
                    Integer count = lhsMap.get(value);
                    if (count == null) {
                        lhsMap.put(value, 1);
                    }
                    else {
                        lhsMap.put(value, count + 1);
                    }
                }
                
                for (int i = 0; i < rhsValues.size(); i ++) {
                    String value = ((StringType) rhsValues.get(i).evaluate()).getJavaStringValue();
                    if (lhsMap.get(value) == null) {
                        Integer count = rhsMap.get(value);
                        if (count == null) {
                            rhsMap.put(value, 1);
                        }
                        else {
                            rhsMap.put(value, count + 1);
                        }
                    }
                    else {
                        deleteMap.put(value, true);
                    }
                }
                
                for (Map.Entry<String, Boolean> entry : deleteMap.entrySet()) {
                    lhsMap.remove(entry.getKey());
                    rhsMap.remove(entry.getKey());
                }
                
                for (Map.Entry<String, Integer> entry : lhsMap.entrySet()) {
                    Integer count = entry.getValue();
                    String key = entry.getKey();
                    for (int i = 0; i < count; i ++) {
                        StringType strValue = StringType.create(statement, key);
                        resultArray.add(strValue);
                    }
                }
                
                for (Map.Entry<String, Integer> entry : rhsMap.entrySet()) {
                    Integer count = entry.getValue();
                    String key = entry.getKey();
                    for (int i = 0; i < count; i ++) {
                        StringType strValue = StringType.create(statement, key);
                        resultArray.add(strValue);
                    }
                }
                
                ArrayType result = new ArrayType(statement);
                result.getValues().addAll(resultArray);
                return result;
            }
            
            else {
                //System.out.println("lhs=" + lhsArray);
                //System.out.println("rhs=" + rhsArray);
                
                List<Integer> skipLhsPositions = new ArrayList<Integer>();
                List<Integer> skipRhsPositions = new ArrayList<Integer>();
                Eq eqOp = new Eq();
                
                for (int i = 0; i < lhsValues.size(); i ++) {
                    //System.out.println("=====================");
                    boolean found = false;
                    for (int j = 0; j < rhsValues.size(); j ++) {
                        
                        try {
                            //System.out.println("compare: " + lhsValues.get(i) + " :: " + rhsValues.get(j));
                            Node eqOpResult = eqOp.process(statement, lhsValues.get(i), rhsValues.get(j));
                            if (eqOpResult instanceof TrueType) {
                                found = true;
                                skipRhsPositions.add(j);
                                //System.out.println("  --> FOUND");
                                //break; // do not stop eagerly, otherwise the duplicates might remain
                            }
                            else {
                                //System.out.println("  - x");
                            }
                        } catch (OperonGenericException oge) {
                            // Some EQ-operations are not defined.
                            // In this case also complement.
                        }
                    }
                    if (found == false) {
                        //System.out.println("  --> ADDING");
                        skipLhsPositions.add(i);
                        resultArray.add(lhsValues.get(i));
                    }
                    else {
                        //System.out.println("  --> SKIPPING");
                    }
                }
    
                for (int i = 0; i < rhsValues.size(); i ++) {
                    //System.out.println("=====================");
                    boolean found = false;
                    if (skipRhsPositions.contains(i)) {
                        continue;
                    }
                    //System.out.println("* Check rhs :: " + i + " --> " + rhsValues.get(i));
                    for (int j = 0; j < lhsValues.size(); j ++) {
                        if (skipLhsPositions.contains(j)) {
                            //System.out.println("  --> SKIP :: " + j + " --> " + lhsValues.get(j));
                            continue;
                        }
                        try {
                            //System.out.println("compare: " + rhsValues.get(i) + " :: " + lhsValues.get(j));
                            Node eqOpResult = eqOp.process(statement, rhsValues.get(i), lhsValues.get(j));
                            if (eqOpResult instanceof TrueType) {
                                found = true;
                                //System.out.println("  --> FOUND");
                                break;
                            }
                            else {
                                //System.out.println("  - x");
                            }
                        } catch (OperonGenericException oge) {
                            // Some EQ-operations are not defined.
                            // In this case also complement.
                        }
                    }
                    if (found == false) {
                        //System.out.println("  --> ADDING");
                        skipLhsPositions.add(i);
                        resultArray.add(rhsValues.get(i));
                    }
                    else {
                        //System.out.println("  --> SKIPPING");
                    }
                }
    
                ArrayType result = new ArrayType(statement);
                result.getValues().addAll(resultArray);
                return result;
            }
        }
        
        else if (lhsResult instanceof ObjectType && rhsResult instanceof ArrayType) {
            // Run PathReclude
            ObjectType obj = (ObjectType) lhsResult;
            ArrayType pathsArray = (ArrayType) rhsResult;
            if (pathsArray.getValues().size() > 0) {
                Path p0 = (Path) pathsArray.getValues().get(0);
                p0.setObjLink(obj);
                statement.setCurrentValue(pathsArray);
                PathReclude pathReclude = new PathReclude(statement, new ArrayList<Node>());
                OperonValue result = pathReclude.evaluate();
                return result;
            }
            else {
                return lhsResult;
            }
        }
        
        // complement of intersection based on any single value.
        // rhs may be any value (other than ArrayType)
        else if (lhsResult instanceof ArrayType) {
            ArrayType lhsArray = (ArrayType) lhsResult;
            List<Node> lhsValues = lhsArray.getValues();
            List<Node> resultArray = new ArrayList<Node>();
            
            for (int i = 0; i < lhsValues.size(); i ++) {
                Eq eqOp = new Eq();
                boolean found = false;
                try {
                    Node eqOpResult = eqOp.process(statement, lhsValues.get(i), rhsResult);
                    if (eqOpResult instanceof TrueType) {
                        found = true;
                    }
                } catch (OperonGenericException oge) {
                    // Some EQ-operations are not defined.
                    // In this case also complement.
                }
                if (found == false) {
                    resultArray.add(lhsValues.get(i));
                }
            }

            ArrayType result = new ArrayType(statement);
            result.getValues().addAll(resultArray);
            return result;
        }
        
        // 
        // "Foo %s" % "Bar"
        // #> "Foo Bar"
        // 
        else if (lhsResult instanceof StringType && rhsResult instanceof StringType) {
            StringType lhsString = (StringType) lhsResult;
            StringType rhsString = (StringType) rhsResult;
            String lhsStringValue = lhsString.getJavaStringValue();
            String rhsStringValue = rhsString.getJavaStringValue();
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            lhsStringValue = fmt.format(lhsStringValue, rhsStringValue).toString();
            StringType result = StringType.create(statement, lhsStringValue);
            return result;
        }
        
        // 
        // `Foo %s` % "Bar"
        // #> Foo Bar
        // 
        else if (lhsResult instanceof RawValue && rhsResult instanceof StringType) {
            RawValue lhsRaw = (RawValue) lhsResult;
            StringType rhsString = (StringType) rhsResult;
            String lhsStringValue = lhsRaw.toRawString();
            String rhsStringValue = rhsString.getJavaStringValue();
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            lhsStringValue = fmt.format(lhsStringValue, rhsStringValue).toString();
            RawValue result = RawValue.createFromString(statement, lhsStringValue);
            return result;
        }
        
        // 
        // `Foo %s` % `Bar`
        // #> Foo Bar
        // 
        else if (lhsResult instanceof RawValue && rhsResult instanceof RawValue) {
            RawValue lhsRaw = (RawValue) lhsResult;
            RawValue rhsRaw = (RawValue) rhsResult;
            String lhsStringValue = lhsRaw.toRawString();
            String rhsStringValue = rhsRaw.toRawString();
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            lhsStringValue = fmt.format(lhsStringValue, rhsStringValue).toString();
            RawValue result = RawValue.createFromString(statement, lhsStringValue);
            return result;
        }
        
        // 
        // "Foo %s" % `Bar`
        // #> "Foo Bar"
        // 
        else if (lhsResult instanceof StringType && rhsResult instanceof RawValue) {
            StringType lhsString = (StringType) lhsResult;
            RawValue rhsRaw = (RawValue) rhsResult;
            String lhsStringValue = lhsString.getJavaStringValue();
            String rhsStringValue = rhsRaw.toRawString();
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            lhsStringValue = fmt.format(lhsStringValue, rhsStringValue).toString();
            StringType result = StringType.create(statement, lhsStringValue);
            return result;
        }
        
        // 
        // :"Foo %s %s" % ["Bin", "Bai"]
        // #> "Foo Bin Bai"
        //
        // :"%s,age=%.0f" % ["Foo", 9]
        // #> "Foo,age=9"
        //
        else if (lhsResult instanceof StringType && rhsResult instanceof ArrayType) {
            StringType lhsString = (StringType) lhsResult;
            ArrayType rhsArray = (ArrayType) rhsResult;
            String lhsStringValue = lhsString.getJavaStringValue();
            
            List<Object> items = new ArrayList<Object>();
            
            for (int i = 0; i < rhsArray.getValues().size(); i ++) {
                OperonValue value = rhsArray.getValues().get(i).evaluate();
                
                if (value instanceof StringType) {
                    items.add(((StringType) value).getJavaStringValue());
                }
                else if (value instanceof RawValue) {
                    items.add(((RawValue) value).toRawString());
                }
                else if (value instanceof NumberType) {
                    items.add(((NumberType) value).getDoubleValue());
                }
            }
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            Object [] fmtParams = items.toArray();
            lhsStringValue = fmt.format(lhsStringValue, fmtParams).toString();
            StringType result = StringType.create(statement, lhsStringValue);
            return result;
        }
        
        // 
        // :`Foo %s %s` % ["Bin", `Bai`]
        // #> Foo Bin Bai
        //
        // :`%s,age=%.0f` % ["Foo", 9]
        // #> Foo,age=9
        //
        else if (lhsResult instanceof RawValue && rhsResult instanceof ArrayType) {
            RawValue lhsRaw = (RawValue) lhsResult;
            ArrayType rhsArray = (ArrayType) rhsResult;
            String lhsStringValue = lhsRaw.toRawString();
            
            List<Object> items = new ArrayList<Object>();
            
            for (int i = 0; i < rhsArray.getValues().size(); i ++) {
                OperonValue value = rhsArray.getValues().get(i).evaluate();
                
                if (value instanceof StringType) {
                    items.add(((StringType) value).getJavaStringValue());
                }
                else if (value instanceof RawValue) {
                    items.add(((RawValue) value).toRawString());
                }
                else if (value instanceof NumberType) {
                    items.add(((NumberType) value).getDoubleValue());
                }
            }
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            Object [] fmtParams = items.toArray();
            lhsStringValue = fmt.format(lhsStringValue, fmtParams).toString();
            RawValue result = RawValue.createFromString(statement, lhsStringValue);
            return result;
        }
        
        // 
        // "PI=%0.2f" % 3.141592
        // #> "PI=3.14"
        // 
        else if (lhsResult instanceof StringType && rhsResult instanceof NumberType) {
            StringType lhsString = (StringType) lhsResult;
            NumberType rhsNumber = (NumberType) rhsResult;
            String lhsStringValue = lhsString.getJavaStringValue();
            double rhsNumberValue = (double) rhsNumber.getDoubleValue();
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            lhsStringValue = fmt.format(lhsStringValue, rhsNumberValue).toString();
            StringType result = StringType.create(statement, lhsStringValue);
            
            return result;
        }
        
        // 
        // `PI=%0.2f` % 3.141592
        // #> `PI=3.14`
        // 
        else if (lhsResult instanceof RawValue && rhsResult instanceof NumberType) {
            RawValue lhsRaw = (RawValue) lhsResult;
            NumberType rhsNumber = (NumberType) rhsResult;
            String lhsStringValue = lhsRaw.toRawString();
            double rhsNumberValue = (double) rhsNumber.getDoubleValue();
            Formatter fmt = new Formatter(NumberType.defaultLocale);
            lhsStringValue = fmt.format(lhsStringValue, rhsNumberValue).toString();
            RawValue result = RawValue.createFromString(statement, lhsStringValue);
            
            return result;
        }
        
        //
        // Automatically extract the keys
        // 
        else if (lhsResult instanceof StringType && rhsResult instanceof ObjectType) {
            String lhsStringValue = interpolateStringWithObj(
                statement, ((StringType) lhsResult).getJavaStringValue(), (ObjectType) rhsResult, lhs, rhs, true
            );
            StringType result = StringType.create(statement, lhsStringValue);
            return result;
        }
        
        else if (lhsResult instanceof RawValue && rhsResult instanceof ObjectType) {
            String lhsStringValue = interpolateStringWithObj(
                statement, ((RawValue) lhsResult).toRawString(), (ObjectType) rhsResult, lhs, rhs, false
            );
            RawValue result = RawValue.createFromString(statement, lhsStringValue);
            return result;
        }
        
        else {
            //:OFF:log.error("INCOMPATIBLE TYPES: " + lhsResult.getClass() + ", " + rhsResult.getClass());
            
            String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
            String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
            return ErrorUtil.createErrorValueAndThrow(statement,
                "OPERATOR", 
                "MODULUS", 
                "Not defined: " + lhsType + " " + binaryOperator + " " + rhsType +
                    ", at line #" + this.getSourceCodeLineNumber() +
                    ". lhs value: " + lhs.toString() + ", rhs value: " + rhs.toString()
            );
        }
        
    }

    private String interpolateStringWithObj(Statement statement, String lhsStringValue,
            ObjectType rhsObject, Node lhs, Node rhs, boolean unescapeExpr) throws OperonGenericException {
        //StringType lhsString = (StringType) lhsResult;
        //ObjectType rhsObject = (ObjectType) rhsResult;
        //String lhsStringValue = lhsString.getJavaStringValue();
        
        List<Object> items = new ArrayList<Object>();
        
        // PARAM-STRING:
        // "Foo %s:key1; %f:key2;"
        
        // First seek the key candidates
        
        boolean extractMode = false;
        boolean fmtMode = false;
        boolean exprMode = false;
        
        String extractedKey = "";
        String extractedFmt = "";
        String extractedExpr = "";
        String channel = "";
        
        StringBuilder resultStr = new StringBuilder();
        for (int i = 0; i < lhsStringValue.length(); i ++) {
            if (lhsStringValue.charAt(i) == '%') {
                fmtMode = true; // start to collect the formatting option
            }
            // Collect the formatting option, e.g. "%s" or "%.2f"
            else if (fmtMode && lhsStringValue.charAt(i) == ':') {
                fmtMode = false;
                extractMode = true;
                channel += lhsStringValue.charAt(i);
                continue;
            }
            // Collect the optional expr, for which the value will be
            // evaluated against
            else if (extractMode && lhsStringValue.charAt(i) == ':') {
                fmtMode = false;
                extractMode = false;
                exprMode = true;
                channel += lhsStringValue.charAt(i);
                continue;
            }
            else if ((extractMode || exprMode) && lhsStringValue.charAt(i) == ';') {
                extractMode = false;
                exprMode = false;
                
                //System.out.println("fmt=" + extractedFmt);
                //System.out.println("expr=" + extractedExpr);
                
                //
                // Convert extractedKey into PathValue and use that to access the value
                // If does not start with "." or "[", then assume that accesses a field,
                // and append with "."
                //
                if (extractedKey.startsWith(".") == false && extractedKey.startsWith("[") == false) {
                    extractedKey = "." + extractedKey;
                }
                //System.out.println("KEY :: " + extractedKey);
                OperonValue value = PathValue.get(rhsObject, extractedKey);
                
                if (extractedExpr.isEmpty() == false) {
                    try {
                        if (unescapeExpr) {
                            extractedExpr = StringToRaw.unescapeString(extractedExpr);
                        }
                        value = RawEvaluate.evaluate(statement, extractedExpr, value);
                    } catch (Exception e) {
                        String lhsType = ErrorUtil.mapTypeFromJavaClass(lhsResult);
                        String rhsType = ErrorUtil.mapTypeFromJavaClass(rhsResult);
                        ErrorUtil.createErrorValueAndThrow(statement,
                            "OPERATOR", 
                            "MODULUS", 
                            "String-templating" +
                                ", at line #" + this.getSourceCodeLineNumber() +
                                ". Input value: " + value.toString() +
                                ". Unable to compile expression: " + extractedExpr
                        );
                    }
                }
                
                // Next line left here as commented, until the related Bug has been resolved
                // OperonValue value = rhsObject.getByKey(extractedKey).evaluate();

                Formatter fmt = new Formatter(NumberType.defaultLocale);
                
                // TODO: check the kind of extractedFmt that matches the type?
                if (value instanceof StringType) {
                    resultStr.append(fmt.format(extractedFmt, ((StringType) value).getJavaStringValue()).toString());
                }
                else if (value instanceof NumberType) {
                    resultStr.append(fmt.format(extractedFmt, ((NumberType) value).getDoubleValue()).toString());
                }
                extractedFmt = "";
                extractedKey = "";
                extractedExpr = "";
                channel = "";
                continue;
            }
            if (extractMode) {
                extractedKey += lhsStringValue.charAt(i);
                channel += lhsStringValue.charAt(i);
            }
            else if (fmtMode) {
                extractedFmt += lhsStringValue.charAt(i);
                channel += lhsStringValue.charAt(i);
            }
            else if (exprMode) {
                extractedExpr += lhsStringValue.charAt(i);
                channel += lhsStringValue.charAt(i);
            }
            else {
                resultStr.append(lhsStringValue.charAt(i));
            }
        }

        // If not terminated with ";"
        if (channel.length() > 0) {
            resultStr.append(channel);
        }

        lhsStringValue = resultStr.toString();
        return lhsStringValue;
    }
}
