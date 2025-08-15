parser grammar SysYParser;

options {
    tokenVocab = SysYLexer;//注意使用该语句指定词法分析器；请不要修改词法分析器或语法分析器的文件名，否则Makefile可能无法正常工作，影响评测结果
}

program
   : compUnit
   ;
compUnit
   : (funcDef | decl)+ EOF
   ; // 编译单元

decl : constDecl | varDecl; // 声明
constDecl : CONST bType constDef (COMMA constDef)* SEMICOLON; // 常量声明
bType : INT; // 基本类型
constDef : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal; // 常量定义
constInitVal : constExp | L_BRACE (constInitVal (COMMA constInitVal)*)? R_BRACE; // 常量初值
varDecl : bType varDef (COMMA varDef)* SEMICOLON; // 变量声明
varDef : IDENT (L_BRACKT constExp R_BRACKT)* (ASSIGN initVal)?; // 变量定义
initVal : exp | L_BRACE (initVal (COMMA initVal)*)? R_BRACE; // 变量初值
funcDef : funcType IDENT L_PAREN (funcFParams)? R_PAREN block; // 函数定义
funcType : VOID | INT; // 函数类型
funcFParams : funcFParam (COMMA funcFParam)*; // 函数形参表
funcFParam : bType IDENT (L_BRACKT R_BRACKT (L_BRACKT exp R_BRACKT)*)?; // 函数形参
block : L_BRACE (blockItem)* R_BRACE; // 语句块
blockItem : decl | stmt; // 语句块项
stmt : lVal ASSIGN exp SEMICOLON | (exp)? SEMICOLON | block |IF L_PAREN cond R_PAREN stmt (ELSE stmt)? | WHILE L_PAREN cond R_PAREN stmt | BREAK SEMICOLON | CONTINUE SEMICOLON | RETURN (exp)? SEMICOLON; // 语句
exp
   : L_PAREN exp R_PAREN
   | lVal
   | number
   | IDENT L_PAREN funcRParams? R_PAREN
   | unaryOp exp
   | exp (MUL | DIV | MOD) exp
   | exp (PLUS | MINUS) exp
   ;

cond
   : exp
   | cond (LT | GT | LE | GE) cond
   | cond (EQ | NEQ) cond
   | cond AND cond
   | cond OR cond
   ;

lVal
   : IDENT (L_BRACKT exp R_BRACKT)*
   ;

number
   : INTEGER_CONST
   ;

unaryOp
   : PLUS
   | MINUS
   | NOT
   ;

funcRParams
   : param (COMMA param)*
   ;

param
   : exp
   ;

constExp
   : exp
   ;


//   以下是官方文档里面的文法定义
/*
编译单元
CompUnit → [ CompUnit ] ( Decl | FuncDef )
声明
Decl → ConstDecl | VarDecl
常量声明
ConstDecl → 'const' BType ConstDef { COMMA ConstDef } ';'
基本类型
BType → 'int'
常数定义
ConstDef
→ Ident { '[' ConstExp ']' } ASSIGN ConstInitVal
常量初值
ConstInitVal
→ ConstExp
| L_BRACE [ ConstInitVal { COMMA ConstInitVal } ] R_BRACE
变量声明
VarDecl → BType VarDef { COMMA VarDef } ';'
变量定义
VarDef → Ident { '[' ConstExp ']' }
| Ident { '[' ConstExp ']' } ASSIGN InitVal
变量初值
InitVal
→ Exp | L_BRACE [ InitVal { COMMA InitVal } ] R_BRACE
函数定义
FuncDef → FuncType Ident L_PAREN [FuncFParams] R_PAREN Block
函数类型
FuncType → 'void' | 'int'
函数形参表
FuncFParams
→ FuncFParam { COMMA FuncFParam }
函数形参
FuncFParam → BType Ident ['[' ']' { '[' Exp ']' }]
语句块
Block → L_BRACE { BlockItem } R_BRACE
语句块项
BlockItem → Decl | Stmt
语句
Stmt → LVal ASSIGN Exp ';' | [Exp] ';' | Block
| 'if' '( Cond R_PAREN Stmt [ 'else' Stmt ]
| 'while' L_PAREN Cond R_PAREN Stmt
| 'break' ';' | 'continue' ';'
| 'return' [Exp] ';'
表达式
Exp → AddExp 注：SysY 表达式是 int 型表达式
条件表达式
Cond → LOrExp
左值表达式
LVal → Ident {'[' Exp ']'}
基本表达式
PrimaryExp
→ L_PAREN Exp R_PAREN | LVal | Number
数值
Number
→ IntConst
一元表达式
UnaryExp
→ PrimaryExp | Ident L_PAREN [FuncRParams] R_PAREN
| UnaryOp UnaryExp
单目运算符
UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
函数实参表
FuncRParams
→ Exp { COMMA Exp }
乘除模表达式
MulExp
→ UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
加减表达式
AddExp
→ MulExp | AddExp ('+' | '−') MulExp
关系表达式
RelExp
→ AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
相等性表达式
EqExp
→ RelExp | EqExp ('==' | '!=') RelExp
逻辑与表达式
LAndExp
→ EqExp | LAndExp '&&' EqExp
逻辑或表达式
LOrExp
→ LAndExp | LOrExp '||' LAndExp
常量表达式
ConstExp
→ AddExp 注：使用的 Ident 必须是常量
*/