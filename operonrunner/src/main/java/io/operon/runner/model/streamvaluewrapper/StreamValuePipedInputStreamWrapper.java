/** OPERON-LICENSE **/
package io.operon.runner.model.streamvaluewrapper;

import java.io.InputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.nio.CharBuffer;
import java.io.Closeable;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.model.exception.OperonGenericException;

//
// Used in exec-component
//
public class StreamValuePipedInputStreamWrapper implements StreamValueWrapper {

    private boolean supportsJson = false;
    
    private PipedInputStream is;

    public StreamValuePipedInputStreamWrapper() {}
    
    public StreamValuePipedInputStreamWrapper(PipedInputStream is) {
        this.is = is;
    }

    public void setPipedInputStream(PipedInputStream is) {
        this.is = is;
    }

    public PipedInputStream getPipedInputStream() {
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