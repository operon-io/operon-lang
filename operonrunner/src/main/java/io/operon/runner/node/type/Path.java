/** OPERON-LICENSE **/
package io.operon.runner.node.type;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement; 
import io.operon.runner.model.path.*;
import io.operon.runner.node.Node; 
import io.operon.runner.node.type.*;
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.util.OutputFormatter;
import io.operon.runner.util.YamlFormatter;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 

//
// Path is definitive: there are no expressions to
// be calculated.
//
// Example: Path(.Persons[1].fname)
//
public class Path extends OperonValue implements Node, AtomicOperonValue { 
    private static Logger log = LogManager.getLogger(Path.class); 
    
    //
    // This is the "root"-object of the Path. This is either an Array or Object.
    //
    private OperonValue objLink;
    
    //
    // This is the value of the Path. This may be any Operon-value.
    //
    private OperonValue valueLink;
    
    private List<PathPart> pathParts;
    
    public Path(Statement stmnt) { 
        super(stmnt);
        this.pathParts = new ArrayList<PathPart>();
    }
 
    public Path evaluate() throws OperonGenericException { 
        log.debug("Path :: evaluate");
        this.setUnboxed(true);
        return this;
    } 

    public void setObjLink(OperonValue ol) {
        this.objLink = ol;
    }

    public OperonValue getObjLink() { 
        return this.objLink; 
    }

    public void setValueLink(OperonValue vl) {
        this.valueLink = vl;
    }

    public OperonValue getValueLink() { 
        return this.valueLink; 
    }

    public int length() {
        return this.getPathParts().size();
    }

    public List<PathPart> getPathParts() { 
        return this.pathParts;
    } 
 
    public void setPathParts(List<PathPart> pp) { 
        this.pathParts = pp; 
    } 
     
    public void addPathPart(PathPart pp) { 
        this.pathParts.add(pp); 
    }

    public void removeLastPathPart() {
        if (this.getPathParts().size() > 0) {
            this.pathParts.remove(this.getPathParts().size() - 1);
        }
    }

    public Path copy() {
        List<PathPart> ppList = new ArrayList<PathPart>();
        for (int i = 0; i < this.getPathParts().size(); i ++) {
            ppList.add(this.getPathParts().get(i).copy());
        }
        Path copyPath = new Path(this.getStatement());
        copyPath.setPathParts(ppList);
        copyPath.setObjLink(this.getObjLink());
        copyPath.setValueLink(this.getValueLink());
        return copyPath;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        Path path2 = (Path) obj;

        if (this.getPathParts().size() != path2.getPathParts().size()) {
            return false;
        }
        else {
            for (int i = 0; i < this.getPathParts().size(); i ++) {
                if (this.getPathParts().get(i).equals(path2.getPathParts().get(i)) == false) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (PathPart pp : this.getPathParts()) {
            sb.append(pp.toString());
        }
        sb.append("\"");
        return sb.toString();
    }
    
    @Override
    public String toFormattedString(OutputFormatter ofmt) {
        if (ofmt == null) {ofmt = new OutputFormatter();}
        StringBuilder sb = new StringBuilder();
        if (ofmt.pathPrefix != null) {
            sb.append(ofmt.pathPrefix);
        }
        
        if (ofmt.pathStart != Character.MIN_VALUE) {
            sb.append(ofmt.pathStart);
        }
        
        if (ofmt.pathInnerStart != Character.MIN_VALUE) {
            sb.append(ofmt.pathInnerStart);
        }
        
        for (PathPart pp : this.getPathParts()) {
            sb.append(pp.toString());
        }
        
        if (ofmt.pathInnerEnd != Character.MIN_VALUE) {
            sb.append(ofmt.pathInnerEnd);
        }
        
        if (ofmt.pathEnd != Character.MIN_VALUE) {
            sb.append(ofmt.pathEnd);
        }
        
        return sb.toString();
    }
    
    @Override
    public String toYamlString(YamlFormatter yf) {
        if (yf == null) {yf = new YamlFormatter();}
        StringBuilder sb = new StringBuilder();
        if (yf.pathPrefix != null) {
            sb.append(yf.pathPrefix);
        }
        
        if (yf.pathStart != Character.MIN_VALUE) {
            sb.append(yf.pathStart);
        }
        
        if (yf.pathInnerStart != Character.MIN_VALUE) {
            sb.append(yf.pathInnerStart);
        }
        
        for (PathPart pp : this.getPathParts()) {
            sb.append(pp.toString());
        }
        
        if (yf.pathInnerEnd != Character.MIN_VALUE) {
            sb.append(yf.pathInnerEnd);
        }
        
        if (yf.pathEnd != Character.MIN_VALUE) {
            sb.append(yf.pathEnd);
        }
        
        return sb.toString();
    }
}