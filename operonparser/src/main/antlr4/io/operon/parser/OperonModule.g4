grammar OperonModule;

// 
// License: https://operon.io/operon-license
// 

import OperonBase;

operonmodule
    : import_stmt?
    (function_stmt | let_stmt | bind_value_expr)*
    ;
