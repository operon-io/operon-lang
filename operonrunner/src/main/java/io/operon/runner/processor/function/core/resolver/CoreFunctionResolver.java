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

package io.operon.runner.processor.function.core.resolver;

import io.operon.runner.node.Node;
import io.operon.runner.node.type.ObjectType;
import io.operon.runner.processor.function.core.*;
import io.operon.runner.processor.function.core.array.*;
import io.operon.runner.processor.function.core.raw.*;
import io.operon.runner.processor.function.core.date.*;
import io.operon.runner.processor.function.core.env.*;
import io.operon.runner.processor.function.core.error.*;
import io.operon.runner.processor.function.core.function.*;
import io.operon.runner.processor.function.core.module.*;
import io.operon.runner.processor.function.core.math.*;
import io.operon.runner.processor.function.core.object.*;
import io.operon.runner.processor.function.core.path.*;
import io.operon.runner.processor.function.core.number.*;
import io.operon.runner.processor.function.core.bool.*;
import io.operon.runner.processor.function.core.resolver.*;
import io.operon.runner.processor.function.core.state.*;
import io.operon.runner.processor.function.core.string.*;

import io.operon.runner.statement.Statement;
import io.operon.runner.util.ErrorUtil;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.operon.runner.model.exception.OperonGenericException;

import org.apache.logging.log4j.LogManager;

public class CoreFunctionResolver {
     // no logger 
    
    public static boolean isCoreFunction(String functionNamespace, String functionName, List<Node> functionParams) {
        String fqFunctionName = functionNamespace + ":" + functionName + ":" + functionParams.size();
        return CoreFunctionResolver.isCoreFunction(fqFunctionName);
    }

    public static boolean isCoreFunction(String fqFunctionName) {
        
        // NOTE: for func:2 remember to set the currentValue between node-evaluations!
        //       Could add func2 interface to enforce this...
        
        if (fqFunctionName.equals("core:number:0") || fqFunctionName.equals("core:number:1") || fqFunctionName.equals(":number:0") || fqFunctionName.equals(":number:1")  // Cast
            || fqFunctionName.equals("core:array:0") || fqFunctionName.equals(":array:0") // Cast array
            || fqFunctionName.equals("core:object:0") || fqFunctionName.equals(":object:0") || fqFunctionName.equals("core:object:1") || fqFunctionName.equals(":object:1") // Cast object (array => object($groupBy?))
            || fqFunctionName.equals("core:string:0") || fqFunctionName.equals("core:string:1") || fqFunctionName.equals(":string:0") || fqFunctionName.equals(":string:1") // Cast
            || fqFunctionName.equals("core:boolean:0") || fqFunctionName.equals(":boolean:0") // Cast
            || fqFunctionName.equals("core:null:0") || fqFunctionName.equals(":null:0") // Cast
            || fqFunctionName.equals("core:raw:0") || fqFunctionName.equals(":raw:0") || fqFunctionName.equals("core:raw:1") || fqFunctionName.equals(":raw:1") // Cast
            
            // core (unbound)
            || fqFunctionName.equals("core:assert:1") || fqFunctionName.equals(":assert:1")
            || fqFunctionName.equals("core:copy:0") || fqFunctionName.equals(":copy:0")
            || fqFunctionName.equals("core:mock:1") || fqFunctionName.equals(":mock:1")
            || fqFunctionName.equals("core:wait:1") || fqFunctionName.equals(":wait:1")
            || fqFunctionName.equals("core:stop:0") || fqFunctionName.equals(":stop:0")
            || fqFunctionName.equals("core:type:0") || fqFunctionName.equals(":type:0")
            || fqFunctionName.equals("core:isString:0") || fqFunctionName.equals(":isString:0")
            || fqFunctionName.equals("core:isArray:0") || fqFunctionName.equals(":isArray:0")
            || fqFunctionName.equals("core:isObject:0") || fqFunctionName.equals(":isObject:0")
            || fqFunctionName.equals("core:isNull:0") || fqFunctionName.equals(":isNull:0")
            || fqFunctionName.equals("core:isEmpty:0") || fqFunctionName.equals(":isEmpty:0")
            || fqFunctionName.equals("core:isEmptyString:0") || fqFunctionName.equals(":isEmptyString:0")
            || fqFunctionName.equals("core:isEmptyArray:0") || fqFunctionName.equals(":isEmptyArray:0")
            || fqFunctionName.equals("core:isEmptyObject:0") || fqFunctionName.equals(":isEmptyObject:0")
            || fqFunctionName.equals("core:mappableTo:1") || fqFunctionName.equals(":mappableTo:1") || fqFunctionName.equals(":fullyMappableTo:1")
            || fqFunctionName.equals("core:uuid:0") || fqFunctionName.equals(":uuid:0")
            || fqFunctionName.equals("core:update:2") || fqFunctionName.equals(":update:2")
            || fqFunctionName.equals("core:durationToMillis:0") || fqFunctionName.equals(":durationToMillis:0")
            || fqFunctionName.equals("core:merge:1") || fqFunctionName.equals(":merge:1")
            || fqFunctionName.equals("core:previous:0") || fqFunctionName.equals(":previous:0")
            || fqFunctionName.equals("core:next:0") || fqFunctionName.equals(":next:0")
            
            // function
            || fqFunctionName.equals("core:function:name:0") || fqFunctionName.equals("function:name:0")
            || fqFunctionName.equals("core:function:fullName:0") || fqFunctionName.equals("function:fullName:0")
            || fqFunctionName.equals("core:function:namespace:0") || fqFunctionName.equals("function:namespace:0")
            || fqFunctionName.equals("core:function:list:0") || fqFunctionName.equals("function:list:0")
            
            // module
            || fqFunctionName.equals("core:module:list:0") || fqFunctionName.equals("module:list:0")
            || fqFunctionName.equals("core:module:add:1") || fqFunctionName.equals("module:add:1")
            || fqFunctionName.equals("core:module:addOrUpdate:1") || fqFunctionName.equals("module:addOrUpdate:1")
            || fqFunctionName.equals("core:module:remove:1") || fqFunctionName.equals("module:remove:1")
            
            // array
            || fqFunctionName.equals("core:array:count:0") || fqFunctionName.equals("array:count:0")
            || fqFunctionName.equals("core:array:first:0") || fqFunctionName.equals("core:array:first:1") || fqFunctionName.equals("array:first:0") || fqFunctionName.equals("array:first:1")
                || fqFunctionName.equals(":first:0") || fqFunctionName.equals(":first:1")
            || fqFunctionName.equals("core:array:last:0") || fqFunctionName.equals("core:array:last:1") || fqFunctionName.equals("array:last:0") || fqFunctionName.equals("array:last:1")
                || fqFunctionName.equals(":last:0") || fqFunctionName.equals(":last:1")
            || fqFunctionName.equals("core:array:get:1") || fqFunctionName.equals("array:get:1")
            || fqFunctionName.equals("core:array:sum:0") || fqFunctionName.equals(":sum:0") || fqFunctionName.equals("array:sum:0")
            || fqFunctionName.equals("core:array:min:0") || fqFunctionName.equals(":min:0") || fqFunctionName.equals("array:min:0") || fqFunctionName.equals("core:array:min:1") || fqFunctionName.equals(":min:1") || fqFunctionName.equals("array:min:1")
            || fqFunctionName.equals("core:array:max:0") || fqFunctionName.equals(":max:0") || fqFunctionName.equals("array:max:0") || fqFunctionName.equals("core:array:max:1") || fqFunctionName.equals(":max:1") || fqFunctionName.equals("array:max:1")
            || fqFunctionName.equals("core:array:avg:0") || fqFunctionName.equals(":avg:0") || fqFunctionName.equals("array:avg:0")
            || fqFunctionName.equals("core:array:distinct:0") || fqFunctionName.equals("array:distinct:0") || fqFunctionName.equals(":distinct:0")
            || fqFunctionName.equals("core:array:reduce:1") || fqFunctionName.equals("array:reduce:1") || fqFunctionName.equals(":reduce:1") || fqFunctionName.equals("core:array:reduce:2") || fqFunctionName.equals(":reduce:2") || fqFunctionName.equals("array:reduce:2")
            || fqFunctionName.equals("core:array:flatten:0") || fqFunctionName.equals("array:flatten:0") || fqFunctionName.equals(":flatten:0")
            || fqFunctionName.equals("core:array:forAll:1") || fqFunctionName.equals("array:forAll:1") || fqFunctionName.equals(":forAll:1")
            || fqFunctionName.equals("core:array:forAtLeast:2") || fqFunctionName.equals("array:forAtLeast:2") || fqFunctionName.equals(":forAtLeast:2")
            || fqFunctionName.equals("core:array:forAtMost:2") || fqFunctionName.equals("array:forAtMost:2") || fqFunctionName.equals(":forAtMost:2")
            || fqFunctionName.equals("core:array:forEach:1") || fqFunctionName.equals("array:forEach:1") || fqFunctionName.equals(":forEach:1")
            || fqFunctionName.equals("core:array:forEachPair:2") || fqFunctionName.equals("array:forEachPair:2") || fqFunctionName.equals(":forEachPair:2")
            || fqFunctionName.equals("core:array:contains:1") || fqFunctionName.equals("array:contains:1")
            || fqFunctionName.equals("core:array:groupBy:0") || fqFunctionName.equals("array:groupBy:0") || fqFunctionName.equals("core:array:groupBy:1") || fqFunctionName.equals("array:groupBy:1")
                || fqFunctionName.equals(":groupBy:0") || fqFunctionName.equals(":groupBy:1")
            || fqFunctionName.equals("core:array:search:1") || fqFunctionName.equals("array:search:1")
            || fqFunctionName.equals("core:array:sort:0") || fqFunctionName.equals("array:sort:0") || fqFunctionName.equals("core:array:sort:1") || fqFunctionName.equals("array:sort:1")
                || fqFunctionName.equals(":sort:0") || fqFunctionName.equals(":sort:1")
            || fqFunctionName.equals("core:array:reverseSort:0") || fqFunctionName.equals("array:reverseSort:0") || fqFunctionName.equals("core:array:reverseSort:1") 
                || fqFunctionName.equals("array:reverseSort:1") || fqFunctionName.equals(":reverseSort:0") || fqFunctionName.equals(":reverseSort:1")
            || fqFunctionName.equals("core:array:random:0") || fqFunctionName.equals("core:array:random:1") || fqFunctionName.equals("array:random:0") || fqFunctionName.equals("array:random:1")
            || fqFunctionName.equals("core:array:remove:1") || fqFunctionName.equals("array:remove:1")
            || fqFunctionName.equals("core:array:reverse:0") || fqFunctionName.equals("array:reverse:0") || fqFunctionName.equals(":reverse:0")
            || fqFunctionName.equals("core:array:rotate:1") || fqFunctionName.equals("array:rotate:1") || fqFunctionName.equals(":rotate:1")
            || fqFunctionName.equals("core:array:shuffle:0") || fqFunctionName.equals("array:shuffle:0") || fqFunctionName.equals(":shuffle:0")
            || fqFunctionName.equals("core:array:update:2") || fqFunctionName.equals("array:update:2") || fqFunctionName.equals("core:array:update:1") || fqFunctionName.equals("array:update:1")
            || fqFunctionName.equals("core:array:swap:2") || fqFunctionName.equals("array:swap:2") || fqFunctionName.equals(":swap:2")
            || fqFunctionName.equals("core:array:insertBefore:2") || fqFunctionName.equals("array:insertBefore:2") || fqFunctionName.equals(":insertBefore:2")
            || fqFunctionName.equals("core:array:toPath:0") || fqFunctionName.equals("array:toPath:0")
            
            // object
            || fqFunctionName.equals("core:object:count:0") || fqFunctionName.equals("object:count:0")
            || fqFunctionName.equals("core:object:key:0") || fqFunctionName.equals(":key:0") || fqFunctionName.equals("object:key:0")
                    || fqFunctionName.equals("core:object:key:1") || fqFunctionName.equals(":key:1") || fqFunctionName.equals("object:key:1")
            || fqFunctionName.equals("core:object:keys:0") || fqFunctionName.equals(":keys:0") || fqFunctionName.equals("object:keys:0")
            || fqFunctionName.equals("core:object:hasKey:1") || fqFunctionName.equals(":hasKey:1") || fqFunctionName.equals("object:hasKey:1")
            
            // Deprecated
            //|| fqFunctionName.equals("core:object:valueKey:0") || fqFunctionName.equals(":valueKey:0") || fqFunctionName.equals("object:valueKey:0")
            || fqFunctionName.equals("core:object:value:0") || fqFunctionName.equals("object:value:0")
                    || fqFunctionName.equals("core:object:value:1") || fqFunctionName.equals(":value:1") || fqFunctionName.equals("object:value:1")
            || fqFunctionName.equals("core:object:values:0") || fqFunctionName.equals(":values:0") || fqFunctionName.equals("object:values:0")
            || fqFunctionName.equals("core:object:random:0") || fqFunctionName.equals("core:object:random:1") || fqFunctionName.equals("object:random:0") || fqFunctionName.equals("object:random:1")
            || fqFunctionName.equals("core:object:remove:1") || fqFunctionName.equals("object:remove:1")
            || fqFunctionName.equals("core:object:rename:2") || fqFunctionName.equals("object:rename:2") || fqFunctionName.equals(":rename:2")
                || fqFunctionName.equals("core:object:rename:1") || fqFunctionName.equals("object:rename:1") || fqFunctionName.equals(":rename:1")
            || fqFunctionName.equals("core:object:update:1") || fqFunctionName.equals("object:update:1") || fqFunctionName.equals("core:object:update:2") || fqFunctionName.equals("object:update:2")
            || fqFunctionName.equals("core:object:upsert:2") || fqFunctionName.equals("object:upsert:2")
            || fqFunctionName.equals("core:object:createPair:2") || fqFunctionName.equals("object:createPair:2") || fqFunctionName.equals(":createPair:2")
            
            // path
            || fqFunctionName.equals("core:path:value:0") || fqFunctionName.equals("path:value:0")
            || fqFunctionName.equals("core:path:length:0") || fqFunctionName.equals("path:length:0")
            || fqFunctionName.equals("core:path:parentPath:0") || fqFunctionName.equals("path:parentPath:0")
            || fqFunctionName.equals("core:path:current:0") || fqFunctionName.equals("path:current:0")
            || fqFunctionName.equals("core:pos:0") || fqFunctionName.equals(":pos:0") || fqFunctionName.equals("core:path:pos:0") || fqFunctionName.equals("path:pos:0")
            || fqFunctionName.equals("core:path:next:0") || fqFunctionName.equals("path:next:0")
            || fqFunctionName.equals("core:path:previous:0") || fqFunctionName.equals("path:previous:0")
            || fqFunctionName.equals("core:path:rootValue:0") || fqFunctionName.equals("path:rootValue:0")
            || fqFunctionName.equals("core:path:linkRoot:1") || fqFunctionName.equals("path:linkRoot:1")
            || fqFunctionName.equals("core:path:create:1") || fqFunctionName.equals("path:create:1")
            || fqFunctionName.equals("core:path:subPath:1") || fqFunctionName.equals("path:subPath:1")
            || fqFunctionName.equals("core:path:commonSubPath:1") || fqFunctionName.equals("path:commonSubPath:1")
            || fqFunctionName.equals("core:path:parts:0") || fqFunctionName.equals("path:parts:0")
            || fqFunctionName.equals("core:path:retain:0") || fqFunctionName.equals("path:retain:0") || fqFunctionName.equals("core:path:retain:1") || fqFunctionName.equals("path:retain:1")
            || fqFunctionName.equals("core:path:reclude:0") || fqFunctionName.equals("path:reclude:0") || fqFunctionName.equals("core:path:reclude:1") || fqFunctionName.equals("path:reclude:1")
            || fqFunctionName.equals("core:path:setCurrent:0") || fqFunctionName.equals("path:setCurrent:0") || fqFunctionName.equals("core:path:setCurrent:1") || fqFunctionName.equals("path:setCurrent:1")
            || fqFunctionName.equals("core:path:diff:1") || fqFunctionName.equals("path:diff:1") || fqFunctionName.equals("core:path:diff:2") || fqFunctionName.equals("path:diff:2")
            || fqFunctionName.equals("core:path:same:1") || fqFunctionName.equals("path:same:1") || fqFunctionName.equals("core:path:same:2") || fqFunctionName.equals("path:same:2")
            
            // string
            || fqFunctionName.equals("core:string:length:0") || fqFunctionName.equals("string:length:0")
            || fqFunctionName.equals("core:string:trim:0") || fqFunctionName.equals(":trim:0") || fqFunctionName.equals("string:trim:0")
            || fqFunctionName.equals("core:string:strip:0") || fqFunctionName.equals(":strip:0") || fqFunctionName.equals("string:strip:0")
            || fqFunctionName.equals("core:string:stripLeft:0") || fqFunctionName.equals(":stripLeft:0") || fqFunctionName.equals("string:stripLeft:0")
            || fqFunctionName.equals("core:string:stripRight:0") || fqFunctionName.equals(":stripRight:0") || fqFunctionName.equals("string:stripRight:0")
            || fqFunctionName.equals("core:string:toCodePoints:0") || fqFunctionName.equals(":toCodePoints:0") || fqFunctionName.equals("string:toCodePoints:0")
            || fqFunctionName.equals("core:string:fromCodePoints:0") || fqFunctionName.equals(":fromCodePoints:0") || fqFunctionName.equals("string:fromCodePoints:0")
            || fqFunctionName.equals("core:string:upperCase:0") || fqFunctionName.equals(":upperCase:0") || fqFunctionName.equals("string:upperCase:0")
            || fqFunctionName.equals("core:string:lowerCase:0") || fqFunctionName.equals(":lowerCase:0") || fqFunctionName.equals("string:lowerCase:0")
            || fqFunctionName.equals("core:string:substring:2") || fqFunctionName.equals(":substring:2") || fqFunctionName.equals("string:substring:2")
            || fqFunctionName.equals("core:string:collect:1") || fqFunctionName.equals("string:collect:1")
            || fqFunctionName.equals("core:string:split:1") || fqFunctionName.equals(":split:1") || fqFunctionName.equals("string:split:1")
            || fqFunctionName.equals("core:string:splitBy:1") || fqFunctionName.equals(":splitBy:1") || fqFunctionName.equals("string:splitBy:1")
            || fqFunctionName.equals("core:string:startsWith:1") || fqFunctionName.equals(":startsWith:1") || fqFunctionName.equals("string:startsWith:1")
            || fqFunctionName.equals("core:string:endsWith:1") || fqFunctionName.equals(":endsWith:1") || fqFunctionName.equals("string:endsWith:1")
            || fqFunctionName.equals("core:string:contains:1") || fqFunctionName.equals(":contains:1") || fqFunctionName.equals("string:contains:1")
            || fqFunctionName.equals("core:string:matches:1") || fqFunctionName.equals(":matches:1") || fqFunctionName.equals("string:matches:1")
            || fqFunctionName.equals("core:string:organize:0") || fqFunctionName.equals("string:organize:0")
                || fqFunctionName.equals("core:string:organize:1") || fqFunctionName.equals("string:organize:1")
            || fqFunctionName.equals("core:string:search:1") || fqFunctionName.equals("string:search:1")
            || fqFunctionName.equals("core:string:random:0") || fqFunctionName.equals("core:string:random:1") || fqFunctionName.equals("string:random:0") || fqFunctionName.equals("string:random:1")
            || fqFunctionName.equals("core:string:repeat:1") || fqFunctionName.equals(":repeat:1") || fqFunctionName.equals("string:repeat:1")
            || fqFunctionName.equals("core:string:replaceFirst:2") || fqFunctionName.equals(":replaceFirst:2") || fqFunctionName.equals("string:replaceFirst:2")
            || fqFunctionName.equals("core:string:replaceAll:2") || fqFunctionName.equals(":replaceAll:2") || fqFunctionName.equals("string:replaceAll:2")
            || fqFunctionName.equals("core:string:padLeft:2") || fqFunctionName.equals(":padLeft:2") || fqFunctionName.equals("string:padLeft:2")
            || fqFunctionName.equals("core:string:padRight:2") || fqFunctionName.equals(":padRight:2") || fqFunctionName.equals("string:padRight:2")
            || fqFunctionName.equals("core:string:toBase64:0") || fqFunctionName.equals("string:toBase64:0") || fqFunctionName.equals("core:string:toBase64:1") || fqFunctionName.equals("string:toBase64:1")
            || fqFunctionName.equals("core:string:fromBase64:0") || fqFunctionName.equals("string:fromBase64:0") || fqFunctionName.equals("core:string:fromBase64:1") || fqFunctionName.equals("string:fromBase64:1")
            || fqFunctionName.equals("core:string:toRaw:0") || fqFunctionName.equals("string:toRaw:0")
            || fqFunctionName.equals("core:string:toPath:0") || fqFunctionName.equals("string:toPath:0")
            || fqFunctionName.equals("core:string:urlEncode:0") || fqFunctionName.equals("string:urlEncode:0") || fqFunctionName.equals(":urlEncode:0")
            || fqFunctionName.equals("core:string:urlDecode:0") || fqFunctionName.equals("string:urlDecode:0") || fqFunctionName.equals(":urlDecode:0")
            || fqFunctionName.equals("core:string:isNumeric:0") || fqFunctionName.equals("string:isNumeric:0") || fqFunctionName.equals(":isNumeric:0")
            
            || fqFunctionName.equals("core:number:random:0") || fqFunctionName.equals("number:random:0") || fqFunctionName.equals("number:random:1")
                || fqFunctionName.equals(":random:0") || fqFunctionName.equals(":random:1") || fqFunctionName.equals("core:number:random:1")
            
            || fqFunctionName.equals("core:boolean:random:0") || fqFunctionName.equals("boolean:random:0") || fqFunctionName.equals("core:boolean:random:1")
                || fqFunctionName.equals("boolean:random:1")
            
            // math
            || fqFunctionName.equals("core:math:abs:0") || fqFunctionName.equals("math:abs:0") || fqFunctionName.equals(":abs:0")
            || fqFunctionName.equals("core:math:ceil:0") || fqFunctionName.equals("math:ceil:0") || fqFunctionName.equals(":ceil:0")
            || fqFunctionName.equals("core:math:floor:0") || fqFunctionName.equals("math:floor:0") || fqFunctionName.equals(":floor:0")
            || fqFunctionName.equals("core:math:round:0") || fqFunctionName.equals("math:round:0") || fqFunctionName.equals(":round:0")
            || fqFunctionName.equals("core:math:sqrt:0") || fqFunctionName.equals("math:sqrt:0") || fqFunctionName.equals(":sqrt:0")
            || fqFunctionName.equals("core:math:exp:0") || fqFunctionName.equals("math:exp:0") || fqFunctionName.equals(":exp:0")
            || fqFunctionName.equals("core:math:pow:1") || fqFunctionName.equals("math:pow:1") || fqFunctionName.equals(":pow:1")
            || fqFunctionName.equals("core:math:log:0") || fqFunctionName.equals("math:log:0") || fqFunctionName.equals(":log:0")
            || fqFunctionName.equals("core:math:sin:0") || fqFunctionName.equals("math:sin:0") || fqFunctionName.equals(":sin:0")
            || fqFunctionName.equals("core:math:cos:0") || fqFunctionName.equals("math:cos:0") || fqFunctionName.equals(":cos:0")
            || fqFunctionName.equals("core:math:tan:0") || fqFunctionName.equals("math:tan:0") || fqFunctionName.equals(":tan:0")
            || fqFunctionName.equals("core:math:arcsin:0") || fqFunctionName.equals("math:arcsin:0") || fqFunctionName.equals(":arcsin:0")
            || fqFunctionName.equals("core:math:arccos:0") || fqFunctionName.equals("math:arccos:0") || fqFunctionName.equals(":arccos:0")
            || fqFunctionName.equals("core:math:arctan:0") || fqFunctionName.equals("math:arctan:0") || fqFunctionName.equals(":arctan:0")
            
            // date
            || fqFunctionName.equals("core:date:now:0") || fqFunctionName.equals(":now:0") || fqFunctionName.equals("date:now:0")
            || fqFunctionName.equals("core:date:add:1") || fqFunctionName.equals("core:date:add:2") || fqFunctionName.equals("core:date:add:3")
                || fqFunctionName.equals("date:add:1") || fqFunctionName.equals("date:add:2") || fqFunctionName.equals("date:add:3")
                || fqFunctionName.equals(":add:1") || fqFunctionName.equals(":add:2") || fqFunctionName.equals(":add:3")
            || fqFunctionName.equals("core:date:toString:1") || fqFunctionName.equals("date:toString:1")
            || fqFunctionName.equals("core:date:toMillis:0") || fqFunctionName.equals("date:toMillis:0") || fqFunctionName.equals(":toMillis:0")
            || fqFunctionName.equals("core:date:fromMillis:0") || fqFunctionName.equals("date:fromMillis:0") || fqFunctionName.equals(":fromMillis:0")
            || fqFunctionName.equals("core:date:fromString:1") || fqFunctionName.equals("date:fromString:1") || fqFunctionName.equals(":fromString:1")
            
            // env
            || fqFunctionName.equals("core:env:get:1") || fqFunctionName.equals("env:get:1")
            
            // error
            || fqFunctionName.equals("core:error:handled:1") || fqFunctionName.equals("error:handled:1") || fqFunctionName.equals(":handled:1")
            || fqFunctionName.equals("core:error:create:1") || fqFunctionName.equals("error:create:1")
            || fqFunctionName.equals("core:error:getCode:0") || fqFunctionName.equals("error:getCode:0") || fqFunctionName.equals(":getCode:0")
            || fqFunctionName.equals("core:error:getType:0") || fqFunctionName.equals("error:getType:0") || fqFunctionName.equals(":getType:0")
            || fqFunctionName.equals("core:error:getMessage:0") || fqFunctionName.equals("error:getMessage:0") || fqFunctionName.equals(":getMessage:0")
            || fqFunctionName.equals("core:error:getJson:0") || fqFunctionName.equals("error:getJson:0") || fqFunctionName.equals(":getJson:0")
            
            // state
            || fqFunctionName.equals("core:state:set:1") || fqFunctionName.equals("state:set:1")
            || fqFunctionName.equals("core:state:get:1") || fqFunctionName.equals("state:get:1") || fqFunctionName.equals("core:state:get:2") || fqFunctionName.equals("state:get:2")
            
            // raw
            || fqFunctionName.equals("core:raw:toBase64:0") || fqFunctionName.equals("raw:toBase64:0") || fqFunctionName.equals("core:raw:toBase64:1") || fqFunctionName.equals("raw:toBase64:1")
            || fqFunctionName.equals("core:raw:toJson:0") || fqFunctionName.equals("raw:toJson:0") || fqFunctionName.equals(":toJson:0")
            || fqFunctionName.equals("core:raw:evaluate:0") || fqFunctionName.equals("raw:evaluate:0") || fqFunctionName.equals(":evaluate:0")
                || fqFunctionName.equals("core:raw:evaluate:1") || fqFunctionName.equals("raw:evaluate:1") || fqFunctionName.equals(":evaluate:1")
            || fqFunctionName.equals("core:raw:toStream:0") || fqFunctionName.equals("raw:toStream:0") || fqFunctionName.equals(":toStream:0")
            || fqFunctionName.equals("core:raw:toString:0") || fqFunctionName.equals("raw:toString:0") || fqFunctionName.equals(":toString:0")
            || fqFunctionName.equals("core:raw:fromBase64:0") || fqFunctionName.equals("raw:fromBase64:0") || fqFunctionName.equals("core:raw:fromBase64:1") || fqFunctionName.equals("raw:fromBase64:1")
            
            // S-Raw
            || fqFunctionName.equals("core:raw:collect:1") || fqFunctionName.equals("raw:collect:1")
            || fqFunctionName.equals("core:raw:organize:0") || fqFunctionName.equals("raw:organize:0")
                || fqFunctionName.equals("core:raw:organize:1") || fqFunctionName.equals("raw:organize:1")
            || fqFunctionName.equals("core:raw:replaceFirst:2") || fqFunctionName.equals("raw:replaceFirst:2")
            || fqFunctionName.equals("core:raw:replaceAll:2") || fqFunctionName.equals("raw:replaceAll:2")
            
            || fqFunctionName.equals("core:stream:toRaw:0") || fqFunctionName.equals("stream:toRaw:0")
            
            //
            // Resolvers (with no namespace prefix):
            //
            || fqFunctionName.equals("core:count:0") || fqFunctionName.equals(":count:0")
            || fqFunctionName.equals("core:length:0") || fqFunctionName.equals(":length:0") // path:, string:
            || fqFunctionName.equals("core:parent:0") || fqFunctionName.equals(":parent:0")
            || fqFunctionName.equals("core:root:0") || fqFunctionName.equals(":root:0")
            || fqFunctionName.equals("core:spliceLeft:1") || fqFunctionName.equals(":spliceLeft:1")
            || fqFunctionName.equals("core:spliceRight:1") || fqFunctionName.equals(":spliceRight:1")
            || fqFunctionName.equals("core:spliceRange:2") || fqFunctionName.equals(":spliceRange:2")
            || fqFunctionName.equals("core:search:1") || fqFunctionName.equals(":search:1") // array:, string:
            || fqFunctionName.equals("core:toBase64:0") || fqFunctionName.equals(":toBase64:0") || fqFunctionName.equals("core:toBase64:1") || fqFunctionName.equals(":toBase64:1") // raw:, string:
            || fqFunctionName.equals("core:remove:1") || fqFunctionName.equals(":remove:1") // array:, object:
            || fqFunctionName.equals("core:update:1") || fqFunctionName.equals(":update:1") || fqFunctionName.equals("core:update:2") || fqFunctionName.equals(":update:2") // array:, object:
            || fqFunctionName.equals("core:value:0") || fqFunctionName.equals(":value:0") // path:, object:
            || fqFunctionName.equals("core:collect:1") || fqFunctionName.equals(":collect:1")
            || fqFunctionName.equals("core:organize:0") || fqFunctionName.equals(":organize:0") 
                || fqFunctionName.equals("core:organize:1") || fqFunctionName.equals(":organize:1")// input: (String | Raw), output: array of string.
            // 
            // Do not provide resolver for :get, because env:, state:
            // 
            //|| fqFunctionName.equals("core:get:1") || fqFunctionName.equals(":get:1") // array:, env:, state:
        ) { return true; }
        else return false;
    }

    public static Node getCoreFunction(String functionNamespace, 
                String functionName, List<Node> functionParams, Statement currentStatement) throws OperonGenericException {
        String fqFunctionName = functionNamespace + ":" + functionName;
        return getCoreFunction(fqFunctionName, functionParams, currentStatement);
    }

    public static Node getCoreFunction(String fqFunctionName, 
                List<Node> functionParams, Statement currentStatement) throws OperonGenericException {
        //System.out.println("getCoreFunction :: " + fqFunctionName);
        //:OFF:log.debug("getCoreFunction :: currentStatement :: " + currentStatement);
        
        if (fqFunctionName.equals("core:number") || fqFunctionName.equals(":number")) { return new CastNumber(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array") || fqFunctionName.equals(":array")) { return new CastArray(currentStatement); }
        else if (fqFunctionName.equals("core:object") || fqFunctionName.equals(":object")) { return new CastObject(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string") || fqFunctionName.equals(":string")) { return new CastString(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:boolean") || fqFunctionName.equals(":boolean")) { return new CastBoolean(currentStatement); }
        else if (fqFunctionName.equals("core:null") || fqFunctionName.equals(":null")) { return new CastNull(currentStatement); }
        else if (fqFunctionName.equals("core:raw") || fqFunctionName.equals(":raw")) { return new CastRaw(currentStatement, functionParams); }
        
        else if (fqFunctionName.equals("core:assert") || fqFunctionName.equals(":assert")) { return new Assert(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:copy") || fqFunctionName.equals(":copy")) { return new Copy(currentStatement); }
        else if (fqFunctionName.equals("core:mock") || fqFunctionName.equals(":mock")) { return new Mock(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:merge") || fqFunctionName.equals(":merge")) { return new Merge(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:wait") || fqFunctionName.equals(":wait")) { return new Wait(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:stop") || fqFunctionName.equals(":stop")) { return new Stop(currentStatement); }
        else if (fqFunctionName.equals("core:type") || fqFunctionName.equals(":type")) { return new OperonType(currentStatement); }
        else if (fqFunctionName.equals("core:isString") || fqFunctionName.equals(":isString")) { return new IsString(currentStatement); }
        else if (fqFunctionName.equals("core:isArray") || fqFunctionName.equals(":isArray")) { return new IsArray(currentStatement); }
        else if (fqFunctionName.equals("core:isObject") || fqFunctionName.equals(":isObject")) { return new IsObject(currentStatement); }
        else if (fqFunctionName.equals("core:isNull") || fqFunctionName.equals(":isNull")) { return new IsNull(currentStatement); }
        else if (fqFunctionName.equals("core:isEmpty") || fqFunctionName.equals(":isEmpty")) { return new IsEmpty(currentStatement); }
        else if (fqFunctionName.equals("core:isEmptyString") || fqFunctionName.equals(":isEmptyString")) { return new IsEmptyString(currentStatement); }
        else if (fqFunctionName.equals("core:isEmptyArray") || fqFunctionName.equals(":isEmptyArray")) { return new IsEmptyArray(currentStatement); }
        else if (fqFunctionName.equals("core:isEmptyObject") || fqFunctionName.equals(":isEmptyObject")) { return new IsEmptyObject(currentStatement); }
        else if (fqFunctionName.equals("core:uuid") || fqFunctionName.equals(":uuid")) { return new Uuid(currentStatement); }
        else if (fqFunctionName.equals("core:mappableTo") || fqFunctionName.equals(":mappableTo") || fqFunctionName.equals(":fullyMappableTo")) { return new MappableTo(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:update") || fqFunctionName.equals(":update")) { return new GenericUpdate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:durationToMillis") || fqFunctionName.equals(":durationToMillis")) { return new DurationToMillis(currentStatement); }
        else if (fqFunctionName.equals("core:previous") || fqFunctionName.equals(":previous")) { return new GenericPrevious(currentStatement); }
        else if (fqFunctionName.equals("core:next") || fqFunctionName.equals(":next")) { return new GenericNext(currentStatement); }
        else if (fqFunctionName.equals("core:parent") || fqFunctionName.equals(":parent")) { return new GenericParent(currentStatement); }
        
        // function
        else if (fqFunctionName.equals("core:function:name") || fqFunctionName.equals("function:name")) { return new FunctionName(currentStatement); }
        else if (fqFunctionName.equals("core:function:fullName") || fqFunctionName.equals("function:fullName")) { return new FunctionFullName(currentStatement); }
        else if (fqFunctionName.equals("core:function:namespace") || fqFunctionName.equals("function:namespace")) { return new FunctionNamespace(currentStatement); }
        else if (fqFunctionName.equals("core:function:list") || fqFunctionName.equals("function:list")) { return new FunctionList(currentStatement); }
        
        // module
        else if (fqFunctionName.equals("core:module:list") || fqFunctionName.equals("module:list")) { return new ModuleList(currentStatement); }
        else if (fqFunctionName.equals("core:module:add") || fqFunctionName.equals("module:add")) { return new ModuleAdd(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:module:addOrUpdate") || fqFunctionName.equals("module:addOrUpdate")) { return new ModuleAddOrUpdate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:module:remove") || fqFunctionName.equals("module:remove")) { return new ModuleRemove(currentStatement, functionParams); }
        
        // array
        else if (fqFunctionName.equals("core:array:count") || fqFunctionName.equals("array:count")) { return new ArrayCount(currentStatement); }
        else if (fqFunctionName.equals("core:array:first") || fqFunctionName.equals("array:first") || fqFunctionName.equals(":first")) { return new ArrayFirst(currentStatement, functionParams); } // TODO: create resolver for this
        else if (fqFunctionName.equals("core:array:last") || fqFunctionName.equals("array:last") || fqFunctionName.equals(":last")) { return new ArrayLast(currentStatement, functionParams); } // TODO: create resolver for this
        else if (fqFunctionName.equals("core:array:get") || fqFunctionName.equals("array:get")) { return new ArrayGet(currentStatement, functionParams); } // TODO: create resolver for this
        else if (fqFunctionName.equals("core:array:reduce") || fqFunctionName.equals(":reduce") || fqFunctionName.equals("array:reduce")) { return new ArrayReduce(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:forAll") || fqFunctionName.equals("array:forAll") || fqFunctionName.equals(":forAll")) { return new ArrayForAll(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:forAtLeast") || fqFunctionName.equals("array:forAtLeast") || fqFunctionName.equals(":forAtLeast")) { return new ArrayForAtLeast(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:forAtMost") || fqFunctionName.equals("array:forAtMost") || fqFunctionName.equals(":forAtMost")) { return new ArrayForAtMost(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:forEach") || fqFunctionName.equals("array:forEach") || fqFunctionName.equals(":forEach")) { return new ArrayForEach(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:forEachPair") || fqFunctionName.equals("array:forEachPair") || fqFunctionName.equals(":forEachPair")) { return new ArrayForEachPair(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:contains") || fqFunctionName.equals("array:contains")) { return new ArrayContains(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:groupBy") || fqFunctionName.equals("array:groupBy") || fqFunctionName.equals(":groupBy")) { return new ArrayGroupBy(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:search") || fqFunctionName.equals("array:search")) { return new ArraySearch(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:sort") || fqFunctionName.equals("array:sort") || fqFunctionName.equals(":sort")) { return new ArraySort(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:reverseSort") || fqFunctionName.equals("array:reverseSort") || fqFunctionName.equals(":reverseSort")) { return new ArrayReverseSort(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:sum") || fqFunctionName.equals("core:array:sum") || fqFunctionName.equals(":sum") || fqFunctionName.equals("array:sum")) { return new ArraySum(currentStatement); }
        else if (fqFunctionName.equals("core:min") || fqFunctionName.equals("core:array:min") || fqFunctionName.equals(":min") || fqFunctionName.equals("array:min")) { return new ArrayMin(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:max") || fqFunctionName.equals("core:array:max") || fqFunctionName.equals(":max") || fqFunctionName.equals("array:max")) { return new ArrayMax(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:avg") || fqFunctionName.equals("core:array:avg") || fqFunctionName.equals(":avg") || fqFunctionName.equals("array:avg")) { return new ArrayAvg(currentStatement); }
        else if (fqFunctionName.equals("core:distinct") || fqFunctionName.equals("core:array:distinct") || fqFunctionName.equals(":distinct") || fqFunctionName.equals("array:distinct")) { return new ArrayDistinct(currentStatement); }
        else if (fqFunctionName.equals("core:flatten") || fqFunctionName.equals("core:array:flatten") || fqFunctionName.equals(":flatten") || fqFunctionName.equals("array:flatten")) { return new ArrayFlatten(currentStatement); }
        else if (fqFunctionName.equals("core:array:random") || fqFunctionName.equals("array:random")) { return new ArrayRandom(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:remove") || fqFunctionName.equals("array:remove")) { return new ArrayRemove(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:rotate") || fqFunctionName.equals("array:rotate") || fqFunctionName.equals(":rotate")) { return new ArrayRotate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:reverse") || fqFunctionName.equals("array:reverse") || fqFunctionName.equals(":reverse")) { return new ArrayReverse(currentStatement); }
        else if (fqFunctionName.equals("core:array:shuffle") || fqFunctionName.equals("array:shuffle") || fqFunctionName.equals(":shuffle")) { return new ArrayShuffle(currentStatement); }
        else if (fqFunctionName.equals("core:array:update") || fqFunctionName.equals("array:update")) { return new ArrayUpdate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:swap") || fqFunctionName.equals("array:swap") || fqFunctionName.equals(":swap")) { return new ArraySwap(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:insertBefore") || fqFunctionName.equals("array:insertBefore") || fqFunctionName.equals(":insertBefore")) { return new ArrayInsertBefore(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:array:toPath") || fqFunctionName.equals("array:toPath")) { return new ArrayToPath(currentStatement); }

        // object
        else if (fqFunctionName.equals("core:object:count") || fqFunctionName.equals("object:count")) { return new ObjectCount(currentStatement); }
        else if (fqFunctionName.equals("core:object:key") || fqFunctionName.equals("core:key") || fqFunctionName.equals(":key") || fqFunctionName.equals("object:key")) { return new ObjectKey(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:object:keys") || fqFunctionName.equals("core:keys") || fqFunctionName.equals(":keys") || fqFunctionName.equals("object:keys")) { return new ObjectKeys(currentStatement); }
        else if (fqFunctionName.equals("core:object:hasKey") || fqFunctionName.equals("core:hasKey") || fqFunctionName.equals(":hasKey") || fqFunctionName.equals("object:hasKey")) { return new ObjectHasKey(currentStatement, functionParams); }
        
        // Deprecated
        //else if (fqFunctionName.equals("core:object:valueKey") || fqFunctionName.equals("core:valueKey") || fqFunctionName.equals(":valueKey") || fqFunctionName.equals("object:valueKey")) { return new ObjectValueKey(currentStatement); }
        else if (fqFunctionName.equals("core:object:value") || fqFunctionName.equals("object:value")) { return new ObjectValue(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:object:values") || fqFunctionName.equals("core:values") || fqFunctionName.equals(":values") || fqFunctionName.equals("object:values")) { return new ObjectValues(currentStatement); }
        else if (fqFunctionName.equals("core:object:random") || fqFunctionName.equals("object:random")) { return new ObjectRandom(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:object:remove") || fqFunctionName.equals("object:remove")) { return new ObjectRemove(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:object:rename") || fqFunctionName.equals("object:rename") || fqFunctionName.equals(":rename")) { return new ObjectRename(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:object:update") || fqFunctionName.equals("object:update")) { return new ObjectUpdate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:object:upsert") || fqFunctionName.equals("object:upsert")) { return new ObjectUpsert(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:object:createPair") || fqFunctionName.equals("object:createPair") || fqFunctionName.equals(":createPair")) { return new ObjectCreatePair(currentStatement, functionParams); }
        
        // path
        else if (fqFunctionName.equals("core:path:value") || fqFunctionName.equals("path:value")) { return new PathValue(currentStatement); }
        else if (fqFunctionName.equals("core:path:length") || fqFunctionName.equals("path:length")) { return new PathLength(currentStatement); }
        else if (fqFunctionName.equals("core:path:parentPath") || fqFunctionName.equals("path:parentPath")) { return new PathParentPath(currentStatement); }
        else if (fqFunctionName.equals("core:path:current") || fqFunctionName.equals("path:current")) { return new PathCurrent(currentStatement); }
        else if (fqFunctionName.equals(":pos") || fqFunctionName.equals("core:pos") || fqFunctionName.equals("core:path:pos") || fqFunctionName.equals("path:pos")) { return new PathPos(currentStatement); }
        else if (fqFunctionName.equals("core:path:next") || fqFunctionName.equals("path:next")) { return new PathNext(currentStatement); }
        else if (fqFunctionName.equals("core:path:previous") || fqFunctionName.equals("path:previous")) { return new PathPrevious(currentStatement); }
        else if (fqFunctionName.equals("core:path:rootValue") || fqFunctionName.equals("path:rootValue")) { return new PathRootValue(currentStatement); }
        else if (fqFunctionName.equals("core:path:linkRoot") || fqFunctionName.equals("path:linkRoot")) { return new PathLinkRoot(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:create") || fqFunctionName.equals("path:create")) { return new PathCreate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:subPath") || fqFunctionName.equals("path:subPath")) { return new PathSubPath(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:commonSubPath") || fqFunctionName.equals("path:commonSubPath")) { return new PathCommonSubPath(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:parts") || fqFunctionName.equals("path:parts")) { return new PathParts(currentStatement); }
        else if (fqFunctionName.equals("core:path:retain") || fqFunctionName.equals("path:retain")) { return new PathRetain(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:reclude") || fqFunctionName.equals("path:reclude")) { return new PathReclude(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:setCurrent") || fqFunctionName.equals("path:setCurrent")) { return new PathSetCurrent(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:diff") || fqFunctionName.equals("path:diff")) { return new PathDiff(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:path:same") || fqFunctionName.equals("path:same")) { return new PathSame(currentStatement, functionParams); }
        
        // string
        else if (fqFunctionName.equals("core:string:length") || fqFunctionName.equals("string:length")) { return new StringLength(currentStatement); }
        else if (fqFunctionName.equals("core:string:trim") || fqFunctionName.equals(":trim") || fqFunctionName.equals("string:trim")) { return new StringTrim(currentStatement); }
        else if (fqFunctionName.equals("core:string:strip") || fqFunctionName.equals(":strip") || fqFunctionName.equals("string:strip")) { return new StringStrip(currentStatement); }
        else if (fqFunctionName.equals("core:string:stripLeft") || fqFunctionName.equals(":stripLeft") || fqFunctionName.equals("string:stripLeft")) { return new StringStripLeft(currentStatement); }
        else if (fqFunctionName.equals("core:string:stripRight") || fqFunctionName.equals(":stripRight") || fqFunctionName.equals("string:stripRight")) { return new StringStripRight(currentStatement); }
        else if (fqFunctionName.equals("core:string:toCodePoints") || fqFunctionName.equals(":toCodePoints") || fqFunctionName.equals("string:toCodePoints")) { return new StringToCodePoints(currentStatement); }
        else if (fqFunctionName.equals("core:string:fromCodePoints") || fqFunctionName.equals(":fromCodePoints") || fqFunctionName.equals("string:fromCodePoints")) { return new StringFromCodePoints(currentStatement); }
        else if (fqFunctionName.equals("core:string:upperCase") || fqFunctionName.equals(":upperCase") || fqFunctionName.equals("string:upperCase")) { return new StringUpperCase(currentStatement); }
        else if (fqFunctionName.equals("core:string:lowerCase") || fqFunctionName.equals(":lowerCase") || fqFunctionName.equals("string:lowerCase")) { return new StringLowerCase(currentStatement); }
        else if (fqFunctionName.equals("core:string:substring") || fqFunctionName.equals(":substring") || fqFunctionName.equals("string:substring")) { return new StringSubstring(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:collect") || fqFunctionName.equals("string:collect")) { return new StringCollect(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:split") || fqFunctionName.equals(":split") || fqFunctionName.equals("string:split")) { return new StringSplit(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:splitBy") || fqFunctionName.equals(":splitBy") || fqFunctionName.equals("string:splitBy")) { return new StringSplitBy(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:startsWith") || fqFunctionName.equals(":startsWith") || fqFunctionName.equals("string:startsWith")) { return new StringStartsWith(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:endsWith") || fqFunctionName.equals(":endsWith") || fqFunctionName.equals("string:endsWith")) { return new StringEndsWith(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:contains") || fqFunctionName.equals(":contains") || fqFunctionName.equals("string:contains")) { return new StringContains(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:matches") || fqFunctionName.equals(":matches") || fqFunctionName.equals("string:matches")) { return new StringMatches(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:search") || fqFunctionName.equals("string:search")) { return new StringSearch(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:organize") || fqFunctionName.equals("string:organize")) { return new StringOrganize(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:random") || fqFunctionName.equals("string:random")) { return new StringRandom(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:repeat") || fqFunctionName.equals(":repeat") || fqFunctionName.equals("string:repeat")) { return new StringRepeat(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:replaceFirst") || fqFunctionName.equals(":replaceFirst") || fqFunctionName.equals("string:replaceFirst")) { return new StringReplaceFirst(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:replaceAll") || fqFunctionName.equals(":replaceAll") || fqFunctionName.equals("string:replaceAll")) { return new StringReplaceAll(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:padLeft") || fqFunctionName.equals(":padLeft") || fqFunctionName.equals("string:padLeft")) { return new StringPadLeft(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:padRight") || fqFunctionName.equals(":padRight") || fqFunctionName.equals("string:padRight")) { return new StringPadRight(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:toBase64") || fqFunctionName.equals("string:toBase64")) { return new StringToBase64(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:fromBase64") || fqFunctionName.equals("string:fromBase64")) { return new StringFromBase64(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:string:toRaw") || fqFunctionName.equals("string:toRaw")) { return new StringToRaw(currentStatement); }
        else if (fqFunctionName.equals("core:string:toPath") || fqFunctionName.equals("string:toPath")) { return new StringToPath(currentStatement); }
        else if (fqFunctionName.equals("core:string:urlEncode") || fqFunctionName.equals("string:urlEncode") || fqFunctionName.equals(":urlEncode")) { return new StringUrlEncode(currentStatement); }
        else if (fqFunctionName.equals("core:string:urlDecode") || fqFunctionName.equals("string:urlDecode") || fqFunctionName.equals(":urlDecode")) { return new StringUrlDecode(currentStatement); }
        else if (fqFunctionName.equals("core:string:isNumeric") || fqFunctionName.equals(":isNumeric") || fqFunctionName.equals("string:isNumeric")) { return new StringIsNumeric(currentStatement); }
        
        else if (fqFunctionName.equals("core:number:random") || fqFunctionName.equals("number:random") || fqFunctionName.equals(":random")) { return new NumberRandom(currentStatement, functionParams); }
        
        else if (fqFunctionName.equals("core:boolean:random") || fqFunctionName.equals("boolean:random")) { return new BooleanRandom(currentStatement, functionParams); }
        
        else if (fqFunctionName.equals("core:math:abs") || fqFunctionName.equals("math:abs")) { return new MathAbs(currentStatement); }
        else if (fqFunctionName.equals("core:math:ceil") || fqFunctionName.equals("math:ceil")) { return new MathCeil(currentStatement); }
        else if (fqFunctionName.equals("core:math:floor") || fqFunctionName.equals("math:floor")) { return new MathFloor(currentStatement); }
        else if (fqFunctionName.equals("core:math:round") || fqFunctionName.equals("math:round")) { return new MathRound(currentStatement); }
        else if (fqFunctionName.equals("core:math:sqrt") || fqFunctionName.equals("math:sqrt")) { return new MathSqrt(currentStatement); }
        else if (fqFunctionName.equals("core:math:exp") || fqFunctionName.equals("math:exp")) { return new MathExp(currentStatement); }
        else if (fqFunctionName.equals("core:math:pow") || fqFunctionName.equals("math:pow")) { return new MathPow(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:math:log") || fqFunctionName.equals("math:log")) { return new MathLog(currentStatement); }
        else if (fqFunctionName.equals("core:math:sin") || fqFunctionName.equals("math:sin")) { return new MathSin(currentStatement); }
        else if (fqFunctionName.equals("core:math:cos") || fqFunctionName.equals("math:cos")) { return new MathCos(currentStatement); }
        else if (fqFunctionName.equals("core:math:tan") || fqFunctionName.equals("math:tan")) { return new MathTan(currentStatement); }
        else if (fqFunctionName.equals("core:math:arcsin") || fqFunctionName.equals("math:arcsin")) { return new MathASin(currentStatement); }
        else if (fqFunctionName.equals("core:math:arccos") || fqFunctionName.equals("math:arccos")) { return new MathACos(currentStatement); }
        else if (fqFunctionName.equals("core:math:arctan") || fqFunctionName.equals("math:arctan")) { return new MathATan(currentStatement); }
        
        else if (fqFunctionName.equals("core:date:now") || fqFunctionName.equals("date:now") || fqFunctionName.equals(":now")) { return new DateNow(currentStatement); }
        else if (fqFunctionName.equals("core:date:add") || fqFunctionName.equals("date:add") || fqFunctionName.equals(":add")) { return new DateAdd(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:date:toString") || fqFunctionName.equals("date:toString")) { return new DateToString(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:date:toMillis") || fqFunctionName.equals("date:toMillis") || fqFunctionName.equals(":toMillis")) { return new DateToMillis(currentStatement); }
        else if (fqFunctionName.equals("core:date:fromMillis") || fqFunctionName.equals("date:fromMillis") || fqFunctionName.equals(":fromMillis")) { return new DateFromMillis(currentStatement); }
        else if (fqFunctionName.equals("core:date:fromString") || fqFunctionName.equals("date:fromString") || fqFunctionName.equals(":fromString")) { return new DateFromString(currentStatement, functionParams); }
        
        else if (fqFunctionName.equals("core:env:get") || fqFunctionName.equals("env:get")) { return new EnvGet(currentStatement, functionParams); }
        
        else if (fqFunctionName.equals("core:error:handled") || fqFunctionName.equals("error:handled") || fqFunctionName.equals(":handled")) { return new ErrorHandled(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:error:create") || fqFunctionName.equals("error:create")) { return new ErrorCreate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:error:getCode") || fqFunctionName.equals("error:getCode") || fqFunctionName.equals(":getCode")) { return new ErrorGetCode(currentStatement); }
        else if (fqFunctionName.equals("core:error:getType") || fqFunctionName.equals("error:getType") || fqFunctionName.equals(":getType")) { return new ErrorGetType(currentStatement); }
        else if (fqFunctionName.equals("core:error:getMessage") || fqFunctionName.equals("error:getMessage") || fqFunctionName.equals(":getMessage")) { return new ErrorGetMessage(currentStatement); }
        else if (fqFunctionName.equals("core:error:getJson") || fqFunctionName.equals("error:getJson") || fqFunctionName.equals(":getJson")) { return new ErrorGetJson(currentStatement); }
        
        else if (fqFunctionName.equals("core:state:set") || fqFunctionName.equals("state:set")) { return new StateSet(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:state:get") || fqFunctionName.equals("state:get")) { return new StateGet(currentStatement, functionParams); }
        
        // raw
        else if (fqFunctionName.equals("core:raw:toBase64") || fqFunctionName.equals("raw:toBase64")) { return new RawToBase64(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:raw:toJson") || fqFunctionName.equals("raw:toJson") || fqFunctionName.equals(":toJson")) { return new RawToJson(currentStatement); }
        else if (fqFunctionName.equals("core:raw:evaluate") || fqFunctionName.equals("raw:evaluate") || fqFunctionName.equals(":evaluate")) { return new RawEvaluate(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:raw:toStream") || fqFunctionName.equals("raw:toStream") || fqFunctionName.equals(":toStream")) { return new RawToStream(currentStatement); }
        else if (fqFunctionName.equals("core:raw:toString") || fqFunctionName.equals("raw:toString") || fqFunctionName.equals(":toString")) { return new RawToStringType(currentStatement); }
        else if (fqFunctionName.equals("core:raw:fromBase64") || fqFunctionName.equals("raw:fromBase64")) { return new RawFromBase64(currentStatement, functionParams); }
        
        // S-Raw
        else if (fqFunctionName.equals("core:raw:collect") || fqFunctionName.equals("raw:collect")) { return new SRawCollect(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:raw:organize") || fqFunctionName.equals("raw:organize")) { return new SRawOrganize(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:raw:replaceFirst") || fqFunctionName.equals("raw:replaceFirst")) { return new SRawReplaceFirst(currentStatement, functionParams); }
        else if (fqFunctionName.equals("core:raw:replaceAll") || fqFunctionName.equals("raw:replaceAll")) { return new SRawReplaceAll(currentStatement, functionParams); }
        
        else if (fqFunctionName.equals("core:stream:toRaw") || fqFunctionName.equals("stream:toRaw")) { return new StreamToRaw(currentStatement); }
        
        //
        // Resolvers for functions that match more than one input-type:
        //
        else if (fqFunctionName.equals("core:count") || fqFunctionName.equals(":count")) { return new GenericResolver(currentStatement, "count"); }
        else if (fqFunctionName.equals("core:length") || fqFunctionName.equals(":length")) { return new GenericResolver(currentStatement, "length"); }
        else if (fqFunctionName.equals("core:root") || fqFunctionName.equals(":root")) { return new GenericResolver(currentStatement, "root"); }
        
        else if (fqFunctionName.equals("core:spliceLeft") || fqFunctionName.equals(":spliceLeft")) { return new GenericResolver(currentStatement, "spliceLeft", functionParams); }
        else if (fqFunctionName.equals("core:spliceRight") || fqFunctionName.equals(":spliceRight")) { return new GenericResolver(currentStatement, "spliceRight", functionParams); }
        else if (fqFunctionName.equals("core:spliceRange") || fqFunctionName.equals(":spliceRange")) { return new GenericResolver(currentStatement, "spliceRange", functionParams); }
        else if (fqFunctionName.equals("core:search") || fqFunctionName.equals(":search")) { return new GenericResolver(currentStatement, "search", functionParams); }
        else if (fqFunctionName.equals("core:toBase64") || fqFunctionName.equals(":toBase64")) { return new GenericResolver(currentStatement, "toBase64", functionParams); }
        else if (fqFunctionName.equals("core:remove") || fqFunctionName.equals(":remove")) { return new GenericResolver(currentStatement, "remove", functionParams); }
        
        // NOTE core:update / :update is handled by GenericUpdate
        
        else if (fqFunctionName.equals("core:value") || fqFunctionName.equals(":value")) { return new GenericResolver(currentStatement, "value", functionParams); }
        else if (fqFunctionName.equals("core:organize") || fqFunctionName.equals(":organize")) { return new GenericResolver(currentStatement, "organize", functionParams); }
        else if (fqFunctionName.equals("core:collect") || fqFunctionName.equals(":collect")) { return new GenericResolver(currentStatement, "collect", functionParams); }
        
        return ErrorUtil.createErrorValueAndThrow(currentStatement, "FUNCTION", "MISSING", "Could not resolve core-function: " + fqFunctionName);
    }

}
