/** OPERON-LICENSE **/
package io.operon.runner.command;

import java.util.List;
import java.util.ArrayList;

public class CommandLineOption {
    String optionName; // e.g. "--test"
    String optionValue; // e.g. "test1.test, test2.test"

    public CommandLineOption() {
        
    }
    
    public void setOptionName(String on) {
        this.optionName = on;
    }
    
    public String getOptionName() {
        return this.optionName;
    }
    
    public void setOptionValue(String ov) {
        this.optionValue = ov;
    }
    
    public String getOptionValue() {
        return this.optionValue;
    }
    
}
