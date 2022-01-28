/** OPERON-LICENSE **/
package io.operon.runner.model;

import io.operon.runner.system.InputSourceDriver;
import io.operon.runner.node.type.OperonValue;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.node.type.PairType;
import io.operon.runner.node.type.StringType;
import io.operon.runner.system.inputsourcedriver.json.JsonSystem;
import io.operon.runner.system.inputsourcedriver.sequence.SequenceSystem;
import io.operon.runner.system.inputsourcedriver.timer.TimerSystem;
import io.operon.runner.system.inputsourcedriver.socketserver.SocketServerSystem;
import io.operon.runner.system.inputsourcedriver.subscribe.SubscribeSystem;
import io.operon.runner.system.inputsourcedriver.queue.QueueSystem;
import io.operon.runner.system.inputsourcedriver.file.FileSystem;
import io.operon.runner.system.inputsourcedriver.readline.ReadlineSystem;
import io.operon.runner.system.inputsourcedriver.httpserver.HttpServerSystem;
import io.operon.runner.system.ComponentSystemUtil;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class InputSource {
    private static Logger log = LogManager.getLogger(InputSource.class);
    
    private transient InputSourceDriver system; // E.g. json, file, http, timer, etc... Loaded dynamically, as an ISD-component.
    private String name;
    private String systemId;

    private ObjectType configuration;
    private OperonValue initialValue;

    public InputSource() {}

    public void setInputSourceDriver(InputSourceDriver sys) {
        this.system = sys;
    }
    
    public InputSourceDriver getInputSourceDriver() {
        return this.system;
    }
    
    public void setName(String n) {
        this.name = n;
    }
    
    public String getName() {
        return this.name;
    }

    public void setSystemId(String id) {
        this.systemId = id;
    }
    
    public String getSystemId() {
        return this.systemId;
    }

    public ObjectType getConfiguration() {
        return this.configuration;
    }
    
    public void setConfiguration(ObjectType configs) {
        this.configuration = configs;
    }
    
    public OperonValue getInitialValue() {
        return this.initialValue;
    }
    
    public void setInitialValue(OperonValue iv) {
        this.initialValue = iv;
    }
    
    //
    // Sets the @param system,
    // by loading isd-component dynamically.
    //
    public void loadInputSourceDriver() {
        if (this.getName().toLowerCase().equals("json")) {
            JsonSystem jsonSystem = new JsonSystem();
            //System.out.println("LOADING INPUT SOURCE DRIVER");
            //System.out.println(">> Initial value :: " + this.getInitialValue());
            jsonSystem.setInitialValue(this.getInitialValue());
            this.setInputSourceDriver((InputSourceDriver) jsonSystem);
        } 
        
        else if (this.getName().toLowerCase().equals("sequence")) {
            SequenceSystem sequenceSystem = new SequenceSystem();
            sequenceSystem.setInitialValue(this.getInitialValue());
            this.setInputSourceDriver((InputSourceDriver) sequenceSystem);
        }
        
        else if (this.getName().toLowerCase().equals("timer")) {
            TimerSystem timerSystem = new TimerSystem();
            timerSystem.setJsonConfiguration(this.getConfiguration());
            this.setInputSourceDriver((InputSourceDriver) timerSystem);
        }

        else if (this.getName().toLowerCase().equals("httpserver")) {
            HttpServerSystem httpServerSystem = new HttpServerSystem();
            httpServerSystem.setJsonConfiguration(this.getConfiguration());
            this.setInputSourceDriver((InputSourceDriver) httpServerSystem);
        }

        else if (this.getName().toLowerCase().equals("file")) {
            FileSystem fileSystem = new FileSystem();
            fileSystem.setJsonConfiguration(this.getConfiguration());
            this.setInputSourceDriver((InputSourceDriver) fileSystem);
        }

        else if (this.getName().toLowerCase().equals("readline")) {
            ReadlineSystem readlineSystem = new ReadlineSystem();
            readlineSystem.setJsonConfiguration(this.getConfiguration());
            this.setInputSourceDriver((InputSourceDriver) readlineSystem);
        }

        else if (this.getName().toLowerCase().equals("operon")) {
            SocketServerSystem socketServerSystem = new SocketServerSystem();
            socketServerSystem.setJsonConfiguration(this.getConfiguration());
            this.setInputSourceDriver((InputSourceDriver) socketServerSystem);
        }

        else if (this.getName().toLowerCase().equals("subscribe")) {
            SubscribeSystem subscribeSystem = new SubscribeSystem();
            subscribeSystem.setJsonConfiguration(this.getConfiguration());
            this.setInputSourceDriver((InputSourceDriver) subscribeSystem);
        }

        else if (this.getName().toLowerCase().equals("queue")) {
            QueueSystem queueSystem = new QueueSystem();
            queueSystem.setJsonConfiguration(this.getConfiguration());
            this.setInputSourceDriver((InputSourceDriver) queueSystem);
        }

        else {
            // Assign correct component for the inputSourceSystem
            // This could be e.g. "http", "file", "timer", or some other basic-component.
            log.debug("Compiler :: loading inputSourceDriver :: " + this.getName());
            
            // load component dynamically:
            try {
                log.debug("  - loading isd-componentsDefinitionObj");
                ObjectType isdComponentObj = ComponentSystemUtil.loadInputSourceDriverComponentDefinition(this.getName(), "components.json");
                log.debug("  - isd componentsDefinitionObj loaded");
                System.out.println("ISD componentsDefinitionObj loaded :: " + isdComponentObj);
                String componentLink = ""; // link to .jar -file
                
                ObjectType altConfigObjTop = null;
                ObjectType altConfigObj = null;
                
                for (PairType pair : isdComponentObj.getPairs()) {
                    String key = pair.getKey();
                    log.debug("  - key :: " + key);
                    if (key.equals("\"resolveUri\"")) {
                        componentLink = ((StringType) pair.getValue().evaluate()).getJavaStringValue();
                        log.debug("  - linking isd-component :: " + componentLink);
                    }
                    //
                    // "configuration": {"foo": {...}}
                    //
                    if (key.equals("\"configuration\"")) {
                        altConfigObjTop = (ObjectType) pair.getValue().evaluate();
                        for (PairType confPair : altConfigObjTop.getPairs()) {
                            String confKey = confPair.getKey();
                            if (confKey.equals("\"" + this.getSystemId() + "\"")) {
                                altConfigObj = (ObjectType) confPair.getValue().evaluate();
                            }
                        }
                    }
                }
                
                String JAR_URL = "jar:" + componentLink + "!/";
                
                log.debug("  - loading component");
                Class isdClass = ComponentSystemUtil.loadComponent(JAR_URL, componentLink);
                log.debug("  - component class loaded");
                
                InputSourceDriver isdInstance = (InputSourceDriver) isdClass.getDeclaredConstructor().newInstance();
                if (this.getConfiguration() != null && this.getConfiguration().getPairs().size() > 0) {
                    isdInstance.setJsonConfiguration(this.getConfiguration());
                }
                else if (altConfigObj != null && altConfigObj.getPairs().size() > 0) {
                    isdInstance.setJsonConfiguration(altConfigObj);
                }
                this.setInputSourceDriver(isdInstance);
            } catch (Exception e) {
                throw new RuntimeException("Error while loading dynamic isd :: " + this.getName() + " :: msg :: " + e.getMessage());
            }
        }
    }
    
    @Override
    public String toString() {
        return this.getName() + ":" + this.getSystemId();
    }
}