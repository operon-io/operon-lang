grammar OperonBase;

// 
// License: https://operon.io/operon-license
// 

//
// object key: namespace to be imported
// value: the URI to be imported
//
// Example: Import {mylib:"file://op/lib.opm"}
//
import_stmt
    : 'Import' json_obj
    ;

from
    : root_input_source (END | END_MARK)?
    ;

// The first json_value_constraint is the constraint for the input-value (i.e. the @-value which is sent to function)
//   FIXME: this input-constraint should be named so that we can point it in the compiler!
function_stmt
    : json_value_constraint? 'Function' (ID ':')? ID '(' function_stmt_param? (',' function_stmt_param?)* ')' json_value_constraint? ':' exception_stmt? let_stmt* expr (END | END_MARK | 'End:Function')
    ;

// pattern_configs (i.e. json_obj after value name) controls:
//  "update": LetStatement.ResetType
//  "bind": bind-configuration
// NOTE: using '(' json_obj ')' instead of pattern_configs, since pattern_configs caused recognizing problem
let_stmt
    : LET? (CONST_ID | ID ':' CONST_ID) json_obj? json_value_constraint? ':' exception_stmt? expr (END | END_MARK | 'End:Let')
    ;

select
    : 'Select'? json_obj? json_value_constraint? ':' exception_stmt? let_stmt* expr (END | END_MARK | 'End:Select')?
    ;
    
exception_stmt
    : 'HandleError' ':' let_stmt* expr (END | END_MARK | 'End:HandleError')
    ;

expr
    : (json                         continuation*
    | assign_expr                   continuation_with_curry*
    | computed_value_ref            continuation_with_curry*
    | value_ref                     continuation_with_curry*
    | obj_access                    continuation*
    | obj_dynamic_access            continuation*
    | obj_deep_scan                 continuation*
    | obj_dynamic_deep_scan         continuation*
    | lambda_function_call          continuation*
    | lambda_function_ref
    | auto_invoke_ref
    | function_call                 continuation*
    | function_ref
    | function_ref_invoke           continuation*
    | function_ref_invoke_full      continuation*
    | operon_type_function_shortcut continuation*
    | io_call                       continuation*
    | choice                        continuation*
    | map_expr                      continuation*
    | filter_full_expr              continuation*
    | where_expr                    continuation*
    | path_matches
    | obj_update_expr               continuation*
    | update_expr                   continuation*
    | build_expr                    continuation*
    | update_array_expr             continuation*
    | loop_expr                     continuation*
    | do_while_expr                 continuation*
    | while_expr                    continuation*
    | try_catch                     continuation*
    | parentheses_expr              continuation*
    | of_expr                       continuation*
    | throw_exception
    | aggregate_expr                continuation*
    | flow_break
    | break_loop
    | continue_loop
    )+
    //
    // Do not reorder these, e.g (PLUS | MINUS | MULT | DIV | MOD)! These must be in correct order for calculation rules.
    //
    | (MINUS | NEGATE) expr
    | NOT expr
    | expr NOT expr
    | <assoc=right> expr POW expr
    | expr MULT expr
    | expr DIV expr
    | expr MOD expr
    | expr (PLUS | MINUS) expr
    | expr (EQ | LT | GT | LTE | GTE | IEQ) expr
    | expr (AND | OR) expr
    ;

continuation
    : (filter_expr | obj_access | obj_dynamic_access | obj_deep_scan | obj_dynamic_deep_scan)
    ;

continuation_with_curry
    : (filter_expr | obj_access | obj_dynamic_access | obj_deep_scan | obj_dynamic_deep_scan | function_ref_curry)
    ;

flow_break
    : '|' expr (';' | 'End')?
    ;

try_catch
    : 'Try' (':' | pattern_configs)? expr 'Catch' expr (END | END_MARK | 'End:Try')
    ;

assign_expr
    : ('Assign')? (ID ':')* CONST_ID ':=' expr (END | END_MARK | 'End:Assign')
    ;

aggregate_expr
    : 'Aggregate' json_obj
    ;

parentheses_expr
    : '(' expr ')'
    ;

//
// {foo: 123}.foo
//
obj_access
    : OBJ_ACCESSOR (ID)
    ;

//
// $..foo
//
obj_deep_scan
    : OBJ_DEEP_SCAN ID
    ;

//
// {foo: 123}.(@ => startsWith("f"))
// {foo: 123}.{throwOnEmpty}("foo")
//
obj_dynamic_access
    : OBJ_ACCESSOR pattern_configs? '(' expr ')'
    ;

//
// {foo, bar, baz: {foo}}..(=> key() => startsWith("f"))
//
obj_dynamic_deep_scan
    : OBJ_DEEP_SCAN pattern_configs? '(' expr ')'
    ;

//
// NOTE: with pattern_configs we use the (':' | pattern_configs)? to ensure that parser gets this correctly.
//
map_expr
    : 'Map' (':' | pattern_configs)? let_stmt* expr (END | END_MARK | 'End:Map')
    ;

//
// "Of" is used for advising that Array contains certain type of data.
//  Example: Select: .foo Of <String>
//
of_expr
    : 'Of' '<' operon_type_function_shortcut '>'
    ;

//
// Used in different patterns (map_expr, invoke_full, filter...)
//
//
// NOTE: with pattern_configs we use the (':' | pattern_configs)? to ensure that parser gets this correctly.
//
pattern_configs
    : json_obj ':'
    ;

//
// This is used to match Array or Object against path-matching predicate-expr.
// The value that is injected for expr is a Path from the source-structure,
// with linked value. When expr returns true, the path is collected into an
// array that is returned as a result.
// 
// Example:
//   Select: [{"foo": "bar"}, 123]
//   Where ~=([1, 3::].foo):
//       @ => path:value() = "bar"
//   End:Where
// #> [Path([1].foo)]
//
where_expr
    : 'Where' pattern_configs? path_matches (':' expr)? (END | END_MARK | 'End:Where')
    ;

//
// Predicate-expr, input is a Path. Returns boolean.
//
path_matches
   : ('PathMatches' | '~=') '(' ('?' | '?+' | '?*' | ('.' ID) | dynamic_key_match_part | ('[' filter_list ']'))+ ')'    // with parentheses
   | ('PathMatches' | '~=') ('?' | '?+' | '?*' | ('.' ID) | dynamic_key_match_part | ('[' filter_list ']'))+            // without parentheses
   ;

dynamic_key_match_part
    : '.' '(' expr ')'
    ;

obj_update_expr
    : ('Update' | '<<') (':' | pattern_configs)? json_obj+ (END | END_MARK | 'End:Update')
    ;

update_expr
    : ('Update' | '<<') (':' | pattern_configs)? (path_value ':' expr)? (',' path_value ':' expr)* (END | END_MARK | 'End:Update')
    ;

build_expr
    : ('Build' | '>>') (':' | pattern_configs)?
    ;

// Update [Path(), Path()]: expr End
// (i.e. update multiple Paths with single value)
//   [...]
//    \|/
//     A
//
// Update [Path(), Path()]: [expr] End
//  (i.e. update multiple Paths with corresponding multiple values.)
//   [...]
//    |||
//   [ABC]
//
// Update [[Path(), Path()]]: [expr, expr] End
//  (i.e. update multiple Paths defined in multiple subarrays, with multiple corresponding values.)
//   [ [...], [...], [...]] (Paths)
//      \|/    \|/    \|/
//   [   A      B      C  ] (Update-values)
//
//
// NOTE: the first expr must evaluate into Array.
update_array_expr
    : ('Update' | '<<') (':' | pattern_configs)? expr ':' expr (END | END_MARK | 'End:Update')
    ;

//
// Select 0 Loop ($i : 1...3): @ + $i;
// #> 6 
// NOTE: the expr for CONST_ID must be iterable.
//
loop_expr
    : 'Loop' '(' CONST_ID ':' (expr | range_expr) ')' ':' let_stmt* expr (END | END_MARK | 'End:Loop')
    ;

do_while_expr
    : 'Do' (':')? let_stmt* expr 'While' '(' expr ')' (END | END_MARK | 'End:Do')
    ;

while_expr
    : 'While' '(' expr ')' ':' let_stmt* expr (END | END_MARK | 'End:While')
    ;

break_loop
    : 'Break'
    ;

continue_loop
    : 'Continue'
    ;

choice
    : ('Choice' let_stmt*)? ('When' let_stmt* expr ('Then' | ':') let_stmt* expr (END | END_MARK | 'End:When'))+ ('Otherwise' let_stmt* expr)? (END | END_MARK | 'End:Choice')
    ;

filter_full_expr
    : 'Filter' (':' | pattern_configs)? '[' filter_list ']' (END | END_MARK | 'End:Filter')
    ;

//
// NOTE: do not use the full pattern_configs here (i.e. (':' | pattern_configs)? ),
// because parser will confuse this with e.g. invoke -pattern.
//
filter_expr
    : pattern_configs? '[' filter_list ']'
    ;

filter_list
    : filter_list_expr | ( filter_list_expr? (',' filter_list_expr )*)
    ;

filter_list_expr
    : expr | splicing_expr | range_expr
    ;

// Used in filter_expr
// 1 :: 4, ::4, 4::
splicing_expr
    : expr '::' expr
    | '::' expr
    | expr '::'
    ;

// Used to generate values for JsonArray
// [1 ... 4] --> [1, 2, 3, 4]
range_expr
    : expr '...' expr
    ;

lambda_function_call
    : '=>' 'Lambda' '(' function_named_argument? (',' function_named_argument)* ')' ':' exception_stmt? let_stmt* expr (END | END_MARK | 'End:Lambda')
    ;

lambda_function_ref
    : 'Lambda' '(' lambda_function_ref_named_argument? (',' lambda_function_ref_named_argument)* ')' json_value_constraint? ':' exception_stmt? let_stmt* expr (END | END_MARK | 'End:Lambda')
    ;

auto_invoke_ref
    : 'Ref' '(' exception_stmt? let_stmt* expr ')'
    ;

function_call
    : '=>' (ID ':')* ID '(' (function_regular_argument | function_named_argument)? (',' (function_regular_argument | function_named_argument))* ')'
    ;

function_ref
    : (ID ':')* ID ('(' (function_regular_argument | function_ref_named_argument | function_ref_argument_placeholder)? (',' (function_regular_argument | function_ref_named_argument | function_ref_argument_placeholder))* ')')
    ;

// 
// This is used currently in the function_ref_invoke
// 
function_arguments
    : '(' (function_regular_argument | function_named_argument)? (',' (function_regular_argument | function_named_argument))* ')'
    ;

// fragment
// used in function_arguments
function_regular_argument
    : expr
    ;

// fragment
// Used in regular (non-function ref) function arguments
function_named_argument
    : CONST_ID ':' expr
    ;

//
// This is used for function_stmt
//
function_stmt_param
    : CONST_ID json_value_constraint?
    ;

// Used in lambda_function_ref -function argument
lambda_function_ref_named_argument
    : CONST_ID json_value_constraint? ':' (expr | function_ref_argument_placeholder)
    ;

// Used in function ref -function argument
function_ref_named_argument
    : CONST_ID ':' (expr | function_ref_argument_placeholder)
    ;

// fragment
function_ref_argument_placeholder
    : '?'
    ;

// expr must evaluate into FunctionRef
function_ref_curry
    : ('(' (function_regular_argument | function_ref_named_argument | function_ref_argument_placeholder)? (',' (function_regular_argument | function_named_argument | function_ref_argument_placeholder))* ')')+
    ;

function_ref_invoke
    : '=>' pattern_configs? expr function_arguments
    ;

function_ref_invoke_full
    : 'Invoke' pattern_configs? expr function_arguments (END | END_MARK | 'End:Invoke')
    ;

// NOTE: there's also 'End', but not listed here because would confuse parser.
operon_type_function_shortcut
    : 'Object' | 'Array' | 'String' | 'EmptyObject' | 'EmptyArray' | 'EmptyString' | 'Number' | 'True' | 'False' | 'Boolean' 
    | 'Empty' | 'Null' | 'Binary' | 'Stream' | 'Lambda' | 'Function' | 'Path'
    | 'Error'
    ;

input_source
    : 'json' json_value_constraint? ':' json
    | 'sequence' json_value_constraint? ':' json_array
    | ID (':' ID)? json_value_constraint? ':' json_obj?
    ;

root_input_source
    : ROOT_VALUE json_value_constraint? ':' json
    | ROOT_VALUE ':' input_source
    ;

// First ID = component name
// Second ID = io-component's unique identifier (must be unique in a registry)
// Examples:
//   -> out:debug:{"printValue": false} # full form with configuration
//   -> operon:{"port": 8082}   # no identifier but configuration
//   -> out # only component. Uses default configuration
io_call
    : '->' ID ':' ID ':' json_obj
    | '->' ID ':' json_obj
    | '->' ID ':' ID
    | '->' ID
    ;

computed_value_ref
    : (ID ':')* '$' '(' expr ')'
    ;

value_ref
    : CURRENT_VALUE // @
    | OBJ_SELF_REFERENCE // _
    | ROOT_VALUE // $
    | (ID ':')* CONST_ID // $ba or local:$wa or mylib:local:$sa
    ;

bind_function_expr
    : 'Bind Function' value_ref '[' operator_expr? (',' operator_expr)* ']'
    ;

bind_component_expr
    : 'Bind Component' value_ref '[' operator_expr? (',' operator_expr)* ']'
    ;

// There's bug if we separate the Bind and Value (as they should) 'Bind' 'Value'
bind_value_expr
    : 'Bind Value' value_ref '[' operator_expr? (',' operator_expr)* ']'
    ;

operator_expr
    : 'Operator' '(' (EQ | LT | GT | LTE | GTE | AND | OR | NOT | PLUS | MINUS | NEGATE | MULT | DIV | POW | MOD) ',' function_ref (',' 'cascade')? ')'
    ;

input_source_alias
    : CONST_ID
    ;

throw_exception
    : 'Throw' '(' expr ')'
    ;

// ====================JSON=================================
json
   : json_value
   ;

//
// {key: "value"}
// {"key": "value"}
// {}
// {key} --> {key: true}
// {"key"} --> {"key": true}
//
json_obj
   : '{' json_pair (',' json_pair)* '}'
   | '{' '}'
   ;

compiler_obj_config_lookup
   : ('<?config:' | '<?env:') ID '>'
   ;

//
// TODO: add support for hiding a field
//
//   {foo {hidden} <String>: "bar"}
// Perhaps use literal "{hidden}" for this?
// Another option is to use double comma "::" for hidden-option, but that's not operonic.
//
json_pair
   //: (STRING | ID) json_value_constraint? (':' (json_value | expr))?
   : (STRING | ID) json_obj? json_value_constraint? (':' (json_value | expr))?
   ;

json_value_constraint
    : '<' expr '>'
    ;

json_array
   : '[' (json_value | expr | range_expr) (',' (json_value | expr | range_expr))* ']'
   | '[' ']'
   ;

//
// "Path" cannot be lowercase, since would be confused with function-name.
// - Path(...)
//    - with parentheses, so we can use e.g. Filter-expr. Empty path can be given as Path().
//   Examples:
//    - Path($foo.names[1]) --> resolve root-value from named Value.
//    - Path(foo().names[1]) --> resolve root-value from function foo.
//    - Path(.names[1]) --> no root-value, it must be set manually.
// - ~...
//    - Concise expr for path. Does not support empty path.
// - ~(...)
//      Allow using Filter-expr after Path. Empty path can be given as ~().
//
path_value
   : ('Path' | '~') '(' (CONST_ID | (ID ':')* ID '(' ')')? (('.' ID) | ('[' NUMBER ']'))* ')'
   | '~' (CONST_ID | (ID ':')* ID '(' ')')? (('.' ID) | ('[' NUMBER ']'))+
   | '~' '(' (('.' ID) | ('[' NUMBER ']'))* ')'
   ;

json_value
   : json_obj
   | json_array
   | (STRING
     | NUMBER
     | JSON_TRUE
     | JSON_FALSE
     | JSON_NULL
     | EMPTY_VALUE
     | RAW_STRING
     | MULTILINE_STRIPPED_STRING
     | MULTILINE_PADDED_LINES_STRING
     | MULTILINE_STRING
     | END_VALUE)
   | path_value
   | compiler_obj_config_lookup
   ;
// =====================================================

END
    : 'End'
    ;
    
END_MARK
    : ';'
    ;

LET
    : 'Let'
    ;

CONST_ID
    : [\\$][a-zA-Z]+[a-zA-Z_0-9]*
    ;

OBJ_DEEP_SCAN
    : '..'
    ;

OBJ_ACCESSOR
    : '.'
    ;

//fragment INTEGRATION
//    : ID ':' [a-zA-Z][0-9a-zA-Z\\._]+
//    ;

CURRENT_VALUE
    : '@'
    ;

OBJ_SELF_REFERENCE
    : '_'
    ;

ROOT_VALUE
    : '$'
    ;

// Used in Set-expressions
SET
    : ':'
    ;

AND
    : 'And'
    ;
    
OR
    : 'Or'
    ;

NOT
    : 'Not'
    ;
    
EQ
    : '='
    ;

IEQ
    : '!='
    ;


LT
    : '<'
    ;

GT
    : '>'
    ;

LTE
    : '<='
    ;

GTE
    : '>='
    ;

PLUS
    : '+'
    ;

MINUS
    : '-'
    ;

NEGATE
    : 'Negate'
    ;

MULT
    : '*'
    ;

DIV
    : '/'
    ;

POW
    : '^'
    ;

MOD
    : '%'
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

COMMENT
    : '#' ~[\r\n]* -> skip
    ;
    
// ============= JSON================

STRING
   : '"' (ESC | SAFECODEPOINT)* '"'
   ;

RAW_STRING
   : '`' (BINARY_ALLOWED | BINARY_SAFECODEPOINT)* '`'
   ;

//
// Initial paddings for each line are ignored.
// The lines are concatenated and new-lines removed.
//
// NOTE: the non-greedy operator ?
//
MULTILINE_STRIPPED_STRING
   : '"""|' (MULTILINE_ESC | SAFECODEPOINT)*? '"""'
   ;

//
// Initial paddings for each line are retained.
// The lines are concatenated and new-lines kept.
//
// NOTE: the non-greedy operator ?
//
MULTILINE_PADDED_LINES_STRING
   : '""">' (MULTILINE_ESC | SAFECODEPOINT)*? '"""'
   ;

//
// NOTE: the non-greedy operator ?
//
MULTILINE_STRING
   : '"""' (MULTILINE_ESC | SAFECODEPOINT)*? '"""'
   ;

fragment ESC
   : '\\' (["\\/brntf] | UNICODE)
   ;

fragment MULTILINE_ESC
   : '\r' | '\n' | '\t' | '"' | ('\\' (["\\/bfrnt] | UNICODE))
   //: '\r' | '\n' | '\t' | '"' | ('\\' (["\\/bfrnt] | UNICODE))
   ;

//
// Allow these outside from the BINARY_SAFECODEPOINT:
//
fragment BINARY_ALLOWED
   : [\b\r\n\t\f\\]
   | '\\`'
   ;

fragment UNICODE
   : 'u' HEX HEX HEX HEX
   ;

fragment HEX
   : [0-9a-fA-F]
   ;

fragment SAFECODEPOINT
   : ~ ["\\\u0000-\u001F]
   ;

fragment BINARY_SAFECODEPOINT
   : ~ [`\\\u0000-\u001F]
   ;

NUMBER
   : '-'? INT ('.' [0-9] +)? EXP?
   ;

fragment INT
   : '0' | [1-9] [0-9]*
   ;

//
// allow: 1e-03 (INT does not allow this since exp has leading zeros)
//
fragment EXP_INT
   : '0' | [0-9] [0-9]*
   ;

JSON_TRUE
    : 'true'
    ;

JSON_FALSE
    : 'false'
    ;

JSON_NULL
    : 'null'
    ;

EMPTY_VALUE
    : 'empty'
    ;

END_VALUE
    : 'end'
    ;

ID
    : [a-zA-Z_][a-zA-Z0-9_\-]*
    ;

// no leading zeros
// Note: has \- since - means "range" (regexp) inside [...]
fragment EXP
   : [Ee] [+\-]? EXP_INT
   ;

