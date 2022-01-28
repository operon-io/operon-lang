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

package io.operon.runner.processor.function.core.string;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.core.raw.RawToStringType;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/*
 * Collects substrings by given regex.
 *
 **/
public class StringCollect extends BaseArity1 implements Node, Arity1 {
    
    public StringCollect(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "collect", "regex");
    }

    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();

            StringType jsStr = (StringType) currentValue.evaluate();
            String strValue = jsStr.getJavaStringValue();
            
            StringType patternJsStr = (StringType) this.getParam1().evaluate();
            String patternStr = patternJsStr.getJavaStringValue();
            
            patternStr = new String(StringToRaw.stringToBytes(patternStr, true));
            
            Pattern p = Pattern.compile(patternStr);
            Matcher m = p.matcher(strValue);
            
            ArrayType result = new ArrayType(this.getStatement());
            
            int startIndex = 0;
            while (m.find(startIndex)) {
                int start = m.start();
                int end = m.end();
                int groupCount = m.groupCount();
                //System.out.println("Group count :: " + groupCount);
                if (groupCount > 0) {
                    ObjectType g = new ObjectType(currentValue.getStatement());
                    for (int i = 0; i <= groupCount; i ++) {
                        String regionMatch = m.group(i);
                        regionMatch = RawToStringType.sanitizeForStringType(regionMatch);
                        StringType js = new StringType(currentValue.getStatement());
                        js.setFromJavaString(regionMatch);
                        
                        PairType pair = new PairType(currentValue.getStatement());
                        if (i == 0) {
                            pair.setPair("\"all\"", js);
                        }
                        else {
                            pair.setPair("\"" + i + "\"", js);
                        }
                        g.addPair(pair);
                    }
                    result.getValues().add(g);
                }
                else {
                    String regionMatch = m.group();
                    regionMatch = RawToStringType.sanitizeForStringType(regionMatch);
                    StringType js = new StringType(currentValue.getStatement());
                    js.setFromJavaString(regionMatch);
                    result.getValues().add(js);
                }
                startIndex = end;
            }
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "string", e.getMessage());
            return null;
        }
    }
    
}