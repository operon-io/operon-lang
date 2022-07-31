// 
// License: https://operon.io/operon-license
// 

// Derived from http://json.org
grammar JSON;

json
   : value
   ;

obj
   : '{' pair (',' pair)* '}'
   | '{' '}'
   ;

pair
   : (STRING | ID) ':' value
   ;

array
   : '[' value (',' value)* ']'
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

value
   : STRING
   | NUMBER
   | obj
   | array
   | TRUE
   | FALSE
   | NULL
   | EMPTY_VALUE
   | RAW_STRING
   | SINGLE_QUOTED_STRING
   | MULTILINE_STRIPPED_STRING
   | MULTILINE_PADDED_LINES_STRING
   | MULTILINE_STRING
   | path_value
   ;

FALSE
   : 'false'
   ;
   
TRUE
   : 'true'
   ;
   
NULL
   : 'null'
   ;
   
STRING
   : '"' (ESC | SAFECODEPOINT)* '"'
   ;

SINGLE_QUOTED_STRING
   : '\'' (SINGLE_QUOTED_ESC | '"' | SINGLE_QUOTED_SAFECODEPOINT)* '\''
   ;

RAW_STRING
   : '`' (BINARY_ALLOWED | BINARY_SAFECODEPOINT)* '`'
   ;

//
// Initial paddings for each line are ignored.
// The lines are concatenated and new-lines removed.
//
MULTILINE_STRIPPED_STRING
   : '"""|' (MULTILINE_ESC | SAFECODEPOINT)* '"""'
   ;

//
// Initial paddings for each line are ignored.
// The lines are concatenated and new-lines kept.
//
MULTILINE_PADDED_LINES_STRING
   : '""">' (MULTILINE_ESC | SAFECODEPOINT)* '"""'
   ;

MULTILINE_STRING
   : '"""' (MULTILINE_ESC | SAFECODEPOINT)* '"""'
   ;

EMPTY_VALUE
   : 'empty'
   ;

fragment ESC
   : '\\' (["\\/bfnrt] | UNICODE)
   ;

fragment MULTILINE_ESC
   : '\r' | '\n' | '\t' | ('\\' (["\\/bfrnt] | UNICODE))
   ;

fragment SINGLE_QUOTED_ESC
   : '\\' (['\\/bfrnt] | UNICODE)
   ;

//
// Allow these outside from the BINARY_SAFECODEPOINT:
//
fragment BINARY_ALLOWED
   : [\b\r\n\t\f]
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

fragment SINGLE_QUOTED_SAFECODEPOINT
   : ~ ['\\\u0000-\u001F]
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

// no leading zeros
// Note: has \- since - means "range" (regexp) inside [...]
fragment EXP
   : [Ee] [+\-]? EXP_INT
   ;

WS
   : [ \t\n\r] + -> skip
   ;

CONST_ID
    : [\\$][a-zA-Z]+[a-zA-Z_0-9]*
    ;

ID
    : [a-zA-Z_][a-zA-Z0-9_\-]*
    ;

COMMENT
    : '#' ~[\r\n]* -> skip
    ;
