/*
 *   Copyright 2022, operon.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.operon.runner.node.type; 
 
import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class PairType extends OperonValue implements Node { 
    private static Logger log = LogManager.getLogger(PairType.class); 
    private String key; 
    private OperonValue value; 
    private OperonValue evaluatedValue; 
    private OperonValueConstraint constraint; 
    private int position; // Required in Filter
    
    private ObjectType parentObj;
    
    public PairType(Statement stmnt) { 
        super(stmnt); 
    } 

    //
    // TODO: this requires the double-quotes. Refactor so that this is not required anymore!
    //      --> this should add the double-quotes itself.
    public void setPair(String key, OperonValue value ) { 
        this.key = key;
        this.value = value;
        this.evaluatedValue = value; // set evaluated value already before evaluating, avoids null-pointer from toString(), which is called before evaluate()
        if (value.isEmptyValue()) {
            this.setIsEmptyValue(true);
        }
    }
 
    public PairType evaluate() throws OperonGenericException { 
        log.debug("PairType :: " + key + ", evaluate :: " + this.getValue().getClass().getName());
        
        if (this.getUnboxed() == true && this.getPreventReEvaluation() == true) {
            return this;
        }
        
        //System.out.println("PairType.evaluate(), key=" + this.getKey());
        //System.out.println(":: parent :: " + this.getParentObj().getClass().getName());
        
        //
        // Set '_' (surrounding object, i.e. "this / self")
        //
        if (this.getParentObj() != null && this.getParentObj() instanceof ObjectType) {
            //System.out.println("PairType evaluate --> @=Object :: setting _ and _$");
            ObjectType obj = (ObjectType) this.getParentObj();
            ObjectType objSurround = new ObjectType(this.getStatement());
            
            for (int i = 0; i < obj.getPairs().size(); i ++) {
                PairType p = obj.getPairs().get(i);
                if (p.getKey().equals(this.getKey())) {
                    //System.out.println("Found a matching key, omitting it: " + this.getKey());
                }
                else {
                    objSurround.addPair(p);
                }
            }
            
            //System.out.println("Filled surroundObj");
            //System.out.println("  _ = " + obj);
            
            this.getStatement().getRuntimeValues().put("_", objSurround); 
        }
        
        OperonValue evaluatedValue = this.getValue().evaluate(); 
        this.setEvaluatedValue(evaluatedValue); 
         
        if (this.getOperonValueConstraint() != null) { 
            log.debug("Evaluating constraint. Statement id :: " + this.getStatement().getId()); 
            // Apply constraint-check: 
            OperonValueConstraint c = this.getOperonValueConstraint(); 
             
            // Put the same constraint also into OperonValue: 
            this.getEvaluatedValue().setOperonValueConstraint(c);
            c.setValueToEvaluateAgainst(evaluatedValue); 
            OperonValue constraintResult = c.evaluate(); 
            if (constraintResult instanceof FalseType) {
                ErrorUtil.createErrorValueAndThrow(this.getStatement(), "CONSTRAINT", "VIOLATION", "Field: " + key.substring(1, key.length() - 1) + " violates constraint " + c.getConstraintAsString());
            } 
        } 
        this.setUnboxed(true);
        if (this.getValue() instanceof AtomicOperonValue) {
            this.setPreventReEvaluation(true);
        }
        return this; 
    } 

    // Assumes double-quotes
    public void setKey(String newKey) {
        this.key = newKey;
    }

    // Returns key with double-quotes
    public String getKey() { 
        return this.key; 
    }

    public OperonValue getValue() { 
        return this.value;
    } 
 
    public void setEvaluatedValue(OperonValue ev) { 
        this.evaluatedValue = ev; 
    } 
     
    public OperonValue getEvaluatedValue() { 
        return this.evaluatedValue; 
    } 
 
    public void setOperonValueConstraint(OperonValueConstraint c) { 
        this.constraint = c; 
    } 
 
    public OperonValueConstraint getOperonValueConstraint() { 
        return this.constraint; 
    } 


    //
    // REMOVE
    //
    public void setPosition(int pos) {
        this.position = pos;
    }
    
    public int getPosition() {
        return this.position;
    }
    //
    // END REMOVE
    //
    
    public void setParentObj(ObjectType parent) {
        this.parentObj = parent;
    }
    
    public ObjectType getParentObj() {
        return this.parentObj;
    }

    @Override
    public String toString() {
        if (this.isEmptyValue()) {
            return ""; 
        } 
        
        else {
            OperonValue evaluatedValue = this.getEvaluatedValue();
            String strValue = evaluatedValue.toString();
            if (strValue.isEmpty()) {
                return "";
            }
            else {
                return this.getKey() + ": " + strValue;
            }
        } 
    }
    
    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        if (this.isEmptyValue()) {
            return ""; 
        } 
        
        else {
            String strValue = this.getEvaluatedValue().toFormattedString(ofmt);
            if (strValue.isEmpty()) {
                return "";
            }
            else {
                return this.getKey() + ": " + strValue;
            }
        } 
    }
    
    @Override
    public String toYamlString(YamlFormatter yf) {
        if (yf == null) {yf = new YamlFormatter();}
        if (this.isEmptyValue()) {
            return ""; 
        } 
        
        else {
            String strValue = null;
            OperonValue ev = null;
            try {
                ev = this.getEvaluatedValue().evaluate();
            } catch (OperonGenericException oge) {
                return "Error()";
            }
            //System.out.println("ev = " + ev);
            //System.out.println("  yf.spaces = " + yf.spaces);
            if (ev instanceof ArrayType) {
                yf.spaces = (short) (yf.spaces + yf.spacing);
                //System.out.println("  - inc yf.spaces = " + yf.spaces);
                strValue = ev.toYamlString(yf);
                yf.spaces = (short) (yf.spaces - yf.spacing); // reset spacing
                //System.out.println("  - dec yf.spaces = " + yf.spaces);
            }
            if (ev instanceof ObjectType) {
                yf.spaces = (short) (yf.spaces + yf.spacing);
                strValue = ev.toYamlString(yf);
                yf.spaces = (short) (yf.spaces - yf.spacing); // reset spacing
            }
            else {
                strValue = ev.toYamlString(yf);
            }
            
            if (strValue.isEmpty()) {
                return "";
            }
            else {
                if (ev instanceof ArrayType) {
                    String result = this.getKey().substring(1, this.getKey().length() - 1) + ":" + System.lineSeparator() + strValue;
                    return result;
                }
                else if (ev instanceof ObjectType) {
                    String result = this.getKey().substring(1, this.getKey().length() - 1) + ":" + System.lineSeparator() + strValue;
                    return result;
                }
                else {
                    return this.getKey().substring(1, this.getKey().length() - 1) + ": " + strValue;
                }
            }
        } 
    }
}