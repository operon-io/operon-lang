/** OPERON-LICENSE **/
package io.operon.runner.command;

import java.util.List;
import java.util.ArrayList;

public class CommandLineOptionParser {
    
    public CommandLineOptionParser() {}
    
    public List<CommandLineOption> parse(String [] args) {
        List<CommandLineOption> result = new ArrayList<CommandLineOption>();
        
        boolean nextIsValue = false;
        CommandLineOption co = null;
        
        for (int i = 0; i < args.length; i ++) {
            if (nextIsValue) {
                co.setOptionValue(args[i]);
                nextIsValue = false;
                result.add(co);
                continue;
            }
            co = new CommandLineOption();
            switch (args[i].toLowerCase()) {
                case "--inputstream":
                    co.setOptionName("inputstream");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "-is": // Short-version (short-versions have the "-", long-versions "--")
                    co.setOptionName("inputstream");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--streamlines":
                    co.setOptionName("streamlines");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "-sl":
                    co.setOptionName("streamlines");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--raw":
                    co.setOptionName("raw");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "-r":
                    co.setOptionName("raw");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--help":
                    co.setOptionName("help");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--version":
                    co.setOptionName("version");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--example":
                    co.setOptionName("example");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--query":
                    co.setOptionName("query");
                    nextIsValue = true;
                    break;
                case "-q":
                    co.setOptionName("query");
                    nextIsValue = true;
                    break;
                case "-iq":
                    co.setOptionName("inputstream");
                    result.add(co);
                    co = new CommandLineOption();
                    co.setOptionName("query");
                    nextIsValue = true;
                    break;
                case "-irq":
                    co.setOptionName("inputstream");
                    result.add(co);
                    co = new CommandLineOption();
                    co.setOptionName("raw");
                    result.add(co);
                    co = new CommandLineOption();
                    co.setOptionName("query");
                    nextIsValue = true;
                    break;
                case "--testsfolder":
                    co.setOptionName("testsfolder");
                    nextIsValue = true;
                    break;
                case "-tf":
                    co.setOptionName("testsfolder");
                    nextIsValue = true;
                    break;
                case "--tests":
                    co.setOptionName("tests");
                    nextIsValue = true;
                    break;
                case "-ts":
                    co.setOptionName("tests");
                    nextIsValue = true;
                    break;
                case "--test":
                    co.setOptionName("test");
                    nextIsValue = false;
                    result.add(co);
                    break;
                case "-t":
                    co.setOptionName("test");
                    nextIsValue = false;
                    result.add(co);
                    break;
                case "--timezone":
                    co.setOptionName("timezone");
                    nextIsValue = true;
                    break;
                case "-tz":
                    co.setOptionName("timezone");
                    nextIsValue = true;
                    break;
                case "--redishost":
                    co.setOptionName("redishost");
                    nextIsValue = true;
                    break;
                case "-rh":
                    co.setOptionName("redishost");
                    nextIsValue = true;
                    break;
                case "--redisport":
                    co.setOptionName("redisport");
                    nextIsValue = true;
                    break;
                case "-rp":
                    co.setOptionName("redisport");
                    nextIsValue = true;
                    break;
                case "--redisuser":
                    co.setOptionName("redisuser");
                    nextIsValue = true;
                    break;
                case "-ru":
                    co.setOptionName("redisuser");
                    nextIsValue = true;
                    break;
                case "--redispassword":
                    co.setOptionName("redispassword");
                    nextIsValue = true;
                    break;
                case "-rpwd":
                    co.setOptionName("redispassword");
                    nextIsValue = true;
                    break;
                case "--redisprefix":
                    co.setOptionName("redisprefix");
                    nextIsValue = true;
                    break;
                case "-rpfx":
                    co.setOptionName("redisprefix");
                    nextIsValue = true;
                    break;
                case "--disablecomponents":
                    co.setOptionName("disablecomponents");
                    nextIsValue = true;
                    break;
                case "-dc":
                    co.setOptionName("disablecomponents");
                    nextIsValue = true;
                    break;
                case "--printduration":
                    co.setOptionName("printduration");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "-pd": // Short-version (short-versions have the "-", long-versions "--")
                    co.setOptionName("printduration");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--prettyprint":
                    co.setOptionName("prettyprint");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "-pp": // Short-version (short-versions have the "-", long-versions "--")
                    co.setOptionName("prettyprint");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "--omitresult":
                    co.setOptionName("omitresult");
                    result.add(co);
                    nextIsValue = false;
                    break;
                case "-or": // Short-version (short-versions have the "-", long-versions "--")
                    co.setOptionName("omitresult");
                    result.add(co);
                    nextIsValue = false;
                    break;
                default:
                    if (args[i].trim().startsWith(":") 
                        || args[i].trim().startsWith("$") 
                        || args[i].trim().startsWith("{")
                        || args[i].trim().startsWith("Select:")
                        || args[i].trim().startsWith("Select {")
                        ) {
                        co.setOptionName("query");
                        co.setOptionValue(args[i]);
                        nextIsValue = false;
                        result.add(co);
                    }
                    else {
                        co.setOptionName("filename");
                        co.setOptionValue(args[i]);
                        nextIsValue = false;
                        result.add(co);
                    }
                    break;
            }
        }
        
        return result;
    }
    
}
