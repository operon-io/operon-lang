/** OPERON-LICENSE **/
package io.operon.runner.node;

import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Operator;
import io.operon.runner.statement.Statement;
import java.util.Map;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

public interface Node {
    // TODO:
    //   Add getId() and setId(Integer id)
    //   --> compiler should set the running id for each Node.
    //   --> JsonUtil.copy() should copy the id.
    
    public OperonValue evaluate() throws OperonGenericException;
    
    public String toString();
    public String toFormattedString(OutputFormatter pp);
    public String toYamlString(YamlFormatter yf);

    public void setEvaluatedValue(OperonValue value);
    
    public OperonValue getEvaluatedValue();

    // Compiler sets this flag (when value is EmptyType)
    public void setIsEmptyValue(boolean ie);
    public boolean isEmptyValue();
    
    public void setUnboxed(boolean ub);
    public boolean getUnboxed();
    public Node lock();
    public void setPreventReEvaluation(boolean pe);
    public boolean getPreventReEvaluation();
    
    // String: the operator-name, e.g. "="
    public Map<String, Operator> getBindings();
    
    public void setDoBindings(boolean doBind);
    public boolean getDoBindings();
    public Statement getStatement();
    public void setStatement(Statement s);
    
    public void setExpr(String expr);
    public String getExpr();
}
