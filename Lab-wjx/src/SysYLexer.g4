lexer grammar SysYLexer;

CONST : 'const';

INT : 'int';

VOID : 'void';

IF : 'if';

ELSE : 'else';

WHILE : 'while';

BREAK : 'break';

CONTINUE : 'continue';

RETURN : 'return';

PLUS : '+';

MINUS : '-';

MUL : '*';

DIV : '/';

MOD : '%';

ASSIGN : '=';

EQ : '==';

NEQ : '!=';

LT : '<';

GT : '>';

LE : '<=';

GE : '>=';

NOT : '!';

AND : '&&';

OR : '||';

L_PAREN : '(';

R_PAREN : ')';

L_BRACE : '{';

R_BRACE : '}';

L_BRACKT : '[';

R_BRACKT : ']';

COMMA : ',';

SEMICOLON : ';';

IDENT : [a-zA-Z_][a-zA-Z_0-9]*;

INTEGER_CONST : [0-9]+ | '0' [0-7]* | '0x' [0-9a-fA-F]+;

WS
   : [ \r\n\t]+;

LINE_COMMENT
   : '//' .*? '\n';

MULTILINE_COMMENT
   : '/*' .*? '*/';
