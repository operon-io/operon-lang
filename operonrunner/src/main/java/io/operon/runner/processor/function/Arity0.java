/** OPERON-LICENSE **/
package io.operon.runner.processor.function;

import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;

public interface Arity0 {
    public void setFunctionName(String fn);
    public String getFunctionName();
}