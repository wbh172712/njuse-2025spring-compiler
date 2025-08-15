
// lab2 词法分析 : 格式化输出代码

import org.antlr.v4.runtime.tree.TerminalNode;

public class SysYFormatter extends SysYParserBaseVisitor<Void> {
    private int indentLevel = 0;
    private StringBuilder formattedCode = new StringBuilder();
    private boolean newLineNeeded = false;

    // 向格式化代码中追加字符串
    private void append(String str) {
        if (newLineNeeded) {
            formattedCode.append("\r\n").append(" ".repeat(indentLevel * 4)); // 添加缩进
            newLineNeeded = false;
        }
        formattedCode.append(str);
    }

    // 开始新的一行
    private void newLine() {
        newLineNeeded = true;
    }

    // 访问程序的根节点
    @Override
    public Void visitProgram(SysYParser.ProgramContext ctx) {
        visitChildren(ctx);
        return null;
    }

    // 访问compUnit节点
    @Override
    public Void visitCompUnit(SysYParser.CompUnitContext ctx) {
        visitChildren(ctx); // 访问所有子节点
        return null;
    }

    // 处理声明
    @Override
    public Void visitDecl(SysYParser.DeclContext ctx) {
        visitChildren(ctx); // 访问声明的所有子节点
        newLine(); // 换行
        return null;
    }

    // 处理常量声明
    @Override
    public Void visitConstDecl(SysYParser.ConstDeclContext ctx) {
        append("const ");
        visit(ctx.bType()); // 访问基本类型
        append(" ");
        visit(ctx.constDef(0)); // 访问第一个常量定义
        for (int i = 1; i < ctx.constDef().size(); i++) {
            append(", ");
            visit(ctx.constDef(i)); // 访问后续常量定义
        }
        append(";");
        newLine();
        return null;
    }

    @Override
    public Void visitBType(SysYParser.BTypeContext ctx) {
        if (ctx.INT() != null) {
            append("int");  // 如果是 INT 类型
        }
        return null;
    }

    // 处理变量声明
    @Override
    public Void visitVarDecl(SysYParser.VarDeclContext ctx) {
        visit(ctx.bType()); // 访问基本类型
        append(" ");
        visit(ctx.varDef(0)); // 访问第一个变量定义
        for (int i = 1; i < ctx.varDef().size(); i++) {
            append(", ");
            visit(ctx.varDef(i)); // 访问后续变量定义
        }
        append(";");
        newLine();
        return null;
    }

    // 处理常量定义
    @Override
    public Void visitConstDef(SysYParser.ConstDefContext ctx) {
        append(ctx.IDENT().getText()); // 访问标识符
        if (!ctx.L_BRACKT().isEmpty()) {
            append("[");
            // 检查 ctx.constExp() 是否为空或非空
            if (ctx.constExp() != null && !ctx.constExp().isEmpty()) {
                for (int i = 0; i < ctx.constExp().size(); i++) {
                    visit(ctx.constExp().get(i)); // 访问每个常量表达式
                    if (i < ctx.constExp().size() - 1) {
                        append("][");
                    }
                }
            }
            append("]");
        }
        append(" = ");
        visit(ctx.constInitVal()); // 访问常量初始化值
        return null;
    }

    // 处理常量初始化值
    @Override
    public Void visitConstInitVal(SysYParser.ConstInitValContext ctx) {
        if (ctx.constExp() != null) {
            visit(ctx.constExp()); // 访问常量表达式

        } else if (!ctx.constInitVal().isEmpty()) {
            // 检查 ctx.constInitVal() 是否为空或非空
            append("{");
            for (int i = 0; i < ctx.constInitVal().size(); i++) {
                visit(ctx.constInitVal().get(i)); // 访问每个常量表达式
                if (i < ctx.constInitVal().size() - 1) {
                    append(", ");
                }
            }
            append("}");
        } else if (ctx.getText().equals("{}")) {
            append("{}");
        }
        return null;
    }

    // 处理变量定义
    @Override
    public Void visitVarDef(SysYParser.VarDefContext ctx) {
        append(ctx.IDENT().getText()); // 访问标识符

        if (!ctx.L_BRACKT().isEmpty()) {
            // 检查 ctx.constExp() 是否为空或非空
            append("[");
            for (int i = 0; i < ctx.constExp().size(); i++) {
                visit(ctx.constExp().get(i)); // 访问每个常量表达式
                if (i < ctx.constExp().size() - 1) {
                    append("][");
                }
            }
            append("]");
        }
        if (ctx.ASSIGN() != null) {
            append(" = ");
            visit(ctx.initVal()); // 访问初始化值
        }
        return null;
    }

    // 处理初始化值
    @Override
    public Void visitInitVal(SysYParser.InitValContext ctx) {
        if (ctx.exp() != null) {
            visit(ctx.exp()); // 访问表达式

        } else if (ctx.initVal() != null && !ctx.initVal().isEmpty()) {
            // 检查 ctx.initVal() 是否为空或非空
            append("{");
            for (int i = 0; i < ctx.initVal().size(); i++) {
                visit(ctx.initVal().get(i)); // 访问每个常量表达式
                // 如果不是最后一个数组维度，添加逗号
                if (i < ctx.initVal().size() - 1) {
                    append(", ");
                }
            }
            append("}");
        } else if (ctx.getText().equals("{}")) {
            append("{}");
        }
        return null;
    }

    // 处理函数定义
    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        if (formattedCode.length() != 0) {
            newLine();
            append("\r\n");
        }
        visit(ctx.funcType()); // 访问函数类型
        append(" ");
        append(ctx.IDENT().getText()); // 访问函数名称
        append("(");
        if (ctx.funcFParams() != null) visit(ctx.funcFParams()); // 访问函数参数
        append(")");
        visit(ctx.block()); // 访问函数体
        return null;
    }

    // 处理函数类型
    @Override
    public Void visitFuncType(SysYParser.FuncTypeContext ctx) {
        append(ctx.getText()); // 直接输出函数类型（如 VOID, INT, FLOAT）
        return null;
    }

    @Override
    public Void visitFuncFParams(SysYParser.FuncFParamsContext ctx) {
        for (int i = 0; i < ctx.funcFParam().size(); i++) {
            visit(ctx.funcFParam(i)); // Visit each function parameter
            if (i < ctx.funcFParam().size() - 1) {
                append(", "); // Add a comma between parameters
            }
        }
        return null;
    }

    // 处理函数参数
    @Override
    public Void visitFuncFParam(SysYParser.FuncFParamContext ctx) {
        visit(ctx.bType()); // 访问参数类型
        append(" ");
        append(ctx.IDENT().getText()); // 访问参数名称
        if (!ctx.L_BRACKT().isEmpty()) {
            append("[]");

            for (int i = 0; i < ctx.exp().size(); i++) {
                append("[");
                visit(ctx.exp().get(i));
                append("]");
            }
        }
        return null;
    }

    // 处理语句
    @Override
    public Void visitStmt(SysYParser.StmtContext ctx) {
        if (ctx.IF() != null) {
            append("if (");
            visit(ctx.cond());
            append(")");

            if (ctx.stmt(0).block() != null) {
                append(" ");
                visit(ctx.stmt(0).block()); // 访问 else 代码块
            } else {
                newLine();
                indentLevel++;
                visit(ctx.stmt(0)); // 访问 else 内部语句
                indentLevel--;
            }

            // 处理 else 语句
            if (ctx.ELSE() != null) {
                if (ctx.stmt(1).IF() != null) {
                    // 处理 else if
                    newLine();
                    append("else ");
                    visit(ctx.stmt(1)); // 递归处理 else if
                } else { // 处理 else 语句
                    newLine();
                    append("else");
                    if (ctx.stmt(1).block() != null) {
                        append(" ");
                        visit(ctx.stmt(1).block()); // 访问 else 代码块
                    } else {
                        newLine();
                        indentLevel++;
                        visit(ctx.stmt(1)); // 访问 else 内部语句
                        indentLevel--;
                    }
                }
            }
        } else if (ctx.WHILE() != null) {
            append("while (");
            visit(ctx.cond());
            append(")");

            if (ctx.stmt(0).block() != null) {
                append(" ");
                visit(ctx.stmt(0).block()); // 访问 else 代码块
            } else {
                newLine();
                indentLevel++;
                visit(ctx.stmt(0)); // 访问 else 内部语句
                indentLevel--;
            }
        } else if (ctx.RETURN() != null) {
            append("return");
            if (ctx.exp() != null) {
                append(" ");
                visit(ctx.exp());
            }
            append(";");
        } else if (ctx.lVal() != null && ctx.ASSIGN() != null) {
            visit(ctx.lVal()); // 访问左值（变量）
            append(" = ");
            visit(ctx.exp()); // 访问右侧表达式
            append(";");
        } else if (ctx.exp() != null) {
            visit(ctx.exp());
            append(";");
        } else if (ctx.CONTINUE() != null || ctx.BREAK() != null){
            newLine();
            append(ctx.getText());
        } else {
            visitChildren(ctx);
        }
        newLine();
        return null;
    }

    // 处理表达式
    @Override
    public Void visitExp(SysYParser.ExpContext ctx) {
        if (ctx.unaryOp() != null) {
            // 处理一元运算符 (如 +,-,!)
            visit(ctx.unaryOp());
            visit(ctx.exp(0)); // 访问子表达式
        } else if (ctx.exp().size() == 2) {
            // 处理二元运算符 (如 +, -, *, /, %, ==, !=, <, >, <=, >=, &&, ||)
            visit(ctx.exp(0)); // 左操作数
            append(" " + ctx.getChild(1).getText() + " "); // 添加运算符
            visit(ctx.exp(1)); // 右操作数
        } else if (ctx.IDENT() != null) {
            append(ctx.IDENT().getText() + "(");
            if (ctx.funcRParams() != null) {
                visit(ctx.funcRParams());
            }
            append(")");
        } else if (ctx.number() != null) {
            // 处理数字
            visit(ctx.number());
        }  else if (ctx.L_PAREN() != null) {
            // 处理括号表达式，如 (a + b)
            append("(");
            visit(ctx.exp(0));
            append(")");
        } else {
            visitChildren(ctx);
        }

        return null;
    }

    // 处理条件表达式
    @Override
    public Void visitCond(SysYParser.CondContext ctx) {
        if (ctx.exp() != null) {
            visit(ctx.exp()); // 直接访问表达式
        } else if (ctx.cond().size() == 2) {
            // 处理二元运算符（如 &&, ||）
            visit(ctx.cond(0)); // 访问左侧条件
            append(" " + ctx.getChild(1).getText() + " "); // 访问运算符
            visit(ctx.cond(1)); // 访问右侧条件
        }
        return null;
    }

    // 处理数字
    @Override
    public Void visitNumber(SysYParser.NumberContext ctx) {
        append(ctx.getText());
        return null;
    }

    // 处理一元运算符
    @Override
    public Void visitUnaryOp(SysYParser.UnaryOpContext ctx) {
        append(ctx.getText()); // 输出运算符
        return null;
    }

    // 访问块
    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        if (ctx.getParent() instanceof SysYParser.StmtContext) {
            append("{");
        } else {
            // 如果是嵌套块，在块开始时有空格且不换行
            append(" {");
        }

        newLine();
        indentLevel++;
        visitChildren(ctx); // 访问块内的子节点
        indentLevel--;
        newLine();
        append("}");
        newLine();
        return null;
    }

    @Override
    public Void visitLVal(SysYParser.LValContext ctx) {
        append(ctx.IDENT().getText());
        for (int i = 0; i < ctx.exp().size(); i++) {
            append("[");
            visit(ctx.exp(i));
            append("]");
        }
        return null;
    }

    @Override
    public Void visitFuncRParams(SysYParser.FuncRParamsContext ctx) {
        visit(ctx.param(0));
        for (int i = 1; i < ctx.param().size(); i++) {
            append(", ");
            visit(ctx.param(i));
        }
        return null;
    }

    @Override
    public Void visitParam(SysYParser.ParamContext ctx) {
        visit(ctx.exp());
        return null;
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        String text = node.getText();
        if (!text.equals("{") && !text.equals("}") && !text.equals("<EOF>")) {
            append(text);
        }
        return null;
    }

    // 获取格式化后的代码
    public String getFormattedCode() {
        return formattedCode.toString();
    }
}
