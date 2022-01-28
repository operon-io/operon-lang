/** OPERON-LICENSE **/
package io.operon.runner.processor.function;

import java.util.List;

import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;

public interface Arity1 {
    public String getParam1Name();
    public void setParam1(Node param1);
    public Node getParam1() throws OperonGenericException;
    public void setParams(List<Node> args, String funcName, String p1) throws OperonGenericException;
    public void setParam1AsOptional(boolean opt);
    public boolean isParam1Optional();
    public void setFunctionName(String fn);
    public String getFunctionName();
}