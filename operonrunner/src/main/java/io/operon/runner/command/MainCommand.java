/** OPERON-LICENSE **/
package io.operon.runner.command;

import java.util.List;
import io.operon.runner.model.exception.OperonGenericException;

public interface MainCommand {

    public int execute(List<CommandLineOption> options) throws OperonGenericException;

}
