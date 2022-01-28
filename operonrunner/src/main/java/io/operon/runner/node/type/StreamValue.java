/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import java.io.InputStream;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.streamvaluewrapper.StreamValueWrapper;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

public class StreamValue extends OperonValue implements Node, AtomicOperonValue {

    private StreamValueWrapper value;
    
    public StreamValue(Statement stmnt) {
        super(stmnt);
    }

    public StreamValue evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    public void setValue(StreamValueWrapper value) {
        this.value = value;
    }
    
    public OperonValue getValue() {
        return this;
    }
    
    public StreamValueWrapper getStreamValueWrapper() {
        return this.value;
    }

    @Override
    public String toString() {
        return "\"Stream()\"";
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        return "\"Stream()\"";
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        return "\"Stream()\"";
    }

}