/** OPERON-LICENSE **/
package io.operon.runner.processor.function.core.path;

import io.operon.runner.OperonContext;

import java.util.List;
import java.util.ArrayList;

import io.operon.runner.node.AbstractNode;
import io.operon.runner.node.Node;
import io.operon.runner.node.Where;
import io.operon.runner.node.PathMatches;
import io.operon.runner.node.UpdateArray;
import io.operon.runner.node.type.*;
import io.operon.runner.model.path.*;
import io.operon.runner.model.pathmatch.*;
import io.operon.runner.statement.Statement;
import io.operon.runner.processor.function.BaseArity0;
import io.operon.runner.processor.function.Arity0;
import io.operon.runner.util.JsonUtil;
import io.operon.runner.util.ErrorUtil;
import io.operon.runner.model.exception.OperonGenericException;

//
// Input: Array of Paths
//
//  - Paths will be removed from the linked object
//
public class PathReclude extends BaseArity0 implements Node, Arity0 {
    
    public PathReclude(Statement statement) throws OperonGenericException {
        super(statement);
        this.setFunctionName("reclude");
    }

    public OperonValue evaluate() throws OperonGenericException {
        try {
            //System.out.println("path:reclude() evaluate");
            ArrayType pathArray = (ArrayType) this.getStatement().getCurrentValue();
            
            if (pathArray.getValues().size() > 0) {
                Path p0 = (Path) pathArray.getValues().get(0).evaluate();
                OperonValue objLink = p0.getObjLink();
                if (objLink == null) {
                    ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), "No root value bound to path.");
                }
                
                this.getStatement().setCurrentValue(objLink);
                
                //
                // expr for: 'objLink Where ~=?* ', which will return all paths of the Object or Array (objLink).
                //
                Where w = new Where(this.getStatement());
                PathMatch pmExpr = new PathMatch(this.getStatement());
                PathMatchPart anyMatch = new AnyNoneOrMorePathMatchPart(); // '?*'
                pmExpr.getPathMatchParts().add(anyMatch);
                PathMatches pm = new PathMatches(this.getStatement());
                pm.setPathMatch(pmExpr);
                w.setPathMatches(pm);
                
                ArrayType allPaths = (ArrayType) w.evaluate();
                //System.out.println("  AllPaths :: " + allPaths);
                
                List<Node> allPathNodes = allPaths.getValues();
                List<Node> retainPathNodes = pathArray.getValues();
                List<Path> removePaths = new ArrayList<Path>();
                
                for (int i = 0; i < allPathNodes.size(); i ++) {
                    Path a = (Path) allPathNodes.get(i).evaluate();
                    //System.out.println("  - path a :: " + a);
                    for (int j = 0; j < retainPathNodes.size(); j ++) {
                        Path b = (Path) retainPathNodes.get(j).evaluate();
                        //System.out.println("  - path b :: " + b);
                        if (a.equals(b)) {
                            removePaths.add(a);
                        }
                    }
                }
                
                //System.out.println("PathRetain :: removePaths :: " + removePaths);
                
                //
                // create Update-expr, which sets value as empty for removePaths
                //
                ArrayType removePathsArray = new ArrayType(this.getStatement());
                removePathsArray.getValues().addAll(removePaths);
                UpdateArray ua = new UpdateArray(this.getStatement());
                EmptyType empty = new EmptyType(this.getStatement());
                
                ua.setUpdatePathsExpr(removePathsArray);
                ua.setUpdateValuesExpr(empty);
                
                this.getStatement().setCurrentValue(objLink);
                OperonValue result = ua.evaluate();
                //System.out.println("PathRetain :: result :: " + result);
                return result;
            }
            
            else {
                //System.out.println("path:reclude() :: pathsArray.size() == 0");
                // Return empty, because we cannot find the linked object.
                EmptyType empty = new EmptyType(this.getStatement());
                return empty;
            }
        } catch (Exception e) {
            ErrorUtil.createErrorValueAndThrow(this.getStatement(), "FUNCTION", "path:" + this.getFunctionName(), e.getMessage());
            return null;
        }
    }

}