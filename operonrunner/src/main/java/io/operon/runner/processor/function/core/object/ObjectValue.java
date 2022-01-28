/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.ObjAccess;
import io.operon.runner.node.type.*;
import io.operon.runner.node.LambdaFunctionRef;
import io.operon.runner.node.FunctionRef;
import io.operon.runner.model.path.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.processor.function.BaseArity1;
import io.operon.runner.processor.function.Arity1;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectValue extends BaseArity1 implements Node, Arity1 {
    
    public ObjectValue(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam1AsOptional(true);
        this.setParams(params, "value", "key");
    }

    public OperonValue evaluate() throws OperonGenericException {        
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType obj = (ObjectType) currentValue.evaluate();
            OperonValue result = ObjectValue.doGetValue(obj, this.getParam1());
            return result;
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
        }
    }

    public static OperonValue doGetValue(ObjectType obj, Node param1) throws OperonGenericException {
        OperonValue result = null;
        if (param1 == null) {
            List<PairType> pairs = obj.getPairs();
            if (pairs.size() == 0) {
                return ErrorUtil.createErrorValueAndThrow(obj.getStatement(), "FUNCTION", "object:value", "object was empty");
            }
            result = pairs.get(0).getValue();
            //setCurrentPathWithPos(result.getStatement(), 1, obj);
        }
        else {
            OperonValue getKeyValue = param1.evaluate();
            if (getKeyValue instanceof StringType) {
                String getKey = ((StringType) getKeyValue).getJavaStringValue();
                result = ObjectValue.getValueByKey(obj, getKey);
            }
            else if (getKeyValue instanceof NumberType) {
                int index = (int) ((NumberType) getKeyValue).getDoubleValue();
                result = ObjectValue.getValueByIndex(obj, index);
            }
            else {
                return ErrorUtil.createErrorValueAndThrow(obj.getStatement(), "FUNCTION", "object:value", "get: key-type not supported: " + getKeyValue);
            }
        }

        return result;
    }

    //
    // Throws an error if key was not found.
    //
    public static OperonValue getValueByKey(ObjectType obj, String key) throws OperonGenericException {
        OperonValue result = null;
        if (key == null) {
            List<PairType> pairs = obj.getPairs();
            if (pairs.size() == 0) {
                return ErrorUtil.createErrorValueAndThrow(obj.getStatement(), "FUNCTION", "object:value", "object was empty");
            }
            result = pairs.get(0).getValue();
            //setCurrentPathWithKey(result.getStatement(), key, obj);
        }
        else {
            boolean found = false;
            List<PairType> pairs = obj.getPairs();
            for (int i = 0; i < pairs.size(); i ++) {
                if (pairs.get(i).getKey().equals("\"" + key + "\"")) {
                    result = pairs.get(i).getValue();
                    found = true;
                    result = result.evaluate();
                    //setCurrentPathWithKey(result.getStatement(), key, obj);
                    if (result instanceof LambdaFunctionRef) {
                        result = handleLambdaFunctionRef(obj, (LambdaFunctionRef) result);
                    }
                    break;
                }
            }

            if (!found) {
                String missingField = key.toString().replaceAll("\"", "");
                ErrorUtil.createErrorValueAndThrow(obj.getStatement(), "FUNCTION", "object:value", "get: field key not found: " + missingField);
            }
        }

        return result;
    }

    public static OperonValue getValueByIndex(ObjectType obj, int index) throws OperonGenericException {
        OperonValue result = null;
        if (index == 0) {
            List<PairType> pairs = obj.getPairs();
            if (pairs.size() == 0) {
                return ErrorUtil.createErrorValueAndThrow(obj.getStatement(), "FUNCTION", "object:value", "object was empty");
            }
            result = pairs.get(0).getValue();
            //setCurrentPathWithPos(result.getStatement(), 1, obj);
        }
        else {
            boolean found = false;

            if (index < 0) {
                index = obj.getPairs().size() + index + 1;
            }
            if (index > obj.getPairs().size()) {
                found = false;
            }
            else {
                PairType pair = obj.getPairs().get(index - 1);
                if (pair == null) {
                    return ErrorUtil.createErrorValueAndThrow(obj.getStatement(), "FUNCTION", "object:value", "value not found for index " + index);
                }
                result = pair.getValue();
                result = result.evaluate();
                //setCurrentPathWithPos(result.getStatement(), index, obj);
                found = true;
                if (result instanceof LambdaFunctionRef) {
                    result = handleLambdaFunctionRef(obj, (LambdaFunctionRef) result);
                }
            }

            if (!found) {
                return ErrorUtil.createErrorValueAndThrow(obj.getStatement(), "FUNCTION", "object:value", "get: field index not found: " + index);
            }
        }

        return result;
    }

    private static OperonValue handleLambdaFunctionRef(ObjectType obj, LambdaFunctionRef lfr) throws OperonGenericException {
        lfr.getStatement().getRuntimeValues().put("_", obj); 
        if (lfr.isInvokeOnAccess()) {
            return lfr.invoke();
        }
        return lfr;
    }
/*
    private static void setCurrentPathWithPos(Statement stmt, int pos, OperonValue objLink) {
        //
        // ATTRIBUTES
        //
        Path currentPath = (Path) stmt.getCurrentPath();
        PathPart pp = new PosPathPart(pos);
        currentPath.getPathParts().add(pp);
        if (currentPath.getObjLink() == null) {
            currentPath.setObjLink(objLink);
        }
        stmt.setCurrentPath(currentPath);
    }

    private static void setCurrentPathWithKey(Statement stmt, String key, OperonValue objLink) {
        //
        // ATTRIBUTES
        //
        Path currentPath = (Path) stmt.getCurrentPath();
        PathPart kpp = new KeyPathPart(key);
        currentPath.getPathParts().add(kpp);
        if (currentPath.getObjLink() == null) {
            currentPath.setObjLink(objLink);
        }
        stmt.setCurrentPath(currentPath);
    }
*/

}