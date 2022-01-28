/** OPERON-LICENSE **/
package io.operon.runner.statement;

import java.util.Map;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.Node;
import io.operon.runner.statement.LetStatement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.Path;
import io.operon.runner.ExceptionHandler;
import io.operon.runner.model.exception.OperonGenericException;

public interface Statement {

    public void setPreviousStatement(Statement stmt);
    
    public Statement getPreviousStatement();

    public OperonValue evaluate() throws OperonGenericException;
    
    public void setCurrentValue(OperonValue cv);
    
    public OperonValue getCurrentValue();
    
    public Path getCurrentPath();
    
    public void setCurrentPath(Path newPath);
    
    public Context getOperonContext();

    public Map<String, OperonValue> getRuntimeValues();
    
    public Map<String, LetStatement> getLetStatements();
    
    public Node getNode();
    
    public void setNode(Node node);

    public void setExceptionHandler(ExceptionHandler eh);
    
    public ExceptionHandler getExceptionHandler();

    public void setErrorHandled(boolean h);
    
    public boolean isErrorHandled();

    public void setId(String id);
    
    public String getId();
    
}