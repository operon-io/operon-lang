/*
 *   Copyright 2022-2023, operon.io
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
 
import java.util.List; 
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.core.object.ObjectValue;
import io.operon.runner.node.Node; 
import io.operon.runner.node.AbstractNode;
import io.operon.runner.model.path.*;
import io.operon.runner.node.type.OperonValue; 
import io.operon.runner.node.type.EmptyType; 
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;
 
import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 

import io.operon.runner.IrTypes;
import com.google.gson.annotations.Expose;

public class ObjectType extends OperonValue implements Node { 
     // no logger  
    
    @Expose private byte t = IrTypes.OBJECT_TYPE; // Type-name in the IR-serialized output
    
    @Expose private List<PairType> pairs;
    
    //
    // This is for trying to optimize accessing the object.
    // If found inefficient, then should be removed!
    //
    private Map<String, PairType> indexedPairs;
    
    // Generated by the compiler.
    private int objId;
    
    public ObjectType(Statement stmnt) { 
        super(stmnt); 
        this.pairs = new ArrayList<PairType>(); 
    }
 
    public void addPair(PairType pair) throws OperonGenericException {
        if (pair.getKey() == null) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT", "ERROR", "Object addPair: key was null");
        }
        if (this.hasKey(pair.getKey())) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT", "ERROR", "duplicate field not allowed: " + pair.getKey());
        }
        //System.out.println("Adding pair. Is empty? " + pair.isEmptyValue());
        this.pairs.add(pair); 
    }

    //
    // This does _not_ check for duplicate key. Some built-in functions, when they build the result,
    // do not require duplicate-key check, so this is much faster.
    //
    public void safeAddPair(PairType pair) {
        this.pairs.add(pair); 
    }

    //
    // @returns true: when pair was addede, 
    //          false: when pair was updates or when 
    //                 key was not found
    //
    public boolean addOrUpdatePair(PairType addPair) {
        String key = addPair.getKey();
        int updateIndex = -1;
        for (int i = 0; i < this.getPairs().size(); i ++) {
            String compareKey = this.getPairs().get(i).getKey();
            if (compareKey.equals(key)) {
                updateIndex = i;
                break;
            }
        }
        if (updateIndex > -1) {
            // Update
            this.getPairs().set(updateIndex, addPair);
            return false;
        }
        else {
            // Add
            this.pairs.add(addPair);
            return true;
        }
    }

    //
    // @param key as non-quoted.
    // @Throws an error if key is not found.
    //
    public void updatePairByKey(String key, OperonValue newValue) throws OperonGenericException {
        int updateIndex = -1;
        for (int i = 0; i < this.getPairs().size(); i ++) {
            if (this.getPairs().get(i).getKey().equals("\"" + key + "\"")) {
                updateIndex = i;
                break;
            }
        }
        if (updateIndex > -1) {
            PairType newPair = new PairType(this.getStatement());
            newPair.setPair("\"" + key + "\"", newValue);
            this.getPairs().set(updateIndex, newPair);
        }
        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT", "ERROR", "update: field not found: " + key);
        }
    }
    
    //
    // @param key as non-quoted.
    //
    public void removePairByKey(String key) throws OperonGenericException {
        int removeIndex = -1;
        for (int i = 0; i < this.getPairs().size(); i ++) {
            if (this.getPairs().get(i).getKey().equals("\"" + key + "\"")) {
                removeIndex = i;
                break;
            }
        }
        if (removeIndex > -1) {
            this.getPairs().remove(removeIndex);
        }
        else {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT", "ERROR", "remove: field not found: " + key);
        }
    }
 
    // @param fromKey as non-quoted.
    // @param toKey as non-quoted.
    public void renameKey(String fromKey, String toKey) throws OperonGenericException {
        for (int i = 0; i < this.getPairs().size(); i ++) {
            if (this.getPairs().get(i).getKey().equals("\"" + fromKey + "\"")) {
                this.getPairs().get(i).setKey("\"" + toKey + "\"");
                return;
            }
        }
        ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT", "ERROR", "rename: field not found: " + fromKey);
    }

    // @param toKey as non-quoted.
    public void renameByIndex(int fromIndex, String toKey) throws OperonGenericException {
        if (fromIndex < 0) {
            fromIndex = this.getPairs().size() + fromIndex + 1;
        }
        if (fromIndex > this.getPairs().size()) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT", "ERROR", "rename: field not found: " + fromIndex);
        }
        this.getPairs().get(fromIndex).setKey("\"" + toKey + "\"");
    }

    // @param toKey as a list of non-quoted keys.
    public void renameKeyList(List<String> fromKey, List<String> toKey) throws OperonGenericException {
        for (int i = 0; i < this.getPairs().size(); i ++) {
            for (int li = 0; li < fromKey.size(); li ++) {
                if (this.getPairs().get(i).getKey().equals("\"" + fromKey.get(li) + "\"")) {
                    this.getPairs().get(i).setKey("\"" + toKey.get(li) + "\"");
                    break;
                }
            }
        }
    }

    //
    // This is used to "normalize" the input object, e.g. in period isd.
    // @param toKey as a list of non-quoted keys.
    // @fromCaseInsensitive: true --> converts fromKey to lowerCase
    //  
    public void renameKeyStartsWith(List<String> fromKeyStartsWith, List<String> toKey, boolean fromCaseInsensitive) throws OperonGenericException {
        for (int i = 0; i < this.getPairs().size(); i ++) {
            for (int li = 0; li < fromKeyStartsWith.size(); li ++) {
                if (fromCaseInsensitive) {
                    if (this.getPairs().get(i).getKey().toLowerCase().startsWith("\"" + fromKeyStartsWith.get(li))) {
                        this.getPairs().get(i).setKey("\"" + toKey.get(li) + "\"");
                        break;
                    }
                }
                else {
                    if (this.getPairs().get(i).getKey().startsWith("\"" + fromKeyStartsWith.get(li))) {
                        this.getPairs().get(i).setKey("\"" + toKey.get(li) + "\"");
                        break;
                    }
                }
            }
        }
    }

    public String getKeyByIndex(int index) throws OperonGenericException {
        if (index < 0) {
            index = this.getPairs().size() + index + 1;
        }
        if (index > this.getPairs().size()) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "OBJECT", "ERROR", "Field not found: " + index);
        }
        return this.getPairs().get(index).getKey();
    }

    //
    // Usage: "keyname"
    // I.e. no extra double-quotes.
    //
    // @param getKey as non-quoted.
    public OperonValue getByKey(String getKey) throws OperonGenericException {
        return ObjectValue.getValueByKey(this, getKey);
    }

    public OperonValue getByIndex(int index) throws OperonGenericException {
        return ObjectValue.getValueByIndex(this, index);
    }

    //
    // Usage: "\"keyname\""
    // NOTE: the extra double-quotes!
    // TODO: refactor this to add the quotes, i.e. do not require double-quotes.
    //       The quotes stem from the PairType, which records the key with the
    //       double-quotes.
    // @param jStrKey as quoted.
    //
    public boolean hasKey(String jStrKey) {
        List<PairType> pairs = this.getPairs();
        for (int i = 0; i < pairs.size(); i ++) {
            if (pairs.get(i).getKey().equals(jStrKey)) {
                return true;
            }
        }
        return false;
    }

    public ObjectType evaluate() throws OperonGenericException { 
        //:OFF:log.debug("ObjectType :: evaluate, stmt :: " + this.getStatement().getId()); 
        //
        // NOTE: do not cache result or use cached result
        //       because object may contain expressions / references
        //       that must be re-evaluated.
        //
        
        //System.out.println("Evaluate obj, currentPath: " + this.getStatement().getCurrentPath());
        Path objPath = this.getStatement().getCurrentPath();
        if (objPath.length() == 0) {
            objPath.setObjLink(this);
        }
        
        if (this.getUnboxed() == true && this.getPreventReEvaluation() == true) {
            return (ObjectType) this;
        }
        
        OperonValue currentValue = this.getStatement().getCurrentValue();
        
        if (currentValue == null) {
            // currentValue was null, so assign current-value with self-copy.
            currentValue = this;
        }
        
        //System.out.println(">> ObjectType 1 :: cv = " + currentValue);
        
        List<PairType> resultList = new ArrayList<PairType>();

        boolean preventReEval = true;

        for (int i = 0; i < this.getPairs().size(); i ++) {
            PairType pair = this.getPairs().get(i);
            //System.out.println(">> ObjectType 2 :: cv = " + currentValue);
            //
            // Use preventReEvaluation instead of just checking for AtomicOperonValue since this ensures that contraint has been checked.
            //
            if (pair.getPreventReEvaluation() == false) {
                // Set the currentPath
                Path pairPath = (Path) objPath.copy();
                String pairKey = pair.getKey().substring(1, pair.getKey().length() - 1);
                PathPart kpp = new KeyPathPart(pairKey);
                pairPath.getPathParts().add(kpp);
                pair.getStatement().setCurrentPath(pairPath);
                
                //
                // This copy() is required: StringPadLeftTests#padLeft4Test, StringPadRightTests#padRight3Test --> check why does not work (moved to BugTests).
                // NOTE: using copy() here is a massive memory eater.
                pair.getStatement().setCurrentValue(currentValue);
                pair.setParentObj(this);
                //pair.getStatement().getRuntimeValues().put("_", this);
                pair = (PairType) pair.evaluate();
                if (pair.getPreventReEvaluation() == false) {
                    preventReEval = false;
                }
            }

            if ( (pair.getValue() instanceof EmptyType) == false
                    && pair.isEmptyValue() == false) {
                resultList.add(pair);
            }
        }
        //
        // Restore current-path
        //
        this.getStatement().setCurrentPath(objPath);
        
        this.setUnboxed(true);
        this.setPreventReEvaluation(preventReEval); // if all pairs are atomic, then we want to prevent re-evaluation of the whole object.
        this.pairs = resultList;
        // Do not log here, causes re-evaluation 
        this.getStatement().setCurrentValue(this);
        return this;
    } 
    
    public ObjectType lock() {
        System.out.println("=== LOCKING OBJECT ===");
        this.setPreventReEvaluation(true);
        this.setUnboxed(true);
        return this;
    }
    
    public void setPreventReEvaluation(boolean pe) {
        //System.out.println("ObjectType :: preventReEval :: " + pe);
        this.preventReEvaluation = pe;
        for (int i = 0; i < this.getPairs().size(); i ++) {
            PairType pair = this.getPairs().get(i);
            pair.setPreventReEvaluation(pe);
        }
    }
    
    public void setPairs(List<PairType> p) {
        this.pairs = p;
    }
     
    public List<PairType> getPairs() { 
        return this.pairs; 
    }
    
    public void setIndexedPairs(Map<String, PairType> ip) {
        this.indexedPairs = ip;
    }
    
    public Map<String, PairType> getIndexedPairs() {
        return this.indexedPairs;
    }
    
    //
    // This is for trying to optimize accessing the object.
    // 
    public void initializeIndexedPairs() {
        System.out.println("Initializing index");
        this.indexedPairs = new HashMap<String, PairType>();
        /*for (int i = 0; i < this.getPairs().size(); i ++) {
            PairType pt = this.getPairs().get(i);
            this.indexedPairs.put(pt.getKey(), pt);
        }*/
        //System.out.println("Index: " + this.indexedPairs);
    }
    
    public void setObjId(int oid) {
        this.objId = oid;
    }
    
    public int getObjId() {
        return this.objId;
    }
    
    @Override
    public String toString() { 
        //:OFF:log.debug("ObjectType :: [" + this.getObjId() + "] toString()"); 
        //System.out.println("ObjectType :: [" + this.getObjId() + "] toString()");
        StringBuilder sb = new StringBuilder(); 
         
        sb.append("{"); 
        
        int i = 0;
        for (i = 0; i < this.getPairs().size(); i ++) { 
            PairType pair = this.getPairs().get(i);
            String pairValueStr = pair.toString();
            // Check if EmptyValue 
            if (pairValueStr.isEmpty()) { 
                continue;
            }
            sb.append(pairValueStr); 
            
            //System.out.println("pairValueStr [" + i + "]: >>" + pairValueStr + "<<");
            
            if (i < this.getPairs().size() - 1) {
                sb.append(", ");
            }
        }
        
        //
        // Remove the last comma, which could be there if 
        // the last field is hidden
        //
        if (sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        String result = sb.toString();
        sb = null;
        return result; 
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        //:OFF:log.debug("ObjectType :: [" + this.getObjId() + "] toString()"); 
        if (ofmt == null) {ofmt = new OutputFormatter();}
        //System.out.println("ObjectType convert to String");
        StringBuilder sb = new StringBuilder(); 
         
        sb.append(ofmt.objectStart + System.lineSeparator());
        ofmt.spaces = (short) (ofmt.spaces + ofmt.spacing);
        
        int i = 0;
        for (i = 0; i < this.getPairs().size(); i ++) {
            PairType pair = this.getPairs().get(i);
            String pairValueStr =  ofmt.spaces() + pair.toFormattedString(ofmt);
            // Check if EmptyValue 
            if (pairValueStr.isEmpty()) { 
                continue;
            }
            sb.append(pairValueStr); 
            
            //System.out.println("pairValueStr [" + i + "]: >>" + pairValueStr + "<<");
            
            if (i < this.getPairs().size() - 1) { 
                sb.append("," + System.lineSeparator()); 
            }
        }
        
        ofmt.spaces = (short) (ofmt.spaces - ofmt.spacing); // reset spacing
        sb.append(System.lineSeparator() + ofmt.spaces() + ofmt.objectEnd);
        String result = sb.toString();
        sb = null;
        return result; 
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        //:OFF:log.debug("ObjectType :: [" + this.getObjId() + "] toYamlString()"); 
        if (yf == null) {
            //System.out.println("ObjectType :: toYamlString :: yf was null, creating new YamlFormatter");
            yf = new YamlFormatter();
        }
        //System.out.println("ObjectType convert to String");
        StringBuilder sb = new StringBuilder(); 
        
        if (this.getPairs().size() == 0) {
            sb.append("{}");
        }
        
        int i = 0;
        String pairValueStr = null;
        for (i = 0; i < this.getPairs().size(); i ++) {
            //System.out.println("toYamlString :: OUTER OBJ :: i=" + i + ", objId=" + this.getObjId());
            PairType pair = this.getPairs().get(i);
            pairValueStr = pair.toYamlString(yf); /*yf.spaces() +*/
            //System.out.println("toYamlString :: OUTER OBJ PAIR CALL DONE");
            // Check if EmptyValue 
            if (pairValueStr.isEmpty()) { 
                continue;
            }
            sb.append(pairValueStr); 

            if (i < this.getPairs().size() - 1) {
                sb.append(System.lineSeparator()); 
            }
        }
        //System.out.println("toYamlString :: OUTER OBJ DONE");
        String result = sb.toString();
        //sb = null;
        return result; 
    }

    @Override
    public String toTomlString(OutputFormatter ofmt) {
        //:OFF:log.debug("ObjectType :: [" + this.getObjId() + "] toString()"); 
        if (ofmt == null) {ofmt = new OutputFormatter();}
        //System.out.println("ObjectType convert to String");
        StringBuilder sb = new StringBuilder(); 
         
        //sb.append(ofmt.objectStart + System.lineSeparator());
        //
        // INC spacing
        //
        //ofmt.spaces = (short) (ofmt.spaces + ofmt.spacing);
        
        int i = 0;
        for (i = 0; i < this.getPairs().size(); i ++) {
            PairType pair = this.getPairs().get(i);
            String pairValueStr =  ofmt.spaces() + pair.toTomlString(ofmt);
            // Check if EmptyValue 
            if (pairValueStr.isEmpty()) { 
                continue;
            }
            sb.append(pairValueStr); 
            
            //System.out.println("pairValueStr [" + i + "]: >>" + pairValueStr + "<<");
            if (i < this.getPairs().size() - 1) {
                sb.append(System.lineSeparator());
            }
        }
        //
        // DEC spacing
        //
        //ofmt.spaces = (short) (ofmt.spaces - ofmt.spacing); // reset spacing
        //sb.append(System.lineSeparator() + ofmt.spaces() + ofmt.objectEnd);
        sb.append(System.lineSeparator());
        String result = sb.toString();
        sb = null;
        return result; 
    }

}