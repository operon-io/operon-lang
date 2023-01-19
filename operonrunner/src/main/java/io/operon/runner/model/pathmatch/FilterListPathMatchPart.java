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