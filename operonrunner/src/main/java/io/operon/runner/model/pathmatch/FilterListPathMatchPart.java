/** OPERON-LICENSE **/
package io.operon.runner.model.pathmatch;

import java.util.List;

import io.operon.runner.statement.Statement; 
import io.operon.runner.node.Node;
import io.operon.runner.node.FilterList;
import io.operon.runner.node.FilterListExpr;
import io.operon.runner.util.ErrorUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;
 
import org.apache.logging.log4j.LogManager; 
 
public class FilterListPathMatchPart implements PathMatchPart { 
    
    private FilterList filterList;
    
    public FilterListPathMatchPart() {}
    
    public FilterListPathMatchPart(FilterList fl) {
        this.filterList = fl;
    }
    
    public void setFilterList(FilterList fl) {
        this.filterList = fl;
    }
    
    public FilterList getFilterList() {
        return this.filterList;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        List<FilterListExpr> flExprList = this.getFilterList().getFilterExprList();

        for (int i = 0; i < flExprList.size(); i ++) {
            if (i < flExprList.size() - 1) {
                sb.append(flExprList.get(i).getExpr() + ", ");
            }
            else {
                sb.append(flExprList.get(i).getExpr());
            }
        }

        sb.append("]");
        return sb.toString();
    }
}