/** OPERON-LICENSE **/
package io.operon.runner.model.pathmatch;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node;
import io.operon.runner.node.Node; 
import io.operon.runner.node.type.*;
import io.operon.runner.node.AbstractNode; 
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 

//
// Container for parts that consist the matching expr.
//
public class PathMatch extends OperonValue implements Node { 
    private static Logger log = LogManager.getLogger(PathMatch.class); 

    private List<PathMatchPart> pathMatchParts;

    public PathMatch(Statement stmnt) { 
        super(stmnt);
        this.pathMatchParts = new ArrayList<PathMatchPart>();
    }

    public int length() {
        return this.getPathMatchParts().size();
    }

    public List<PathMatchPart> getPathMatchParts() { 
        return this.pathMatchParts;
    } 
 
    public void setPathMatchParts(List<PathMatchPart> pmp) { 
        this.pathMatchParts = pmp; 
    } 
     
    public void addPathMatchPart(PathMatchPart pmp) { 
        this.pathMatchParts.add(pmp); 
    }

    public void removeLastPathMatchPart() {
        if (this.getPathMatchParts().size() > 0) {
            this.pathMatchParts.remove(this.getPathMatchParts().size() - 1);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (PathMatchPart pmp : this.getPathMatchParts()) {
            sb.append(pmp.toString());
        }
        sb.append("\"");
        return sb.toString();
    }
}