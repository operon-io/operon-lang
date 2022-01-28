/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.object;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.FunctionRegularArgument;
import io.operon.runner.node.type.*;
import io.operon.runner.OperonContext;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.processor.function.BaseArity2;
import io.operon.runner.processor.function.Arity2;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

public class ObjectRename extends BaseArity2 implements Node, Arity2 {
    
    public ObjectRename(Statement statement, List<Node> params) throws OperonGenericException {
        super(statement);
        this.setParams(params, "rename", "key", "to");
    }

    public ObjectType evaluate() throws OperonGenericException {        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        ObjectType obj = (ObjectType) currentValue.evaluate();
        OperonValue rnKeyValue = (OperonValue) this.getParam1().evaluate();
        
        if (rnKeyValue instanceof StringType) {
            StringType rnKey = (StringType) rnKeyValue;
            this.getStatement().setCurrentValue(rnKey);
            StringType rnTo = (StringType) this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            //
            // Must create a copy so that the following would work:
            //   $: {"src": 1, "trgt": 2} Select: {"bin": $ => rename("src", "trgt"), "bai": $}
            // otherwise "bai" would get the renamed object.
            //
            ObjectType objCopy = ((ObjectType) obj.copy());
            objCopy.renameKey(rnKey.getJavaStringValue(), rnTo.getJavaStringValue());
            return objCopy;
        }
        
        else if (rnKeyValue instanceof NumberType) {
            NumberType rnKey = (NumberType) rnKeyValue;
            int index = (int) (rnKey.getDoubleValue() - 1);
            String rnKeyStr = obj.getKeyByIndex(index);
            StringType rnKeyJsStr = new StringType(this.getStatement());
            rnKeyJsStr.setValue(rnKeyStr); // already double-quoted
            
            this.getStatement().setCurrentValue(rnKeyJsStr);
            StringType rnTo = (StringType) this.getParam2().evaluate();
            this.getStatement().setCurrentValue(currentValue);
            
            //
            // Must create a copy so that the following would work:
            //   $: {"src": 1, "trgt": 2} Select: {"bin": $ => rename("src", "trgt"), "bai": $}
            // otherwise "bai" would get the renamed object.
            //
            ObjectType objCopy = ((ObjectType) obj.copy());
            objCopy.renameByIndex(index, rnTo.getJavaStringValue());
            return objCopy;
        }
        else {
            ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "FUNCTION", "PARAM", "object:" + this.getFunctionName() + ": key type not valid");
            return null;
        }
    }

    public boolean ensuredCurrentValueSetBetweenParamEvaluations() {
        return true;
    }

}