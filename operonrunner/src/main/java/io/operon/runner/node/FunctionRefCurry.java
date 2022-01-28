/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.IOException;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class FunctionRefCurry extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(FunctionRefCurry.class);

    private List<Node> args;
    
    public FunctionRefCurry(Statement stmnt) {
        super(stmnt);
        this.args = new ArrayList<Node>();
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("FunctionRefCurry :: evaluate");
        //System.out.println("CURRY: args: " + this.getArguments());
        OperonValue currentValue = this.getStatement().getCurrentValue();
        
        if ( currentValue instanceof FunctionRef ) {
            log.debug("FunctionRefCurry :: currentValue :: Function ref detected!");
            // Add the args for the functionRef
            FunctionRef fref = (FunctionRef) currentValue;
            for (Node arg : this.getArguments()) {
                if (arg instanceof FunctionRegularArgument) {
                    log.debug("DEBUG :: FunctionRegularArgument Detected.");
                    FunctionRegularArgument regArg = (FunctionRegularArgument) arg;
                    Node argNode = regArg.getArgument();

                    log.debug("DEBUG :: " + argNode.getClass().getName());
                    
                    while (argNode instanceof OperonValue == false) {
                        argNode = argNode.evaluate();
                    }
                    
                    fref.setArgument(argNode);
                }
            }
            return fref;
        }
        
        else if ( currentValue instanceof LambdaFunctionRef ) {
            log.debug("FunctionRefCurry :: currentValue :: LambdaFunctionRef detected!");
            //currentValueCopy = currentValue/*.copy()*/;
            LambdaFunctionRef lfref = (LambdaFunctionRef) currentValue;
            FunctionArguments fArgs = new FunctionArguments(this.getStatement());
            //System.out.println("CURRY >> 1: args size=" + this.getArguments().size());
            //
            // TODO: Check why compiler sees both: FunctionNamedArgument and FunctionRefNamedArgument?
            //
            for (Node arg : this.getArguments()) {
                if (arg instanceof FunctionRegularArgument) {
                    log.debug("DEBUG :: FunctionRegularArgument Detected.");
                    FunctionRegularArgument regArg = (FunctionRegularArgument) arg;
                    fArgs.getArguments().add(regArg);
                }
                else if (arg instanceof FunctionNamedArgument) {
                    log.debug("DEBUG :: FunctionNamedArgument Detected.");
                    FunctionNamedArgument namedArg = (FunctionNamedArgument) arg;
                    //System.out.println("Curry: add namedArg: " + namedArg.getArgumentName());
                    fArgs.getArguments().add(namedArg);
                }
                else if (arg instanceof FunctionRefNamedArgument) {
                    log.debug("DEBUG :: FunctionRefNamedArgument Detected.");
                    FunctionRefNamedArgument namedArg = (FunctionRefNamedArgument) arg;
                    //System.out.println("Curry: add namedRefArg: " + namedArg.getArgumentName());
                    fArgs.getArguments().add(namedArg);
                }
                else {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_REF_CURRY", "ERROR", "CANNOT RECOGNIZE ARG: " + arg.getClass().getName());
                }
            }
            //System.out.println("CURRY >> 2, fArgs.size()=" + fArgs.getArguments().size());
            try {
                LambdaFunctionRef.setParams(lfref, currentValue, fArgs);
            } catch (IOException | ClassNotFoundException e) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION_REF_CURRY", "ERROR", e.getMessage());
            }

            return lfref;
        }
        
        else {
            log.debug("FunctionRefCurry :: currentValue type :: " + currentValue.getClass().getName());
        }
        return currentValue;
    }

    public List<Node> getArguments() {
        return this.args;
    }

}