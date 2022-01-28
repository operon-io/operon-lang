/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.raw;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.processor.function.core.string.StringToRaw;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/*
 * Collects substrings by given regex.
 *
 **/
public class SRawCollect extends BaseArity1 implements Node, Arity1 {
    
    public SRawCollect(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "collect", "regex");
    }

    public ArrayType evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();

            RawValue raw = (RawValue) currentValue.evaluate();
            String strValue = new String(raw.getBytes());

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
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw", e.getMessage());
            return null;
        }
    }
    
}