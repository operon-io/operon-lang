/** OPERON-LICENSE **/
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
}