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

package io.operon.runner.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import io.operon.runner.node.type.OperonValue;

public class OperonConfigs {

    private String timezone;
    private List<String> disabledComponents;
    private boolean supportPos = false; // does the query contain pos() -function?
    private boolean supportParent = false; // does the query contain parent / root / previous / next  -function?
    private boolean printDuration = false; // print duration at the end of the query?
    private boolean prettyPrint = false;
    private boolean outputResult = true;
    private boolean indexRoot = true;
    private Map<String, OperonValue> namedValues;
    
    // Redis-related:
    private int redisPort = 6379;
    private String redisHost = null;
    private String redisUser = null;
    private String redisPassword = null;
    private String redisPrefix = null; // when using state:set(key), the key will be prefixed with value in redisPrefix. The state:get(key) will also prefix the key.

    public OperonConfigs() {
        this.disabledComponents = new ArrayList<String>();
        this.namedValues = new HashMap<String, OperonValue>();
    }

    public void setSupportPos(boolean sp) {
        this.supportPos = sp;
    }
    
    public boolean getSupportPos() {
        return this.supportPos;
    }
    
    public void setSupportParent(boolean supPar) {
        this.supportParent = supPar;
    }

    public boolean getSupportParent() {
        return this.supportParent;
    }

    public void setPrintDuration(boolean pd) {
        this.printDuration = pd;
    }
    
    public boolean getPrintDuration() {
        return this.printDuration;
    }

    public void setPrettyPrint(boolean pp) {
        this.prettyPrint = pp;
    }
    
    public boolean getPrettyPrint() {
        return this.prettyPrint;
    }

    public void setOutputResult(boolean or) {
        this.outputResult = or;
    }

    public boolean getOutputResult() {
        return this.outputResult;
    }

    public List<String> getDisabledComponents() {
        return this.disabledComponents;
    }

    public void setDisabledComponents(List<String> dc) {
        this.disabledComponents = dc;
    }

    public void setTimezone(String tz) {
        this.timezone = tz;
    }

    public String getTimezone() {
        return this.timezone;
    }

    public void setIndexRoot(boolean ir) {
        this.indexRoot = ir;
    }
    
    public boolean getIndexRoot() {
        return this.indexRoot;
    }

    public Map<String, OperonValue> getNamedValues() {
        return this.namedValues;
    }
    
    public void setNamedValue(String namedValue, OperonValue value) {
        this.getNamedValues().put(namedValue, value);
    }

    public String getRedisHost() {
        return this.redisHost;
    }

    public void setRedisHost(String rh) {
        this.redisHost = rh;
    }

    public int getRedisPort() {
        return this.redisPort;
    }

    public void setRedisPort(int rp) {
        this.redisPort = rp;
    }

    public String getRedisUser() {
        return this.redisUser;
    }

    public void setRedisUser(String ru) {
        this.redisUser = ru;
    }

    public String getRedisPassword() {
        return this.redisPassword;
    }

    public void setRedisPassword(String rpwd) {
        this.redisPassword = rpwd;
    }
    
    public String getRedisPrefix() {
        return this.redisPrefix;
    }

    public void setRedisPrefix(String rprefix) {
        this.redisPrefix = rprefix;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OperonConfigs\n");
        sb.append(" - timezone: " + this.getTimezone() + "\n");
        sb.append(" - supportPos: " + this.getSupportPos() + "\n");
        sb.append(" - supportParent: " + this.getSupportParent() + "\n");
        sb.append(" - printDuration: " + this.getPrintDuration() + "\n");
        sb.append(" - prettyPrint: " + this.getPrettyPrint() + "\n");
        sb.append(" - outputResult: " + this.getOutputResult() + "\n");
        sb.append(" - indexRoot: " + this.getIndexRoot() + "\n");
        sb.append(" - disabledComponents: " + this.getDisabledComponents() + "\n");
        sb.append(" - namedValues: " + this.getNamedValues() + "\n");
        return sb.toString();
    }
}