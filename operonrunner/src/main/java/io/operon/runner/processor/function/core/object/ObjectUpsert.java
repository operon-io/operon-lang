/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Insert or update.
//
public class ObjectUpsert extends BaseArity2 implements Node, Arity2 {
    
    public ObjectUpsert(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParam2AsOptional(true);
        this.setParams(params, "upsert", "value", "target");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        try {
            //System.out.println("object:upsert");
            OperonValue currentValue = this.getStatement().getCurrentValue();
            ObjectType obj = (ObjectType) currentValue.evaluate();
            
            ObjectType result = null;
            
            if (this.getParam1() != null && this.getParam2() != null) {
                OperonValue updateKeyValue = this.getParam2().evaluate();
                this.getStatement().setCurrentValue(obj);
                Node updateValueNode = this.getParam1();
                
                //System.out.println("object:upsert :: updateKeyValue :: " + );
                
                ObjectType objCopy = (ObjectType) obj.copy();
                
                if (this.getParam1() != null && this.getParam2() != null && updateKeyValue instanceof StringType) {
                    StringType updateKeyJson = (StringType) updateKeyValue;
                    String updateKeyStr = updateKeyJson.getJavaStringValue();
                    result = ObjectUpsert.doUpsertByKey(objCopy, updateKeyStr, updateValueNode);
                }
                
                // NumberType is not supported in upsert.
            }

            else {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), "Missing parameter.");
            }
            
            return result;
        } catch (OperonGenericException e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "object:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

    public static ObjectType doUpsertByKey(ObjectType obj, String updateKey, Node updateValueNode) throws OperonGenericException {
        boolean hasKey = obj.hasKey("\"" + updateKey + "\"");
        if (hasKey == false) {
            // insert the key:
            
            // check that value does not evaluate into empty
            // (if it does, then no insert is done):
            OperonValue updateValueJson = updateValueNode.evaluate();
            
            if (updateValueJson instanceof EmptyType == false) {
                PairType newPair = new PairType(obj.getStatement());
                newPair.setPair("\"" + updateKey + "\"", updateValueJson);
                obj.addPair(newPair);
            }
            return obj;
        }
        else {
            // inject with currentValue
            OperonValue injectValue = obj.getByKey(updateKey);
            
            // inject value in object
            ObjectType injectObj = new ObjectType(obj.getStatement());
            PairType injectPair = new PairType(obj.getStatement());
            injectPair.setPair("\"" + updateKey + "\"", injectValue);
            injectObj.addPair(injectPair);
            
            updateValueNode.getStatement().setCurrentValue(injectObj);
            OperonValue updateValueJson = updateValueNode.evaluate();
            
            if (updateValueJson instanceof EmptyType == false) {
                obj.updatePairByKey(updateKey, updateValueJson);
            }
            else {
                obj.removePairByKey(updateKey);
            }
            return obj;
        }
    }

}