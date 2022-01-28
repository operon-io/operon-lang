/** OPERON-LICENSE **/
package io.operon.runner.node;

import java.util.List;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ArrayType;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class ObjDeepScan extends AbstractNode implements Node {
    private static Logger log = LogManager.getLogger(ObjDeepScan.class);
    private String objDeepScanKey;
    
    public ObjDeepScan(Statement stmnt) {
        super(stmnt);
    }

    public void setObjDeepScanKey(String key) {
        this.objDeepScanKey = key;
    }

    public OperonValue evaluate() throws OperonGenericException {
        log.debug("ENTER ObjDeepScan.evaluate(). Stmt: " + this.getStatement().getId());
        //System.out.println("ObjDeepScan :: evaluate");
        // get currentValue from the statement
        OperonValue currentValue = this.getStatement().getCurrentValue();
        //System.out.println("ObjDeepScan :: evaluate :: cv :: " + currentValue);
        log.debug("  >> ObjDeepScan :: 1 :: " + currentValue.getClass().getName());
        
        //log.debug("    >> @: " + value.toString()); // Do not log here, causes re-evaluation

        return evaluateSelector(currentValue);
    }
    
    private OperonValue evaluateSelector(OperonValue value) throws OperonGenericException {
        log.debug("  >> ObjDeepScan :: evaluate selector");

        if (value instanceof ObjectType == false && value instanceof ArrayType == false) {
            value = value.evaluate();
        }

        if (value instanceof ObjectType) {
            log.debug("EXIT ObjDeepScan.evaluate() obj");
            return evaluateObj( (ObjectType) value );
        }
        
        else if (value instanceof ArrayType) {
            log.debug("EXIT ObjDeepScan.evaluate() array");
            return evaluateArray( (ArrayType) value );
        }
        
        log.debug("ObjDeepScan: cannot scan object. Wrong type: " + value.getClass().getName());
        return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT_DEEP_SCAN", "TYPE", "Cannot scan object. Wrong type.");
    }
    
    private ArrayType evaluateObj(ObjectType obj) throws OperonGenericException {
        ArrayType result = new ArrayType(this.getStatement());
        
        log.debug("    Scan key: " + this.getObjDeepScanKey());
        
        for (PairType pair : obj.getPairs()) {
            log.debug("    Obj key :: " + pair.getKey());
            if (pair.getKey().equals("\"" + this.getObjDeepScanKey() + "\"")) {
                result.addValue(pair.getEvaluatedValue());
            }
            
            OperonValue subObj = pair.getEvaluatedValue();
            
            // Inject parent ObjectType-scopes for the ObjectType
            if (subObj instanceof LambdaFunctionRef) {
                LambdaFunctionRef lfr = (LambdaFunctionRef) subObj;
                lfr.getStatement().getRuntimeValues().put("_", obj);
            }
            
            else {
                //System.out.println("ObjDeepScan :: Linking parentObj :: " + obj + " --> to subObj :: " + subObj);

            }
            
            subObj = subObj.evaluate();
            
            // Recursive handling
            ArrayType subResult = null;
            
            if ((subObj instanceof ObjectType) || (subObj instanceof ArrayType)) {
                subResult = (ArrayType) evaluateSelector(subObj);
                if (subResult.getValues().size() > 0) {
                    // Recursive call creates an array, which must be flattened:
                    for (Node n : subResult.getValues()) {
                        result.addValue(n);
                    }
                }
            }
        }
        
        // update the currentValue from the statement
        this.getStatement().setCurrentValue(result);
        
        this.setEvaluatedValue(result);
        return result;
    }
    
    private ArrayType evaluateArray(ArrayType array) throws OperonGenericException {
        log.debug("Accessing array of objects: " + this.getObjDeepScanKey());
        
        ArrayType resultArray = new ArrayType(this.getStatement());
        List<Node> arrayValues = array.getValues();
        
        for (int i = 0; i < arrayValues.size(); i ++) {
            Node arrayNode = arrayValues.get(i);
            log.debug("    >> Looping: " + i);
            if (arrayNode.evaluate() instanceof ObjectType) {
                ArrayType arrayNodeResult = (ArrayType) evaluateObj((ObjectType) arrayNode.evaluate());
                if (arrayNodeResult.getValues().size() > 0) {
                    // Recursive call creates an array, which must be flattened:
                    for (Node n : arrayNodeResult.getValues()) {
                        resultArray.addValue(n);
                    }
                }
            }
        }
        
        // update the currentValue from the statement
        this.getStatement().setCurrentValue(resultArray);
        
        this.setEvaluatedValue(resultArray );
        return resultArray;
    }
    
    public String getObjDeepScanKey() {
        return this.objDeepScanKey;
    }
    
    public String toString() {
        return this.getEvaluatedValue().toString();
    }
}