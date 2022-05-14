grammar OperonTests;

// 
// License: https://operon.io/operon-license
// 

import OperonBase;

operontests
    : from?
      (assert_component_stmt | mock_stmt)*
    ;

assert_component_stmt
    : 'Assert' json_obj? 'Component' (ID ':')+ ID 'With' expr ('End' | ';' | 'End:Assert')
    ;

mock_stmt
    : 'Mock Component' (ID ':')+ ID 'With' expr ('End' | ';' | 'End:Mock')
    ;