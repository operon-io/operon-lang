/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.raw;

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

public class RawFromBase64 extends BaseArity1 implements Node, Arity1 {
    
    public RawFromBase64(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "fromBase64", "options");
    }

    public RawValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StringType currentValueStr = (StringType) currentValue.evaluate();
            
            RawValue result = new RawValue(this.getStatement());
            
            if (this.getParam1() != null) {
                Info info = this.resolve(currentValue);
                if (info.decoder == Base64Decoder.URLSAFE) {
                    byte[] resultBytes = RawValue.base64UrlSafeToBytes(currentValueStr.getJavaStringValue().getBytes());
                    result.setValue(resultBytes);
                    return result;
                }
                // TODO: mime-encoder
                else {
                    byte[] resultBytes = RawValue.base64ToBytes(currentValueStr.getJavaStringValue().getBytes());
                    result.setValue(resultBytes);
                    return result;
                }
            }
            
            else {
                byte[] resultBytes = RawValue.base64ToBytes(currentValueStr.getJavaStringValue().getBytes());
                result.setValue(resultBytes);
                return result;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw", e.getMessage());
            return null;
        }
    }

    public Info resolve(OperonValue currentValue) throws Exception, OperonGenericException {
        List<PairType> jsonPairs = ((ObjectType) this.getParam1().evaluate()).getPairs();

        Info info = new Info();
        
        for (PairType pair : jsonPairs) {
            String key = pair.getKey();
            pair.getStatement().setCurrentValue(currentValue);
            switch (key.toLowerCase()) {
                case "\"decoder\"":
                    String decoderStr = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                    info.decoder = Base64Decoder.valueOf(decoderStr.toUpperCase());
                    break;
                default:
                    throw new Exception("unknown option: " + key);
            }
        }
        
        return info;
    }

    private class Info {
        private Base64Decoder decoder = Base64Decoder.BASIC;
    }
    
    private enum Base64Decoder {
        BASIC, URLSAFE, MIME;
    }

}