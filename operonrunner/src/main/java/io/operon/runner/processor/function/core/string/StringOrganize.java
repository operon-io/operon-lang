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

package io.operon.runner.processor.function.core.string;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Input: String
// Process: converts Atring into an Array, by collecting the parts separated by WS or tabulators.
// Output: Array
//
// NOTE: this is same code as in SRawOrganize
//
public class StringOrganize extends BaseArity1 implements Node, Arity1 {
    
    public StringOrganize(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "organize", "options"); // TODO: implement options.
    }

    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            StringType jsStr = (StringType) currentValue.evaluate();
            String str = jsStr.getJavaStringValue();
            byte[] bytes = StringToRaw.stringToBytes(str, true);
            String strValue = new String(bytes);
            
            Statement stmt = currentValue.getStatement();
            ObjectType options = null;
            
            Boolean multiLine = null; // when not set, this is set automatically.
            boolean skipfirstLine = false;
            boolean trim = true;
            boolean parseQuotedStrings = true; // e.g.: 'foo  bar  "baz"' --> ["foo", "bar", "baz"]
                                                // if false, then: ["foo", "bar", "\"baz\""]
            boolean sparseResults = true;
            String singleSeparators = ";\t";  // semicolon and tabulator are set by default.  
            String lineSeparator = "\\n"; // Linux: \\n, Windows: \\r\\n
            List<String> includelinesstartingwith = null;
            int readFirst = 0;
            
            if (this.getParam1() != null) {
                options = (ObjectType) this.getParam1().evaluate();
                for (int i = 0; i < options.getPairs().size(); i ++) {
                    PairType pair = options.getPairs().get(i);
                    switch (pair.getKey().toLowerCase()) {
                        case "\"multiline\"":
                            OperonValue multiLineValue = pair.getValue().evaluate();
                            if (multiLineValue instanceof TrueType) {
                                multiLine = true;
                            }
                            else {
                                multiLine = false;
                            }
                            break;
                        case "\"skipfirstline\"":
                            multiLine = true;
                            OperonValue skipFirstLineValue = pair.getValue().evaluate();
                            if (skipFirstLineValue instanceof TrueType) {
                                skipfirstLine = true;
                            }
                            else {
                                skipfirstLine = false;
                            }
                            break;
                        case "\"trim\"":
                            OperonValue trimValue = pair.getValue().evaluate();
                            if (trimValue instanceof FalseType) {
                                trim = false;
                            }
                            else {
                                trim = true;
                            }
                            break;
                        case "\"parsequotedstrings\"":
                            OperonValue parseQuotedStringsValue = pair.getValue().evaluate();
                            if (parseQuotedStringsValue instanceof FalseType) {
                                parseQuotedStrings = false;
                            }
                            else {
                                parseQuotedStrings = true;
                            }
                            break;
                        case "\"sparseresults\"":
                            sparseResults = true;
                            OperonValue sparseResultsValue = pair.getValue().evaluate();
                            if (sparseResultsValue instanceof TrueType) {
                                sparseResults = true;
                            }
                            else {
                                sparseResults = false;
                            }
                            break;
                        case "\"lineseparator\"":
                            String lineSeparatorStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                            lineSeparator = lineSeparatorStr;
                            break;
                        case "\"separators\"":
                            String separatorsStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                            separatorsStr = separatorsStr.replaceAll("\\\\n", "\n");
                            separatorsStr = separatorsStr.replaceAll("\\\\r", "\r");
                            
                            if (separatorsStr.contains("\"")) {
                                parseQuotedStrings = false;
                            }
                            separatorsStr = separatorsStr.replaceAll("\\\\\"", "\"");
                            
                            separatorsStr = separatorsStr.replaceAll("\\\\t", "\t");
                            separatorsStr = separatorsStr.replaceAll("\\\\b", "\b");
                            separatorsStr = separatorsStr.replaceAll("\\\\f", "\f");
                            separatorsStr = separatorsStr.replaceAll("\\\\\\\\", "\\\\");
                            singleSeparators = separatorsStr;
                            break;
                        case "\"includelinesstartingwith\"":
                            multiLine = true;
                            includelinesstartingwith = new ArrayList<String>();
                            ArrayType includelinesstartingwithArray = ((ArrayType) pair.getValue().evaluate());
                            for (int ii = 0; ii < includelinesstartingwithArray.getValues().size(); ii ++) {
                                String si = ((StringType) includelinesstartingwithArray.getValues().get(ii).evaluate()).getJavaStringValue();
                                //System.out.println("SI=" + si);
                                includelinesstartingwith.add(si);
                            }
                            break;
                        case "\"readfirst\"":
                            readFirst = (int) ((NumberType) pair.getValue().evaluate()).getDoubleValue();
                            break;
                    }
                }
            }
            
            if (multiLine == null) {
                if (singleSeparators.contains("\n")) {
                    multiLine = false;
                }
                else if (strValue.contains("\n")) {
                    multiLine = true;
                }
                else {
                    multiLine = false;
                }
            }
            
            //System.out.println("Organize :: " + strValue);
            if (multiLine == false) {
                return singleLine(strValue, stmt, singleSeparators, trim, parseQuotedStrings, sparseResults);
            }
            
            else {
                //String sep = System.getProperty("line.separator");
                //System.out.println("Separator :: [" + sep + "]");
                //sep = sep.replaceAll("\\", "\\\\");
                //System.out.println("Separator_ :: [" + sep + "]");
                String [] parts = strValue.split(lineSeparator);
                ArrayType result = new ArrayType(stmt);
                int startIndex = 0;
                
                if (skipfirstLine) {
                    startIndex = 1;
                }
                
                for (int i = startIndex; i < parts.length; i ++) {
                    //System.out.println("Split part: " + parts[i]);
                    if (includelinesstartingwith != null) {
                        for (int incIndex = 0; incIndex < includelinesstartingwith.size(); incIndex ++) {
                            if (parts[i].startsWith(includelinesstartingwith.get(incIndex))) {
                                ArrayType lineResult = singleLine(parts[i], stmt, singleSeparators, trim, parseQuotedStrings, sparseResults);
                                result.getValues().add(lineResult);
                                break;
                            }
                        }
                    }
                    else {
                        ArrayType lineResult = singleLine(parts[i], stmt, singleSeparators, trim, parseQuotedStrings, sparseResults);
                        result.getValues().add(lineResult);
                    }
                    if (readFirst > 0 && readFirst == i) {
                        break;
                    }
                }
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }

    private ArrayType singleLine(String strValue, Statement stmt, String singleSeparators,
                                 boolean trim, boolean parseQuotedStrings, boolean sparseResults) {
        //System.out.println("singleLine, separators=[" + singleSeparators + "]");
        
        boolean previousWS = false;
        List<Node> collectedParts = new ArrayList<Node>();
        
        StringBuilder collect = new StringBuilder();
        
        int yieldPos = 0;
        boolean doubleQuotedStringParseMode = false;
        int backslashCounter = 0;
        boolean doEscapeChar = false;
        
        for (int i = 0; i < strValue.length(); i ++) {
            char c = strValue.charAt(i);
            //System.out.println("c=[" + c + "]");
            
            if (doubleQuotedStringParseMode == false && singleSeparators.length() > 0) {
                //System.out.println("--> ss");
                boolean yielded = false;
                for (int j = 0; j < singleSeparators.length(); j ++) {
                    char sep = singleSeparators.charAt(j);
                    if (c == sep) {
                        String s = collect.toString();
                        collect.setLength(0);
                        //System.out.println("ss Encountered " + sep + ", c=" + c + ", previous WS :: " + previousWS + ", s=[" + s + "]");
                        // if previous is also same separator, then apply sparseResults, otherwise don't 
                        if (i > 0 && strValue.charAt(i - 1) == sep) {
                            this.addResult(stmt, s, collectedParts, trim, sparseResults);
                        }
                        
                        else {
                            this.addResult(stmt, s, collectedParts, trim, false);
                        }
                        previousWS = false;
                        yielded = true;
                        yieldPos = i;
                        backslashCounter = 0;
                        break;
                    }
                }
                
                if (yielded) {
                    continue;
                }
            }
            
            if (c == '\\') {
                if (i > 0 && strValue.charAt(i - 1) == '\\') {
                    backslashCounter += 1;
                }
                else {
                    backslashCounter = 1;
                }
            }
            
            else if (c == ' ') {
                //System.out.println("--> ws");
                if (previousWS) {
                    //System.out.println("  --> previousWS");
                    String s = collect.toString();
                    collect.setLength(0);
                    //System.out.println("ws Encountered WS, previous WS :: " + previousWS + ", s=" + s);
                    this.addResult(stmt, s, collectedParts, trim, false);
                    previousWS = false;
                    yieldPos = i;
                    backslashCounter = 0;
                    continue;
                }
                
                else {
                    //.out.println("  --> else:: previousWS was false");
                    previousWS = true;
                    if (i == 0) {
                        continue;
                    }
                    else if (yieldPos == i - 1) {
                        continue;
                    }
                    
                    // if next not WS then collect
                    if (i < strValue.length() - 1) {
                        //System.out.println(">> i=" + i + ", length=" + strValue.length());
                        if (strValue.charAt(i + 1) == ' ') {
                            continue;
                        }
                        else if (strValue.charAt(i + 1) == '\t') {
                            continue;
                        }
                        else {
                            collect.append(c);
                        }
                    }
                    continue;
                }
            }
            
            else if (c == '"') {
                //System.out.println("Double quote detected!, backslashCounter=" + backslashCounter);
                if (parseQuotedStrings) {
                    if (backslashCounter == 0 && doubleQuotedStringParseMode) {
                        // end parsing and yield
                        //System.out.println("  DQ :: yield");
                        String s = collect.toString();
    
                        collect.setLength(0);
                        this.addResult(stmt, s, collectedParts, trim, sparseResults);
                        previousWS = false;
                        yieldPos = i;
                        backslashCounter = 0;
                        doubleQuotedStringParseMode = false;
                        continue;
                    }
                    
                    else if ((backslashCounter == 0 || backslashCounter % 2 != 0) && (i - 1 == yieldPos) && doubleQuotedStringParseMode == false) {
                        //System.out.println("  DQ :: backslashCounter suitable for starting the doubleQuotedStringParseMode, yieldPos=" + yieldPos + ", i=" + i);
                        doubleQuotedStringParseMode = true;
                        previousWS = false;
                        continue;
                    }
                    
                    else {
                        // Don't do anything here: 
                        // Encountered backslashed double-quote,
                        // which should be collected.
                        doEscapeChar = true;
                    }
                }
                
                else {
                    doEscapeChar = true;
                }
            }
            
            //System.out.println("--> collect");
            if (doEscapeChar) {
                collect.append("\\");
                collect.append(c);
                doEscapeChar = false;
            }
            
            else {
                collect.append(c);
            }
            
            previousWS = false;
        }
        
        if (collect.capacity() > 0) {
            //System.out.println("last addResult");
            String s = collect.toString();
            collect.setLength(0);
            this.addResult(stmt, s, collectedParts, trim, sparseResults);
        }
        
        ArrayType result = new ArrayType(stmt);
        result.setValues(collectedParts);
        this.getStatement().setCurrentValue(result);
        return result;
    }

    private void addResult(Statement stmt, String s, List<Node> collectedParts, boolean trim, boolean sparseResults) {
        if (s.isEmpty() && sparseResults == false) {
            return;
        }
        
        if (trim) {
            s = s.trim();
        }
        StringType jstr = new StringType(stmt);
        jstr.setFromJavaString(s);
        collectedParts.add(jstr);
    }

}