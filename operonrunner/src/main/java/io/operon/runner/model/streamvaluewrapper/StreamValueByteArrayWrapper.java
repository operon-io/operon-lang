/** OPERON-LICENSE **/
package io.operon.runner.model.streamvaluewrapper;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.CharBuffer;
import java.io.Closeable;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.model.exception.OperonGenericException;

public class StreamValueByteArrayWrapper implements StreamValueWrapper {

    private boolean supportsJson = false;
    
    private ByteArrayInputStream is;

    public StreamValueByteArrayWrapper() {}
    
    public StreamValueByteArrayWrapper(ByteArrayInputStream is) {
        this.is = is;
    }

    public void setByteArrayInputStream(ByteArrayInputStream is) {
        this.is = is;
    }

    public ByteArrayInputStream getByteArrayInputStream() {
        return this.is;
    }

    public boolean supportsJson() {
        return this.supportsJson;
    }
    
    public void setSupportsJson(boolean sj) {
        this.supportsJson = false;
    }

    public void close() throws IOException {
        this.is.close();
    }
    
    public OperonValue readJson() throws OperonGenericException {
        return null;
    }

}