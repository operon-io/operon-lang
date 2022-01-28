/** OPERON-LICENSE **/
package io.operon.runner.command;

import java.util.List;
import io.operon.runner.model.exception.OperonGenericException;
import io.operon.runner.Main;

public class VersionCommand implements MainCommand {

    public VersionCommand() {}

    public int execute(List<CommandLineOption> options) throws OperonGenericException {
        System.out.println(Main.VERSION);
        return 0;
    }
}
