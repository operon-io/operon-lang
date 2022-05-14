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

import java.util.Base64;

import io.operon.runner.statement.Statement;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.Node;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;


public class ErrorValue extends OperonValue implements Node, AtomicOperonValue {

    private String errorCode = "";
    private String errorMessage = "";
    private String errorType = "generic";
    private OperonValue json = null;
    
    public ErrorValue(Statement stmnt) {
        super(stmnt);
        this.json = new EmptyType(stmnt);
    }

    public ErrorValue evaluate() throws OperonGenericException {
        this.setUnboxed(true);
        return this;
    }
    
    public void setCode(String code) {
        this.errorCode = code;
    }
    
    public String getCode() {
        return this.errorCode;
    }

    public void setType(String t) {
        this.errorType = t;
    }
    
    public String getType() {
        return this.errorType;
    }

    public void setMessage(String msg) {
        this.errorMessage = msg;
    }
    
    public String getMessage() {
        return this.errorMessage;
    }

    public void setErrorJson(OperonValue j) {
        this.json = j;
    }

    public OperonValue getErrorJson() {
        return this.json;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Error(");
        
        sb.append("\"code\": \"");
        sb.append(this.getCode());
        sb.append("\", ");
    
        sb.append("\"type\": \"");
        sb.append(this.getType());
        sb.append("\", ");
    
        sb.append("\"message\": \"");
        sb.append(this.getMessage());
        sb.append("\"");
        
        if (this.getErrorJson() != null && (this.getErrorJson() instanceof EmptyType) == false) {
            sb.append(", ");
            sb.append("\"json\": ");
            sb.append(this.getErrorJson());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        StringBuilder sb = new StringBuilder();
        sb.append("Error(");
        
        sb.append("\"code\": \"");
        sb.append(this.getCode());
        sb.append("\", ");
    
        sb.append("\"type\": \"");
        sb.append(this.getType());
        sb.append("\", ");
    
        sb.append("\"message\": \"");
        sb.append(this.getMessage());
        sb.append("\"");
        
        if (this.getErrorJson() != null && (this.getErrorJson() instanceof EmptyType) == false) {
            sb.append(", ");
            sb.append("\"json\": ");
            sb.append(this.getErrorJson());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toYamlString(YamlFormatter yf) {
        StringBuilder sb = new StringBuilder();
        sb.append("Error(");
        
        sb.append("\"code\": \"");
        sb.append(this.getCode());
        sb.append("\", ");
    
        sb.append("\"type\": \"");
        sb.append(this.getType());
        sb.append("\", ");
    
        sb.append("\"message\": \"");
        sb.append(this.getMessage());
        sb.append("\"");
        
        if (this.getErrorJson() != null && (this.getErrorJson() instanceof EmptyType) == false) {
            sb.append(", ");
            sb.append("\"json\": ");
            sb.append(this.getErrorJson());
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String toTomlString(OutputFormatter ofmt) {
        StringBuilder sb = new StringBuilder();
        sb.append("Error(");
        
        sb.append("\"code\": \"");
        sb.append(this.getCode());
        sb.append("\", ");
    
        sb.append("\"type\": \"");
        sb.append(this.getType());
        sb.append("\", ");
    
        sb.append("\"message\": \"");
        sb.append(this.getMessage());
        sb.append("\"");
        
        if (this.getErrorJson() != null && (this.getErrorJson() instanceof EmptyType) == false) {
            sb.append(", ");
            sb.append("\"json\": ");
            sb.append(this.getErrorJson());
        }
        sb.append(")");
        return sb.toString();
    }

}