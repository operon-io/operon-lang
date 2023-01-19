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
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.Main;

public class HelpCommand implements MainCommand {

    public HelpCommand() {}

    public int execute(List<CommandLineOption> options) throws OperonGenericException {
        StringBuilder sb = new StringBuilder();
        sb.append("Operon version: " + Main.VERSION + "\n");
        sb.append("Copyright (C) 2021\n");
        sb.append("Author: tuomas@operon.io --> feel free to send me suggestions, ask help or provide support :-)\n");
        sb.append("Web: https://operon.io\n");
        sb.append("License: Operon-license. See: https://operon.io/operon-license.html\n");
        sb.append("\n");
        sb.append("Default usage:\n");
        sb.append("  operon query.op\n");
        sb.append("\n");
        sb.append("Options:\n");
        sb.append("  --help : this helpful output.\n");
        sb.append("  --version : prints only the Operon-version.\n");
        sb.append("  --raw or -r : interprets the input from the inputstream-option as raw (e.g. csv or xml-file should be read as raw).\n");
        sb.append("  --example : shows an example Operon-query.\n");
        sb.append("  --inputstream or -is : receives JSON-input from the system's inputstream. This overrides any given root-value from the query.\n");
        sb.append("  --omitresult or -or: does not print the result.\n");
        sb.append("  --prettyprint or -pp: prints the output formatted.\n");
        sb.append("  --printduration or -pd: prints after the query has been executed how long the execution took.\n");
        sb.append("  --query or -q: run the query given in the commandline.\n");
        sb.append("  -iq : the combination of --inputstream and --query.\n");
        sb.append("  -irq : the combination of --inputstream, --raw and --query.\n");
        sb.append("  --redishost or -rh: redis hostname if not localhost.\n");
        sb.append("  --redisport or -rp: redis port, if not default.\n");
        sb.append("  --redisuser or -ru: redis user, if not empty.\n");
        sb.append("  --redispassword or -rpwd: redis password, if not empty.\n");
        sb.append("  --redisprefix or -rpfx: prefix for state:set/get key.\n");
        sb.append("  --streamlines or -sl: reads the input line by line, sending each line for the query.\n");
        sb.append("  --test or -t: runs the default testfile \"operon.tests\"\n");
        sb.append("  --tests or -ts \"file1.test, file2.optest,...\": runs the named testfiles separated by commas.\n");
        sb.append("  --testsfolder or -tf \"./tests\": run all the tests from the given folder and its subfolders. This can be combined with --tests -option, so only the named tests will be run.\n");
        sb.append("  --timezone or -tz. Examples: -tz \"UTC\" or -tz \"Europe/Helsinki\": sets the default-timezone for Operon.\n");
        sb.append("  --disablecomponents or -dc: list of the components that are not allowed to use in the query.\n");
        sb.append("\n");
        System.out.println(sb.toString());
        return 0;
    }
}
