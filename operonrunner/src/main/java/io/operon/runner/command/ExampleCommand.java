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
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.Main;

//
// This gives a query-example
//
public class ExampleCommand implements MainCommand {

    public ExampleCommand() {}

    public int execute(List<CommandLineOption> options) throws OperonGenericException {
        StringBuilder sb = new StringBuilder();
        sb.append("Example:\n");
        
        sb.append("1. Create a file 'q.op' with the following content:\n$:\"Hello world!\"\nSelect: $\n");
        sb.append("\n2. To run the query, issue command: ./operon q.op\n");
        
        sb.append("\n");
        sb.append("We can achieve the same result without saving the query in the file first, but running it directly with Operon with 'query' -option:\n");
        sb.append("./operon -q '$:\"Hello world!\" Select: $'\n");
        sb.append("This method is good for short queries, and when the input is expected from the system's input-stream.\n");
        
        sb.append("\n");
        sb.append("To receive the input from the system's input-stream and to forward it to the query (works in Linux-systems):\n");
        sb.append("echo '\"Hello world!\"' | ./operon -is -q 'Select: $'\n");
        
        sb.append("\n");
        sb.append("For more examples visit https://operon.io\n");
        System.out.println(sb.toString());
        return 0;
    }
}
