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
