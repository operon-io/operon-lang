/*
 *   Copyright 2022, operon.io
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

package io.operon.runner.node;

import java.util.List;

import io.operon.runner.statement.Statement;
import io.operon.runner.model.path.*;
import io.operon.runner.model.pathmatch.*;
import io.operon.runner.processor.function.core.SpliceLeft;
import io.operon.runner.processor.function.core.SpliceRight;
import io.operon.runner.processor.function.core.SpliceRange;
import io.operon.runner.node.type.*;
import io.operon.runner.util.JsonUtil;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

// Input: Path
// Form 1: PathMatches ([3, 10::])
// Form 2: ~ ([3, 10::])
// Returns Boolean
//
public class PathMatches extends AbstractNode implements Node {
     // no logger 
    
    private PathMatch pathMatch;
    
    public PathMatches(Statement stmnt) {
        super(stmnt);
    }

    public OperonValue evaluate() throws OperonGenericException {
        //System.out.println("PathMatches :: evaluate()");
        OperonValue currentValue = this.getStatement().getCurrentValue();
        return PathMatches.matchPath(this.getStatement(), currentValue, this.getPathMatch());
    }

    // 
    // Match currentValue (as Path) against PathMatch
    // Returns TrueType or FalseType
    // 
    public static OperonValue matchPath(Statement stmt, OperonValue currentValue, PathMatch pathMatch) throws OperonGenericException {
        Path path = (Path) currentValue.evaluate();
        TrueType trueResult = new TrueType(stmt);
        FalseType falseResult = new FalseType(stmt);
        
        // Run the path against pathMatch.
        //System.out.println("Path to evaluate against: " + path);
        
        List<PathPart> sourcePathParts = path.getPathParts();
        List<PathMatchPart> pathMatchParts = pathMatch.getPathMatchParts();
        
        boolean previousWildcardAny = false; // '?+' or '?*'
        boolean currentClearMatch = false; // true when matches against Object or Array, but NOT against wildcarded any, i.e. '?+' or '?*'.
        int currentWildcardAnyIndex = 0;
        int pathMatchPartsIndex = -1;
        int sourceIndex = 0;
        
        //System.out.println("sourcePathParts.size()=" + sourcePathParts.size());
        //System.out.println("pathMatchParts.size()=" + pathMatchParts.size());
        
        PathPart sourcePathPart = null;
        PathMatchPart pathMatchPart = null;
        
        for (int i = 0; i < sourcePathParts.size(); i ++) {
            //System.out.println("i=" + i);
            sourceIndex = i;
            
            if (previousWildcardAny) {
                pathMatchPartsIndex = currentWildcardAnyIndex + 1;
            }
            else {
                pathMatchPartsIndex = pathMatchPartsIndex + 1;
            }
            
            if (pathMatchPartsIndex >= pathMatchParts.size() && previousWildcardAny == true) {
                pathMatchPartsIndex -= 1;
            }
            else if (pathMatchPartsIndex >= pathMatchParts.size()) {
                //System.out.println("Breaking for: pathMatchPartsIndex >= pathMatchParts.size()");
                pathMatchPartsIndex -= 1;
                currentClearMatch = false;
                break;
            }
            
            //System.out.println("  - pathMatchPartsIndex=" + pathMatchPartsIndex);
            //System.out.println("  - currentWildcardAnyIndex=" + currentWildcardAnyIndex);
            //System.out.println("  - previousWildcardAny=" + previousWildcardAny);
            
            sourcePathPart = sourcePathParts.get(sourceIndex);
            pathMatchPart = pathMatchParts.get(pathMatchPartsIndex);
            
            //System.out.println("  - SPP: " + sourcePathPart);
            //System.out.println("  - PMP: " + pathMatchPart);
            
            if (pathMatchPart instanceof AnyNoneOrMorePathMatchPart && previousWildcardAny == false) {
                //System.out.println("Matched: '?*', previousWildcardAny=false");
                previousWildcardAny = true;
                currentWildcardAnyIndex = sourceIndex;
                pathMatchPartsIndex += 1;
                if (pathMatchPartsIndex >= pathMatchParts.size() && previousWildcardAny == true) {
                    pathMatchPartsIndex -= 1;
                }
                pathMatchPart = pathMatchParts.get(pathMatchPartsIndex);
            }
            
            if (sourcePathPart instanceof KeyPathPart) {
                //System.out.println("SPP was KeyPathPart --> compare keys");
                if (pathMatchPart instanceof KeyPathMatchPart) {
                    //System.out.println("PMP was also KeyPathPart --> compare keys");
                    
                    String sourceKey = ((KeyPathPart) sourcePathPart).getKey();
                    String pathMatchKey = ((KeyPathMatchPart) pathMatchPart).getKey();
                    
                    //System.out.println("SPP key: " + sourceKey);
                    //System.out.println("PMP key: " + pathMatchKey);
                    if (sourceKey.equals(pathMatchKey)) {
                        //System.out.println("Keys match --> continue, sourceIndex = " + sourceIndex);
                        previousWildcardAny = false;
                        currentClearMatch = true;
                        continue;
                    }
                    else {
                        //System.out.println("SPP and PMP did not match: " + sourceKey + " != " + pathMatchKey);
                        //System.out.println("  pathMatchParts.size()=" + pathMatchParts.size());
                        //System.out.println("  sourcePathParts.size()=" + sourcePathParts.size());
                        if (previousWildcardAny && currentWildcardAnyIndex < pathMatchParts.size() - 1 && sourceIndex < sourcePathParts.size() - 1) {
                            //System.out.println("  >> Continue");
                            currentClearMatch = false;
                            continue;
                        }
                        return falseResult;
                    }
                }
                else if (pathMatchPart instanceof DynamicKeyPathMatchPart) {
                    //System.out.println("DynamicKeyPathMatchPart");
                    
                    String sourceKey = ((KeyPathPart) sourcePathPart).getKey();
                    DynamicKeyPathMatchPart dnKeyMatchPart = (DynamicKeyPathMatchPart) pathMatchPart;
                    Node dnKeyExpr = dnKeyMatchPart.getKeyExpr();
                    StringType sourceKeyValue = new StringType(stmt);
                    sourceKeyValue.setFromJavaString(sourceKey);
                    dnKeyExpr.getStatement().setCurrentValue(sourceKeyValue);
                    OperonValue dnKeyExprResult = dnKeyExpr.evaluate();
                    //System.out.println("PMP :: DynamicKeyPathMatchPart --> check match against result :: " + dnKeyExprResult);
                    if (dnKeyExprResult instanceof TrueType) {
                        //System.out.println("PMP :: DynamicKeyPathMatchPart --> match");
                        previousWildcardAny = false;
                        continue;
                    }
                    else if (dnKeyExprResult instanceof StringType) {
                        String matchStr = ((StringType) dnKeyExprResult).getJavaStringValue();
                        
                        //System.out.println("PMP :: DynamicKeyPathMatchPart --> str :: " + matchStr);
                        if (sourceKey.equals(matchStr)) {
                            previousWildcardAny = false;
                            currentClearMatch = true;
                            continue;
                        }
                        else {
                            if (previousWildcardAny && currentWildcardAnyIndex < pathMatchParts.size() - 1 && sourceIndex < sourcePathParts.size() - 1) {
                                //System.out.println("  >> Continue");
                                currentClearMatch = false;
                                continue;
                            }
                            return falseResult;
                        }
                    }
                    else {
                        //System.out.println("PMP :: DynamicKeyPathMatchPart --> NO MATCH!!! --> " + dnKeyExprResult.getClass().getName());
                        if (previousWildcardAny && currentWildcardAnyIndex < pathMatchParts.size() - 1 && sourceIndex < sourcePathParts.size() - 1) {
                            //System.out.println("  >> Continue");
                            currentClearMatch = false;
                            continue;
                        }
                        return falseResult;
                    }
                }
                else if (pathMatchPart instanceof AnySinglePathMatchPart) {
                    //System.out.println("Source=Key, matched: ?");
                    previousWildcardAny = false;
                    currentClearMatch = true; // '?' is counted as clearMatch
                    continue;
                }
                else if (pathMatchPart instanceof AnySingleOrMorePathMatchPart && previousWildcardAny == false) {
                    //System.out.println("Source=Pos, matched: '?+', previousWildcardAny=false");
                    previousWildcardAny = true;
                    currentWildcardAnyIndex = sourceIndex;
                    currentClearMatch = false;
                    continue;
                }
            }
            
            else if (sourcePathPart instanceof PosPathPart) {
                if (pathMatchPart instanceof FilterListPathMatchPart) {
                    boolean posResult = PathMatches.evaluatePosPathAgainstFilterListPathMatchPart(stmt, (PosPathPart) sourcePathPart, (FilterListPathMatchPart) pathMatchPart);
                    // Found:
                    if (posResult == true) {
                        previousWildcardAny = false;
                        currentClearMatch = true;
                        continue;
                    }
                    else {
                        if (previousWildcardAny && currentWildcardAnyIndex < pathMatchParts.size() - 1 && sourceIndex < sourcePathParts.size() - 1) {
                            //System.out.println("  >> Continue");
                            currentClearMatch = false;
                            continue;
                        }
                        return falseResult;
                    }
                }
                else if (pathMatchPart instanceof AnySinglePathMatchPart) {
                    //System.out.println("Source=Pos, matched: ?");
                    previousWildcardAny = false;
                    currentClearMatch = true; // '?' is counted as clearMatch
                    continue;
                }
                else if (pathMatchPart instanceof AnySingleOrMorePathMatchPart && previousWildcardAny == false) {
                    //System.out.println("Source=Pos, matched: '?+', previousWildcardAny=false");
                    previousWildcardAny = true;
                    currentWildcardAnyIndex = sourceIndex;
                    currentClearMatch = false;
                    continue;
                }
            }
            
            if (previousWildcardAny == true) {
                //System.out.println("Source=Any, matched: '?+' or '?*', previousWildcardAny=true, sourceIndex=" + sourceIndex + ", pathMatchParts.size()=" + pathMatchParts.size());
                if (currentWildcardAnyIndex < pathMatchParts.size() - 1 && sourceIndex < sourcePathParts.size() - 1) {
                    currentClearMatch = false;
                    continue;
                }
            }
            else {
                return falseResult;
            }
            
        }
        
        //System.out.println("Exited for:");
        
        //System.out.println("  - sourceIndex=" + sourceIndex);
        //System.out.println("  - sourcePath size=" + sourcePathParts.size());
        //System.out.println("  - currentClearMatch=" + currentClearMatch);
        //System.out.println("  - pathMatchPartsIndex=" + pathMatchPartsIndex);
        //System.out.println("  - pathMatchParts size=" + pathMatchParts.size());
        //System.out.println("  - pathMatchPart=" + pathMatchPart);
        
        //
        // There was more path matching parts remaining (excluding '?*'), but all the source path parts were consumed:
        //
        if (pathMatchParts.size() > sourcePathParts.size() 
                && sourceIndex >= pathMatchPartsIndex 
                && pathMatchParts.get(pathMatchPartsIndex + 1) instanceof AnyNoneOrMorePathMatchPart == false) {
            //System.out.println("1. Exit with false");
            //System.out.println("  - next pathMatchPart: " + pathMatchParts.get(pathMatchPartsIndex + 1));
            return falseResult;
        }

        //
        // There was more source matching parts remaining, but all the path match parts were consumed, and last was not wildcarded any (which could consume all the source parts):
        // Previous pathMatchPart was not wildcarded any
        if (pathMatchParts.size() < sourcePathParts.size() 
                && sourceIndex < sourcePathParts.size()
                && sourceIndex >= pathMatchPartsIndex 
                && pathMatchPart instanceof AnyNoneOrMorePathMatchPart == false
                && pathMatchPart instanceof AnySingleOrMorePathMatchPart == false
                && currentClearMatch == false
                //&& pathMatchParts.get(pathMatchPartsIndex - 1) instanceof AnySingleOrMorePathMatchPart == false
                //&& pathMatchParts.get(pathMatchPartsIndex - 1) instanceof AnyNoneOrMorePathMatchPart == false
            ) {
            //System.out.println("1.1 Exit with false");
            //System.out.println("  - pathMatchPart: " + pathMatchPart);
            return falseResult;
        }

        //
        // The last path matching part is a wildcarded any:
        //
        else if (pathMatchPart instanceof AnyNoneOrMorePathMatchPart ||
                 pathMatchPart instanceof AnySingleOrMorePathMatchPart) {
            //System.out.println("2. Exit with true");
            return trueResult;
        }
        
        // ~.c[1] ~= ?*.id
        //
        // Current path matching part did not match clearly, i.e. it was not wildcarded any,
        // but the previous path part was wildcarded any.
        //
        else if (previousWildcardAny && currentClearMatch == false) {
            //System.out.println("3. Exit with false");
            return falseResult;
        }

        //System.out.println("4. Exit with true");
        return trueResult;
    }
    
    public static boolean evaluatePosPathAgainstFilterListPathMatchPart(Statement stmt, PosPathPart sourcePathPart, FilterListPathMatchPart pathMatchPart) throws OperonGenericException {
        //System.out.println("evaluate the FilterListPathMatchPart");
        List<FilterListExpr> filterListExprList = ((FilterListPathMatchPart) pathMatchPart).getFilterList().getFilterExprList();
        boolean found = false;
        for (int flExprListIndex = 0; flExprListIndex < filterListExprList.size(); flExprListIndex ++) {
            FilterListExpr flExpr = filterListExprList.get(flExprListIndex);
            Node filterExpr = flExpr.getFilterExpr();
            int pos = ((PosPathPart) sourcePathPart).getPos();
            NumberType posNum = new NumberType(stmt);
            posNum.setDoubleValue((double) pos);
            posNum.setPrecision((byte) 0);
            
            // If expr is a splice or range, then it operates against an array 
            //System.out.println("EXPR TYPE == " + filterExpr.getClass().getName());
            
            if (filterExpr instanceof SpliceRight ||
               filterExpr instanceof SpliceLeft ||
               filterExpr instanceof SpliceRange ||
               filterExpr instanceof Range) {
                ArrayType inputArray = new ArrayType(stmt);
                inputArray.addValue(posNum);
                filterExpr.getStatement().setCurrentValue(inputArray);
            }
            else {
                filterExpr.getStatement().setCurrentValue(posNum);
            }
            OperonValue filterExprResult = filterExpr.evaluate();
            //System.out.println("filterExprResult :: " + filterExprResult);
            //System.out.println("    filterExprResult type :: " + filterExprResult.getClass().getName());
            
            if (filterExprResult instanceof Range) {
                Range range = (Range) filterExprResult;
                ArrayType inputArray = new ArrayType(stmt);
                inputArray.addValue(posNum);
                filterExprResult = Range.filterArray(inputArray, range);
            }
            
            if (filterExprResult instanceof NumberType) {
                int pathMatchIndex = (int) ((NumberType) filterExprResult).getDoubleValue();
                //System.out.println(">>> COMPARE NUMBERS :: " + pathMatchIndex + " == pos :: " + pos);
                if (pathMatchIndex == pos) {
                    //System.out.println(">>> MATCH");
                    found = true;
                    break;
                }
                else {
                    //System.out.println(">>> NO MATCH :: continue");
                    continue;
                }
            }
            else if (filterExprResult instanceof ArrayType) {
                if ( ((ArrayType) filterExprResult).getValues().size() > 0) {
                    found = true;
                    break;
                }
                else {
                    //System.out.println(">>> size was 0, continue to next filterExpr");
                    continue;
                }
            }
            else if (filterExprResult instanceof TrueType) {
                //System.out.println("filterExpr type = " + filterExprResult.getClass().getName());
                found = true;
                break;
            }
            else if (filterExprResult instanceof FalseType) {
                //System.out.println("filterExpr type = " + filterExprResult.getClass().getName());
                continue;
            }
        }
        //System.out.println(">>> found? " + found);
        return found;
    }
    
    public void setPathMatch(PathMatch pm) {
        this.pathMatch = pm;
    }
    
    public PathMatch getPathMatch() {
        return this.pathMatch;
    }

}