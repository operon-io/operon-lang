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

package io.operon.runner.command;

import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.operon.runner.Main;
import io.operon.runner.OperonContextManager;
import io.operon.runner.OperonContext;
import io.operon.runner.OperonRunner;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.model.OperonConfigs;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.RawValue;
import io.operon.runner.statement.Statement;
import io.operon.runner.statement.DefaultStatement;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;

public class QueryCommand implements MainCommand {

    private String query = "";
    
    public QueryCommand(String query) {
        this.query = query;
    }

    public int execute(List<CommandLineOption> options) throws OperonGenericException {
        boolean inputstream = false; // piped input: load the $ from the piped inputstream (consumes the whole stream, materializes it and loads as a single value)
        boolean streamLines = false; // assumes piped input (--inputstream): stream each line for own operon-query
        boolean raw = false; // assumes piped input (--inputstream): interpret the piped input as raw
        
        OperonConfigs configs = new OperonConfigs();
        for (CommandLineOption option : options) {
            
            // read input from previous command
            if (option.getOptionName().toLowerCase().equals("inputstream")) {
                inputstream = true;
            }
            
            // read input line by line from the inputstream
            else if (option.getOptionName().toLowerCase().equals("streamlines")) {
                inputstream = true;
                streamLines = true;
            }
            
            // read the input as raw. Sets also inputstream -option.
            else if (option.getOptionName().toLowerCase().equals("raw")) {
                inputstream = true;
                raw = true;
            }
            
            // allows to switch off certain components so they cannot be used
            else if (option.getOptionName().toLowerCase().equals("disablecomponents")) {
                String [] dc = option.getOptionValue().split(",");
                List<String> dcList = new ArrayList<String>();
                for (int i = 0; i < dc.length; i ++) {
                    dcList.add(dc[i].trim());
                }
                //System.out.println("The following components have been disabled: " + dcList);
                configs.setDisabledComponents(dcList);
            }
            
            // allows user to override the system's default-timezone
            else if (option.getOptionName().toLowerCase().equals("timezone")) {
                configs.setTimezone(option.getOptionValue());
            }
            
            // prints how long the query took after it has been executed
            else if (option.getOptionName().toLowerCase().equals("printduration")) {
                configs.setPrintDuration(true);
            }
            
            // format the json-output
            else if (option.getOptionName().toLowerCase().equals("prettyprint")) {
                configs.setPrettyPrint(true);
            }
            
            // do not print result
            else if (option.getOptionName().toLowerCase().equals("omitresult")) {
                configs.setOutputResult(false);
            }
            
            // allows to set the redis-host (default is localhost)
            else if (option.getOptionName().toLowerCase().equals("redishost")) {
                configs.setRedisHost(option.getOptionValue());
            }
            
            // allows to override the redis port (default is 6379)
            else if (option.getOptionName().toLowerCase().equals("redisport")) {
                configs.setRedisPort(Integer.valueOf(option.getOptionValue()));
            }
            
            // allows to set the redis-user (by default there is none)
            else if (option.getOptionName().toLowerCase().equals("redisuser")) {
                configs.setRedisUser(option.getOptionValue());
            }
            
            // allows to set the redis-password (by default there is none)
            else if (option.getOptionName().toLowerCase().equals("redispassword")) {
                configs.setRedisPassword(option.getOptionValue());
            }
            
            // allows to set the redis-prefix to control key prefixes (state:set / get) (by default there is none)
            else if (option.getOptionName().toLowerCase().equals("redisprefix")) {
                configs.setRedisPrefix(option.getOptionValue());
            }
        }
        
        OperonRunner runner = new OperonRunner();
        runner.setConfigs(configs);
        
        if (inputstream) {
            BufferedReader f = new BufferedReader(new InputStreamReader(System.in));
            String dataInput = "";
            StringBuilder sb = new StringBuilder();
            
            if (streamLines == false) {
                do {
                    try {
                        dataInput = f.readLine();
                        if (dataInput != null) {
                            sb.append(dataInput + System.getProperty("line.separator"));
                        }
                    } catch (IOException e) {
                        System.err.println("ERROR SIGNAL while reading inputstream");
                        return 1;
                    }
                } while (dataInput != null);
    
                OperonValue initialValue = null;
                
                sb.setLength(sb.length() - 1); // remove the last line break.
                String initialValueStr = sb.toString();
                
                if (raw == false) {
                    initialValue = JsonUtil.operonValueFromString(initialValueStr);
                }
                
                else {
                    try {
                        OperonContext ctx = new OperonContext();
                        Statement stmt = new DefaultStatement(ctx);
                        RawValue rawValue = new RawValue(stmt);
                        rawValue.setValue(initialValueStr.getBytes(StandardCharsets.UTF_8));
                        initialValue = rawValue;
                    } catch (IOException e) {
                       System.err.println("ERROR SIGNAL while reading inputstream: " + e.getMessage());
                    } catch (Exception e) {
                       System.err.println("ERROR SIGNAL while reading inputstream: " + e.getMessage());
                    }
                }
                
                runner.setQuery(this.query);
                runner.setInitialValueForJsonSystem(initialValue);
            }
            
            // streamlines:
            else {
                String line = "";
                OperonRunner runner2 = new OperonRunner();
                do {
                   try {
                        line = f.readLine();
                        if (line != null && line.isEmpty() == false) {
                            // 
                            // When streaming, we use the lightweight-parser by default:
                            // 
                            OperonValue initialValue = null;
                            if (raw == false) {
                                initialValue = JsonUtil.lwOperonValueFromString(line);
                            }
                            else {
                                try {
                                    OperonContext ctx = new OperonContext();
                                    Statement stmt = new DefaultStatement(ctx);
                                    RawValue rawValue = new RawValue(stmt);
                                    rawValue.setValue(line.getBytes(StandardCharsets.UTF_8));
                                    initialValue = rawValue;
                                } catch (IOException e) {
                                   System.err.println("ERROR SIGNAL while reading inputstream: " + e.getMessage());
                                } catch (Exception e) {
                                   System.err.println("ERROR SIGNAL while reading inputstream: " + e.getMessage());
                                }
                            }
                            
                            OperonValue result = runner2.doQueryWithInitialValue(query, initialValue);
                        }
                   } catch (IOException e) {
                       System.err.println("ERROR SIGNAL while reading inputstream: " + e.getMessage());
                   } catch (Exception e) {
                       System.err.println("ERROR SIGNAL while reading inputstream: " + e.getMessage());
                   }
                } while (line != null);
            }
        }
        
        else {
            runner.setQuery(this.query);
        }
        
        if (streamLines == false) {
            runner.run();
        }
        return 0;
    }
}
