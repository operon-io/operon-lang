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

package io.operon.runner.model.exception;

import io.operon.runner.node.Node;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

public class OperonGenericException extends Exception {

    // 
    // These are the fields that may be used to build the representation of the error
    // 
    private String type; // e.g. "SEMERR" for semantic errors
    private String code; // coded value, e.g. "0001"
    private String errorMessage;
    private String valueRefKey; // e.g. $a
    private String functionNamespace;
    private String functionName;
    private String detailsUri; // https://operon.io/exceptions/{type}/code
    private String nodeType; // where the exception occured, e.g. ValueRef

    // 
    // This is what is assigned from the "Throw"-clause, e.g..
    // Throw ("oops!") --> assigns "oops!" as errorJson.
    //
    private OperonValue errorJson;
    
    // The value that was valid before the error occured.
    private OperonValue currentValue;
    
    // The value before error. Error-handling does not modify this.
    private OperonValue valueBeforeError;
    
    // This is the serialized form of this error
    //private ObjectType errorObject;
    private ErrorValue errorValue;

    public OperonGenericException(String msg) {
        super(ErrorUtil.sanitizeValue(msg));
        try {
            this.setErrorJson(JsonUtil.operonValueFromString(this.getMessage()));
            this.setErrorMessage(this.getMessage());
        } catch (Exception e) {
            
        }
    }

    public OperonGenericException(OperonValue e) {
        //
        // Refer to Exception
        // Done for Exception, so that existing functionality would still work
        super(ErrorUtil.sanitizeValue(e.toString()));
        this.setErrorJson(e);
        ErrorValue errorValue = new ErrorValue(e.getStatement());
        errorValue.setErrorJson(e);
        this.setErrorValue(errorValue);
        
        if (e instanceof ObjectType) {
            try {
                // assumes that object has field called message
                String msg = ErrorUtil.sanitizeValue(((StringType) ((ObjectType) e).getByKey("message").evaluate()).getJavaStringValue());
                this.setErrorMessage(msg);
            } catch (Exception ex) {
                // suppress
                //System.err.println("Could not set error-message, field \"message\" was not found");
            }
        }
        else if (e instanceof ErrorValue) {
            ErrorValue ev = (ErrorValue) e;
            this.setErrorMessage(ev.getMessage());
        }
    }
    
    public void setErrorJson(OperonValue ejs) {
        this.errorJson = ejs;
    }
    
    public OperonValue getErrorJson() {
        return this.errorJson;
    }
    
    public void setErrorValue(ErrorValue ev) {
        this.errorValue = ev;
    }
    
    public ErrorValue getErrorValue() {
        return this.errorValue;
    }
    
    public void setType(String t) {this.type = t;}
    public void setCode(String c) {this.code = c;}
    public void setErrorMessage(String m) {this.errorMessage = m;}
    public void setValueRefKey(String frk) {this.valueRefKey = frk;}
    public void setFunctionNamespace(String fns) {this.functionNamespace = fns;}
    public void setFunctionName(String fn) {this.functionName = fn;}
    public void setDetailsUri(String duri) {this.detailsUri = duri;}
    public void setNodeType(String nt) {this.nodeType = nt;}
    public void setCurrentValue(OperonValue cv) {this.currentValue = cv;}
    public void setValueBeforeError(OperonValue v) {this.valueBeforeError = v;}
    
    public String getType() { return this.type; }
    public String getCode() { return this.code; }
    public String getErrorMessage() { return this.errorMessage; }
    public String getValueRefKey() { return this.valueRefKey; }
    public String getFunctionNamespace() { return this.functionNamespace; }
    public String getFunctionName() { return this.functionName; }
    public String getDetailsUri() { return this.detailsUri; }
    public String getNodeType() { return this.nodeType; }
    public OperonValue getCurrentValue() { return this.currentValue; }
    public OperonValue getValueBeforeError() { return this.valueBeforeError; }

}