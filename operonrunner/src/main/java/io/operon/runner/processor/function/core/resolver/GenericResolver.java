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

package io.operon.runner.processor.function.core.resolver;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.type.*;

import io.operon.runner.processor.function.core.GenericParent;
import io.operon.runner.processor.function.core.GenericRoot;
import io.operon.runner.processor.function.core.GenericNext;
import io.operon.runner.processor.function.core.GenericPrevious;
import io.operon.runner.processor.function.core.array.ArrayCount;
import io.operon.runner.processor.function.core.array.ArrayGet;
import io.operon.runner.processor.function.core.array.ArraySearch;
import io.operon.runner.processor.function.core.array.ArrayRemove;
import io.operon.runner.processor.function.core.array.ArrayUpdate;
import io.operon.runner.processor.function.core.object.ObjectCount;
import io.operon.runner.processor.function.core.object.ObjectRemove;
import io.operon.runner.processor.function.core.object.ObjectUpdate;
import io.operon.runner.processor.function.core.object.ObjectValue;
import io.operon.runner.processor.function.core.string.StringSearch;
import io.operon.runner.processor.function.core.string.StringLength;
import io.operon.runner.processor.function.core.string.StringToBase64;
import io.operon.runner.processor.function.core.string.StringOrganize;
import io.operon.runner.processor.function.core.string.StringCollect;
import io.operon.runner.processor.function.core.path.PathLength;
import io.operon.runner.processor.function.core.path.PathValue;
import io.operon.runner.processor.function.core.raw.RawToBase64;
import io.operon.runner.processor.function.core.raw.SRawOrganize;
import io.operon.runner.processor.function.core.raw.SRawCollect;

import io.operon.runner.processor.function.core.SpliceLeft;
import io.operon.runner.processor.function.core.SpliceRight;
import io.operon.runner.processor.function.core.SpliceRange;

import io.operon.runner.statement.Statement;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

/**
 * Resolvers are used to resolve the correct function, when function call
 * refers to ambiguos function.
 * 
 * Example: [1,2,3] =&gt; Count() # This could refer to object:Count() or array:Count()
 *
 */
public class GenericResolver extends AbstractNode implements Node {
    
    private Node param1;
    private String coreFunctionName;
    private List<Node> params;
    
    public GenericResolver(Statement statement, String coreFuncName) {
        super(statement);
        this.setCoreFunctionName(coreFuncName);
    }

    public GenericResolver(Statement statement, String coreFuncName, List<Node> params) {
        super(statement);
        this.setCoreFunctionName(coreFuncName);
        this.setParams(params);
    }

    public OperonValue evaluate() throws OperonGenericException {
        OperonValue result = null;
        try {
            OperonValue currentValue = this.getStatement().getCurrentValue();
            
            if (this.getCoreFunctionName().equals("count")) {return this.resolveCount0(currentValue);}
            else if (this.getCoreFunctionName().equals("length")) {return this.resolveLength0(currentValue);}
            else if (this.getCoreFunctionName().equals("parent")) {return this.resolveParent0(currentValue);}
            else if (this.getCoreFunctionName().equals("root")) {return this.resolveRoot0(currentValue);}
            else if (this.getCoreFunctionName().equals("next")) {return this.resolveNext0(currentValue);}
            else if (this.getCoreFunctionName().equals("previous")) {return this.resolvePrevious0(currentValue);}
            else if (this.getCoreFunctionName().equals("spliceLeft")) {return this.resolveSpliceLeft1(currentValue);}
            else if (this.getCoreFunctionName().equals("spliceRight")) {return this.resolveSpliceRight1(currentValue);}
            else if (this.getCoreFunctionName().equals("spliceRange")) {return this.resolveSpliceRange2(currentValue);}
            else if (this.getCoreFunctionName().equals("search")) {return this.resolveSearch1(currentValue);}
            else if (this.getCoreFunctionName().equals("toBase64")) {return this.resolveToBase64_1(currentValue);}
            else if (this.getCoreFunctionName().equals("remove")) {return this.resolveRemove1(currentValue);}
            else if (this.getCoreFunctionName().equals("value")) {return this.resolveValue0(currentValue);}
            else if (this.getCoreFunctionName().equals("organize")) {return this.resolveOrganize1(currentValue);}
            else if (this.getCoreFunctionName().equals("collect")) {return this.resolveCollect1(currentValue);}
        } catch (Exception e) {
            return ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "RESOLVER", e.getMessage());
        }
        return result;
    }

    public void setCoreFunctionName(String cfName) {this.coreFunctionName = cfName;}
    public String getCoreFunctionName() {return this.coreFunctionName;}
    public void setParams(List<Node> p) {this.params = p;}
    public List<Node> getParams() {
        if (this.params != null) {
            return this.params;
        }
        else {
            return new ArrayList<Node>();
        }
    }
    
    private NumberType resolveCount0(OperonValue currentValue) throws OperonGenericException {
        NumberType result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);

        if (currentValue instanceof ArrayType) {
            result = (NumberType) (new ArrayCount(this.getStatement())).evaluate();
            return result;
        }
        
        else if (currentValue instanceof ObjectType) {
            result = (NumberType) (new ObjectCount(this.getStatement())).evaluate();
            return result;
        }
        
        else {
            //System.err.println("COUNT CV=" + currentValue);
            ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", "count", "Invalid input. Expected Array or Object");
            return null;
        }
    }

    private OperonValue resolveParent0(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        //if (currentValue.getParentObj() == null) {
        //    return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", "parent", "Invalid input. Parent not supported, got: " + currentValue.getClass().getName());
        //}
        
        //else {
            result = (new GenericParent(this.getStatement())).evaluate();
            return result;
        //}
    }

    private OperonValue resolveRoot0(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        
        // root uses the parent -relation to traverse path
        //if (currentValue.getParentObj() == null) {
        //    return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", "root", "Invalid input. Parent not supported, got: " + currentValue.getClass().getName());
        //}
        
        //else {
            result = (new GenericRoot(this.getStatement())).evaluate();
            return result;
        //}
    }

    private OperonValue resolveNext0(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        
        // Check that position() exists
        //if (currentValue.getPosition() == 0) {
        //    return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", "next", "Invalid input. Position not supported, got: " + currentValue.getClass().getName());
        //}
        // next() uses the parent -relation to traverse path
        //if (currentValue.getParentObj() == null) {
        //    return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", "next", "Invalid input. Position not supported, got: " + currentValue.getClass().getName());
        //}
        
        //else {
            result = (new GenericNext(this.getStatement())).evaluate();
            return result;
        //}
    }

    private OperonValue resolvePrevious0(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        
        // previous() uses the parent -relation to traverse path
        // uses also position()
        //if (currentValue.getParentObj() == null || currentValue.getPosition() == 0) {
        //    return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", "previous", "Invalid input. Parent not supported, got: " + currentValue.getClass().getName());
        //}
        
        //else {
            result = (new GenericPrevious(this.getStatement())).evaluate();
            return result;
        //}
    }

    private OperonValue resolveSpliceLeft1(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof ArrayType == false 
         && currentValue instanceof ObjectType == false
         && currentValue instanceof StringType == false) {
            return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Array, Object or String.");
        }
        
        Node spliceLeft = new SpliceLeft(this.getStatement(), this.getParams());
        result = spliceLeft.evaluate();
        return result;
    }

    private OperonValue resolveSpliceRight1(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof ArrayType == false 
         && currentValue instanceof ObjectType == false
         && currentValue instanceof StringType == false) {
            return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Array, Object or String.");
        }
        
        Node spliceRight = new SpliceRight(this.getStatement(), this.getParams());
        result = spliceRight.evaluate();
        return result;
    }

    private OperonValue resolveSpliceRange2(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof ArrayType == false 
         && currentValue instanceof ObjectType == false
         && currentValue instanceof StringType == false) {
            return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Array, Object or String.");
        }
        
        Node spliceRange = new SpliceRange(this.getStatement(), this.getParams());
        result = spliceRange.evaluate();
        return result;
    }

    private ArrayType resolveSearch1(OperonValue currentValue) throws OperonGenericException {
        ArrayType result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof ArrayType) {
            Node arraySearchFunc = new ArraySearch(this.getStatement(), this.getParams());
            result = (ArrayType) arraySearchFunc.evaluate();
            return result;
        }
        
        else if (currentValue instanceof StringType) {
            Node stringSearchFunc = new StringSearch(this.getStatement(), this.getParams());
            result = (ArrayType) stringSearchFunc.evaluate();
            return result;
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Array or String.");
            return null;
        }
    }

    private StringType resolveToBase64_1(OperonValue currentValue) throws OperonGenericException {
        StringType result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof RawValue) {
            Node rawToBase64Func = new RawToBase64(this.getStatement(), this.getParams());
            result = (StringType) rawToBase64Func.evaluate();
            return result;
        }
        
        else if (currentValue instanceof StringType) {
            Node stringToBase64Func = new StringToBase64(this.getStatement(), this.getParams());
            result = (StringType) stringToBase64Func.evaluate();
            return result;
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Raw or String.");
            return null;
        }
    }

    private OperonValue resolveRemove1(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof ArrayType) {
            Node arrayRemoveFunc = new ArrayRemove(this.getStatement(), this.getParams());
            result = arrayRemoveFunc.evaluate();
            return result;
        }
        
        else if (currentValue instanceof ObjectType) {
            Node objectRemoveFunc = new ObjectRemove(this.getStatement(), this.getParams());
            result = objectRemoveFunc.evaluate();
            return result;
        }
        
        else {
            return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Array or Object.");
        }
    }

    private OperonValue resolveValue0(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof Path) {
            Node pathValue0Func = new PathValue(this.getStatement());
            result = pathValue0Func.evaluate();
            return result;
        }
        
        else if (currentValue instanceof ObjectType) {
            Node objValue0Func = new ObjectValue(this.getStatement(), this.getParams());
            result = objValue0Func.evaluate();
            return result;
        }
        
        else {
            return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Object or Path.");
        }
    }

    private NumberType resolveLength0(OperonValue currentValue) throws OperonGenericException {
        NumberType result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof Path) {
            Node pathLength0Func = new PathLength(this.getStatement());
            result = (NumberType) pathLength0Func.evaluate();
            return result;
        }
        
        else if (currentValue instanceof StringType) {
            Node stringLength0Func = new StringLength(this.getStatement());
            result = (NumberType) stringLength0Func.evaluate();
            return result;
        }
        
        else {
            ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected Object or Path.");
            return null;
        }
    }

    private OperonValue resolveOrganize1(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof StringType) {
            Node stringOrganizeFunc = new StringOrganize(this.getStatement(), this.getParams());
            result = stringOrganizeFunc.evaluate();
            return result;
        }
        
        else if (currentValue instanceof RawValue) {
            Node rawOrganizeFunc = new SRawOrganize(this.getStatement(), this.getParams());
            result = rawOrganizeFunc.evaluate();
            return result;
        }
        
        else {
            return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected String or Raw.");
        }
    }
    
    private OperonValue resolveCollect1(OperonValue currentValue) throws OperonGenericException {
        OperonValue result = null;
        currentValue = currentValue.evaluate();
        this.getStatement().setCurrentValue(currentValue);
        
        if (currentValue instanceof StringType) {
            Node stringCollectFunc = new StringCollect(this.getStatement(), this.getParams());
            result = stringCollectFunc.evaluate();
            return result;
        }
        
        else if (currentValue instanceof RawValue) {
            Node rawCollectFunc = new SRawCollect(this.getStatement(), this.getParams());
            result = rawCollectFunc.evaluate();
            return result;
        }
        
        else {
            return ErrorUtil.createErrorValueAndThrow(currentValue.getStatement(), "RESOLVER", this.getCoreFunctionName(), "Invalid input. Expected String or Raw.");
        }
    }
}