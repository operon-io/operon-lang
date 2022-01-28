/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.raw;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.NumberType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.node.type.StreamValue;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.streamvaluewrapper.*;

public class StreamToRaw extends BaseArity0 implements Node, Arity0 {
    
    public StreamToRaw(Statement statement) {
        super(statement);
        this.setFunctionName("toRaw");
    }

    public RawValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            StreamValue sv = (StreamValue) currentValue.evaluate();
            
            StreamValueWrapper svw = sv.getStreamValueWrapper();
            
            RawValue result = new RawValue(currentValue.getStatement());
            
            if (svw instanceof StreamValueByteArrayWrapper) {
                byte[] targetArray = new byte[((StreamValueByteArrayWrapper) svw).getByteArrayInputStream().available()];
                ((StreamValueByteArrayWrapper) svw).getByteArrayInputStream().read(targetArray);
                result.setValue(targetArray);
            }
            
            else if (svw instanceof StreamValuePipedInputStreamWrapper) {
                byte[] targetArray = new byte[((StreamValuePipedInputStreamWrapper) svw).getPipedInputStream().available()];
                ((StreamValuePipedInputStreamWrapper) svw).getPipedInputStream().read(targetArray);
                result.setValue(targetArray);
            }
            
            else {
                throw new Exception("could not recognize stream-type");
            }
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "stream", e.getMessage());
            return null;
        }
    }

}