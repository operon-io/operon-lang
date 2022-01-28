grammar Operon;

//
// License: Operon-license v1. https://operon.io/operon-license
//

import OperonBase;

operon
    : import_stmt?
      from?
      exception_stmt?
      (function_stmt | let_stmt | bind_value_expr)*
      select
    ;
