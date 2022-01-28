/** OPERON-LICENSE **/
package io.operon.runner.model.streamvaluewrapper;

import java.io.InputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.CharBuffer;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.model.exception.OperonGenericException;

public interface StreamValueWrapper extends Closeable {

    public boolean supportsJson();
    
    public void setSupportsJson(boolean sj);

    public void close() throws IOException;
    
    public OperonValue readJson() throws OperonGenericException;

}