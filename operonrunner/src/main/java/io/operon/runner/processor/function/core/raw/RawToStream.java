/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.raw;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.streamvaluewrapper.StreamValueWrapper;
import io.operon.runner.model.streamvaluewrapper.StreamValueByteArrayWrapper;

public class RawToStream extends BaseArity0 implements Node, Arity0 {
    
    public RawToStream(Statement statement) {
        super(statement);
        this.setFunctionName("toStream");
    }

    public StreamValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            RawValue raw = (RawValue) currentValue.evaluate();
            
            StreamValue result = new StreamValue(currentValue.getStatement());
            ByteArrayInputStream is = new ByteArrayInputStream(raw.getBytes());
            StreamValueWrapper svw = new StreamValueByteArrayWrapper(is);
            result.setValue(svw);
            
            return result;
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "raw:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}