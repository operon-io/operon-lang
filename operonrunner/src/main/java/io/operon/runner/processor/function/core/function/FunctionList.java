/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.function;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import io.operon.runner.OperonContext;
import io.operon.runner.Context;
import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.node.type.StringType;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.FunctionStatement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 *
 * List available functions from query-main context.
 *
 */
public class FunctionList extends BaseArity0 implements Arity0 {
    
    public FunctionList(Statement statement) {
        super(statement);
        this.setFunctionName("list");
    }

    @SuppressWarnings("unchecked")
    public ArrayType evaluate() throws OperonGenericException {
        Context ctx = this.getStatement().getOperonContext();

        ArrayType result = new ArrayType(this.getStatement());
        
        List<StringType> functionFQNames = this.getFunctionFQNamesFromFunctionStatementsMap(ctx.getFunctionStatements());
        Collections.sort(functionFQNames);
        result.getValues().addAll(functionFQNames);
        
        if (ctx.getModules().isEmpty() == false) {
            List<StringType> functionFQNamesFromModules = this.extractFromModule(ctx.getModules());
            result.getValues().addAll(functionFQNamesFromModules);
        }
        
        return result;
    }

    private List<StringType> extractFromModule(Map<String, Context> moduleMap) {
        List<StringType> results = new ArrayList<StringType>();
        for (Map.Entry<String, Context> moduleEntry : moduleMap.entrySet()) {
            Context module = moduleEntry.getValue();
            // prefix with module's own namespace:
            String moduleOwnNamespace = module.getOwnNamespace();
            if (moduleOwnNamespace.charAt(0) == ':') {
                moduleOwnNamespace = moduleOwnNamespace.substring(1, moduleOwnNamespace.length());
            }
            List<StringType> moduleResults = this.getFunctionFQNamesFromFunctionStatementsMap(module.getFunctionStatements());
            for (StringType jstr : moduleResults) {
                if (jstr.getJavaStringValue().charAt(0) == ':') {
                    jstr.setFromJavaString(moduleOwnNamespace + jstr.getJavaStringValue());
                }
                else {
                    jstr.setFromJavaString(moduleOwnNamespace + ":" + jstr.getJavaStringValue());
                }
            }
            results.addAll(moduleResults);
        }
        return results;
    }

    private List<StringType> getFunctionFQNamesFromFunctionStatementsMap(Map<String, FunctionStatement> functionStatementsMap) {
        List<StringType> result = new ArrayList<StringType>();
        for (Map.Entry<String, FunctionStatement> functionEntry : functionStatementsMap.entrySet()) {
        	String functionFQName = functionEntry.getKey();
        	StringType functionKey = new StringType(this.getStatement());
        	functionKey.setFromJavaString(functionFQName);
        	result.add(functionKey);
        }
        return result;
    }
}