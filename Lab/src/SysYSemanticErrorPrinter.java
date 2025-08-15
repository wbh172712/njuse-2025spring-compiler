import type.*;
// lab3 语法分析 : 错误检测与输出

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SysYSemanticErrorPrinter extends SysYParserBaseVisitor<Void> {

    public boolean hasSemanticError = false;

    private Scope currentScope;

    public Type curFuncRetType = null;

    public Map<String, Integer> errorType = new HashMap<>(){{
        put("1 VarNotDeclared", 1); // 变量未声明
        put("2 FuncNotDefined", 2); // 函数未定义
        put("3 VarAlreadyDeclared", 3); // 变量重复声明
        put("4 FuncAlreadyDefined", 4); // 函数重复定义
        put("5 EqualFormError", 5); // 赋值号两侧类型不匹配
        put("6 OpError", 6); // 运算符需求类型与提供类型不匹配
        put("7 ReturnError", 7); // 返回值类型不匹配
        put("8 ParamError", 8); // 函数参数不适用
        put("9 IndexError", 9); // 对非数组使用下标运算符
        put("10 MistakeVarToFunc", 10); // 对变量使用函数调用
        put("11 LvalError", 11); // 赋值号左侧非变量或数组元素
    }};

    public List<String> errors = new ArrayList<>();

    // 访问程序的根节点
    @Override
    public Void visitProgram(SysYParser.ProgramContext ctx) {
        //全局作用域
        currentScope = new Scope(null);;
        return super.visitProgram(ctx);
    }

    // 处理常量声明
    @Override
    public Void visitConstDecl(SysYParser.ConstDeclContext ctx) {
        for (int i = 0; i < ctx.constDef().size(); i++) {
            if (currentScope.findInCurrentScope(ctx.constDef(i).IDENT().getText()) != null) {
                printSemanticError("3 VarAlreadyDeclared", ctx.constDef(i).IDENT().getSymbol().getLine());
            }
            visit(ctx.constDef(i)); // 访问所有常量定义
        }
        return null;
    }

    // 处理变量声明
    @Override
    public Void visitVarDecl(SysYParser.VarDeclContext ctx) {
        for (int i = 0; i < ctx.varDef().size(); i++) {
            if (currentScope.findInCurrentScope(ctx.varDef(i).IDENT().getText()) != null) {
                printSemanticError("3 VarAlreadyDeclared", ctx.varDef(i).IDENT().getSymbol() .getLine());
            }
            visit(ctx.varDef(i)); // 访问所有常量定义
        }
        return null;
    }

    // 处理常量定义
    @Override
    public Void visitConstDef(SysYParser.ConstDefContext ctx) {
        String symbolName = ctx.IDENT().getText();
        Type lType = new IntType();
        if (!ctx.L_BRACKT().isEmpty()) { // 数组
            for (int i = 0; i < ctx.constExp().size(); i++) {
                lType = new ArrayType(lType); // 迭代嵌套 ltype
            }
        }

        if (ctx.ASSIGN() != null) {
            super.visitConstDef(ctx);
            if (!errors.isEmpty()) {
                String prvError = errors.get(errors.size() - 1).split("/")[0];
                String prvLine = errors.get(errors.size() - 1).split("/")[1];
                if (!prvError.equals("5 EqualFormError")
                        && prvLine.equals(String.valueOf(ctx.getStart().getLine()))) {
                    return null;
                }
            }

            SysYParser.ConstExpContext constExpCtx = ctx.constInitVal().constExp(); // 访问初始化值
            if (constExpCtx != null) { // constInitVal 为 exp
                Type rType = getExpType(constExpCtx.exp());
                if (!lType.typeMatch(rType)) {
                    printSemanticError("5 EqualFormError", ctx.getStart().getLine());
                    return null;
                }
            } else { // initVal 为 {···}
//                Type rType = new ArrayType(new IntType());
//                if (!typeCheck(lType, rType)) {
//                    printSemanticError("5 EqualFormError", ctx.getStart().getLine());
//                    return null;
//                }
            }
        }
        currentScope.put(symbolName, lType);
        return null;
    }

    // 处理变量定义
    @Override
    public Void visitVarDef(SysYParser.VarDefContext ctx) {
        String symbolName = ctx.IDENT().getText();
        Type lType = new IntType();
        if (!ctx.L_BRACKT().isEmpty()) { // 数组
            int n = ctx.constExp().size();
            for (int i = 0; i < n; i++) {
                lType = new ArrayType(lType); // 迭代嵌套 ltype
            }
        }

        if (ctx.ASSIGN() != null) {
            super.visitVarDef(ctx);
            if (!errors.isEmpty()) {
                String prvError = errors.get(errors.size() - 1).split("/")[0];
                String prvLine = errors.get(errors.size() - 1).split("/")[1];
                if (!prvError.equals("5 EqualFormError")
                        && prvLine.equals(String.valueOf(ctx.getStart().getLine()))) {
                    return null;
                }
            }

            SysYParser.ExpContext expCtx = ctx.initVal().exp(); // 访问初始化值
            if (expCtx != null) { // initVal 为 exp
                Type rType = getExpType(expCtx);
                if (!lType.typeMatch(rType)) {
                    printSemanticError("5 EqualFormError", ctx.getStart().getLine());
                    return null;
                }
            } else { // initVal 为 {···}
//                Type rType = new ArrayType(new IntType());
//                if (!typeCheck(lType, rType) && !((lType instanceof ArrayType) && (rType instanceof ArrayType))) {
//                    printSemanticError("5 EqualFormError", ctx.getStart().getLine());
//                    return null;
//                }
            }
        }
        currentScope.put(symbolName, lType);
        return null;
    }

    private Type getExpType(SysYParser.ExpContext ctx) {
        if (ctx.isEmpty()) {
            return null;
        } else if (ctx.lVal() != null) { // lVal
            return getLValType(ctx.lVal());
        } else if (ctx.number() != null) { // number
            return new IntType();
        } else if (ctx.IDENT() != null) { // IDENT L_PAREN funcRParams? R_PAREN
            Type funcTypeTemp = currentScope.findInAllScope(ctx.IDENT().getText());
            if(!(funcTypeTemp instanceof FunctionType)) return null;
            FunctionType funcType = (FunctionType) funcTypeTemp;
            if (funcType.getReturnType() instanceof IntType) {
                return new IntType();
            }
            return null;
        } else if (ctx.L_PAREN() != null) { // L_PAREN exp R_PAREN
            return getExpType(ctx.exp(0));
        } else if (ctx.unaryOp() != null) { // 一元运算
            Type expType = getExpType(ctx.exp(0));
            if (expType instanceof IntType) {
                return new IntType();
            }
            return null;
        } else if (ctx.exp().size() == 2) { // 二元运算
            Type lType = getExpType(ctx.exp(0));
            Type rType = getExpType(ctx.exp(1));
            if (lType == null) {
                return null;
            }
            if (lType.typeMatch(rType)) {
                return lType;
            }
             return null;
        }
        return new IntType();
    }

    public Type getLValType(SysYParser.LValContext ctx) {
        String symbolName = ctx.IDENT().getText();
        Type lvalType = currentScope.findInAllScope(symbolName);

        if (lvalType == null) {
            return null;
        }

        // 如果是数组，需要进一步确定访问的维度
        for (SysYParser.ExpContext expCtx : ctx.exp()) {
            if (!(lvalType instanceof ArrayType)) {
                return null;
            }
            lvalType = ((ArrayType) lvalType).getElementType();
        }
        return lvalType;
    }

    // 处理左值
    @Override
    public Void visitLVal(SysYParser.LValContext ctx) {
        String symbolName = ctx.IDENT().getText();
        if(currentScope.findInAllScope(symbolName) == null){
            printSemanticError("1 VarNotDeclared", ctx.IDENT().getSymbol().getLine());
            return null;
        }

        // 检测维度
        Type lValTypeTemp = getLValType(ctx);
        if(lValTypeTemp == null){
            printSemanticError("9 IndexError", ctx.IDENT().getSymbol().getLine());
            return null;
        }
        return super.visitLVal(ctx);
    }

    // 处理函数定义
    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {

        String constName = ctx.IDENT().getText();
        if(currentScope.findInCurrentScope(constName) != null){
            printSemanticError("4 FuncAlreadyDefined", ctx.IDENT().getSymbol().getLine());
            return null;
        }

        Type retType;
        if (ctx.funcType().INT() != null) {
            retType = new IntType();
        } else {
            retType = new VoidType();
        }

        // 处理参数
        ArrayList<Type> paramsType = new ArrayList<>();
        currentScope = new Scope(currentScope);

        if(ctx.funcFParams() != null){
            visit(ctx.funcFParams());
            for (Map.Entry<String, Type> entry : currentScope.symbolTable.entrySet()) { //获取当前作用域，即函数参数的所有type
                paramsType.add(entry.getValue());
            }
        }else{
            paramsType = new ArrayList<>();
        }
        curFuncRetType = retType;
        currentScope.getFatherScope().put(ctx.IDENT().getText(), new FunctionType(retType, paramsType));
        visit(ctx.block());
        currentScope = currentScope.getFatherScope();
        curFuncRetType = null;
        return null;
    }

    // 处理函数参数
    @Override
    public Void visitFuncFParam(SysYParser.FuncFParamContext ctx) {
        String paramName = ctx.IDENT().getText();
        if(currentScope.findInCurrentScope(paramName) != null){
            printSemanticError("3 VarAlreadyDeclared", ctx.IDENT().getSymbol().getLine());
            return null;
        }
        Type paramType;
        if (ctx.getChildCount() == 2) {
            paramType = new IntType();
        } else {
            paramType = new ArrayType(new IntType());
        }
        currentScope.put(paramName, paramType);
        return null;
    }

    @Override
    public Void visitFuncRParams(SysYParser.FuncRParamsContext ctx) {
        super.visitFuncRParams(ctx);

        SysYParser.ExpContext exp = (SysYParser.ExpContext) ctx.getParent();

        if (!errors.isEmpty()) {
            String prvError = errors.get(errors.size() - 1).split("/")[0];
            String prvLine = errors.get(errors.size() - 1).split("/")[1];
            if (!prvError.equals("8 ParamError")
                    && prvLine.equals(String.valueOf(exp.getStart().getLine()))) {
                return null;
            }
        }

        FunctionType funcType = (FunctionType) currentScope.findInAllScope(exp.IDENT().getText());
        ArrayList<Type> paramsType = (ArrayList<Type>) funcType.getParamsType();

        ArrayList<SysYParser.ParamContext> rParamsType;
        if (ctx.param().isEmpty()) {
            rParamsType = new ArrayList<>();
        } else {
            rParamsType = (ArrayList<SysYParser.ParamContext>) ctx.param();
        }

        if(paramsType.size() != rParamsType.size()){
            printSemanticError("8 ParamError", ctx.getStart().getLine());
            return null;
        }
        for(int i = 0; i < rParamsType.size(); i++){
            Type rParamTypeTemp = getExpType(rParamsType.get(i).exp());
            if (!paramsType.get(i).typeMatch(rParamTypeTemp)){
                printSemanticError("8 ParamError", ctx.getStart().getLine());
                return null;
            }
        }
        return null;
    }

    // 访问块
    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        currentScope = new Scope(currentScope);
        super.visitBlock(ctx);
        currentScope = currentScope.getFatherScope();
        return null;
    }

    // 处理语句
    @Override
    public Void visitStmt(SysYParser.StmtContext ctx) {

        if (ctx.ASSIGN() != null) {
            if(currentScope.findInAllScope(ctx.lVal().IDENT().getText()) instanceof FunctionType){
                printSemanticError("11 LvalError", ctx.getStart().getLine());
                return null;
            }

            super.visitStmt(ctx);

            if (!errors.isEmpty()) {
                String prvError = errors.get(errors.size() - 1).split("/")[0];
                String prvLine = errors.get(errors.size() - 1).split("/")[1];
                if (!prvError.equals("5 EqualFormError")
                        && prvLine.equals(String.valueOf(ctx.getStart().getLine()))) {
                    return null;
                }
            }

            Type lType = getLValType(ctx.lVal());
            Type rType = getExpType(ctx.exp());
            if (lType instanceof FunctionType) {
                printSemanticError("11 LvalError", ctx.lVal().getStart().getLine());

            } else if (lType != null && !lType.typeMatch(rType)) {
                printSemanticError("5 EqualFormError", ctx.lVal().getStart().getLine());
            }

            return null;

        } else if (ctx.RETURN() != null) {
            if(ctx.exp() == null){
                if(!(curFuncRetType instanceof VoidType)){
                    printSemanticError("7 ReturnError", ctx.RETURN().getSymbol().getLine());
                    return null;
                }
            } else {
                super.visitStmt(ctx);
                if (!errors.isEmpty()) {
                    String prvError = errors.get(errors.size() - 1).split("/")[0];
                    String prvLine = errors.get(errors.size() - 1).split("/")[1];
                    if (!prvError.equals("7 ReturnError")
                            && prvLine.equals(String.valueOf(ctx.getStart().getLine()))) {
                        return null;
                    }
                }
                if (!curFuncRetType.typeMatch(getExpType(ctx.exp()))) {
                    printSemanticError("7 ReturnError", ctx.exp().getStart().getLine());
                }
            }
        }
        return super.visitStmt(ctx);
    }

    // 处理表达式
    @Override
    public Void visitExp(SysYParser.ExpContext ctx) {
        if (ctx.unaryOp() != null) {
            super.visitExp(ctx);
            // 处理一元运算符 (如 +,-,!)
            if (!errors.isEmpty()) {
                String prvLine = errors.get(errors.size() - 1).split("/")[1];
                if (prvLine.equals(String.valueOf(ctx.getStart().getLine()))) {
                    return null;
                }
            }
            Type expType = getExpType(ctx.exp(0));
            if (!(expType instanceof IntType)) {
                printSemanticError("6 OpError", ctx.unaryOp().getStart().getLine());
            }
            return null;
        } else if (ctx.exp().size() == 2) {
            super.visitExp(ctx);
            // 处理二元运算符 (如 +, -, *, /, %, ==, !=, <, >, <=, >=, &&, ||)
            if (!errors.isEmpty()) {
                String prvLine = errors.get(errors.size() - 1).split("/")[1];
                if (prvLine.equals(String.valueOf(ctx.getStart().getLine()))) {
                    return null;
                }
            }
            if(!(getExpType(ctx.exp(0)) instanceof IntType) || !(getExpType(ctx.exp(1)) instanceof IntType)){
                printSemanticError("6 OpError", ctx.getStart().getLine());
            }
        } else if (ctx.IDENT() != null) {
            Type type = currentScope.findInAllScope(ctx.IDENT().getText());
            if(type == null){
                printSemanticError("2 FuncNotDefined", ctx.IDENT().getSymbol().getLine());
                return null;
            }else if(!(type instanceof FunctionType)){
                printSemanticError("10 MistakeVarToFunc", ctx.IDENT().getSymbol().getLine());
                return null;

            }else if (ctx.funcRParams() == null) { // type instanceof FunctionType ctx运行到这里如果funcRParams为空无法识别它为子类

                FunctionType funcType = (FunctionType) type;

                if(!funcType.getParamsType().isEmpty()){
                    printSemanticError("8 ParamError", ctx.getStart().getLine());
                    return null;
                }
            }
        }
        return super.visitExp(ctx);
    }

    // 处理条件表达式
    @Override
    public Void visitCond(SysYParser.CondContext ctx) {
        super.visitCond(ctx);

        for (String oldErrorStr : errors) {
            if (oldErrorStr.split("/")[1].equals(String.valueOf(ctx.getStart().getLine()))) {
                return null;
            }
        }
        Type condType = getCondType(ctx);
        if (!(condType instanceof IntType)) {
            printSemanticError("6 OpError", ctx.getStart().getLine());
        }
        return null;
    }

    public Type getCondType(SysYParser.CondContext ctx) {
        if (ctx.isEmpty()) {
            return null;
        }
        if (ctx.exp() != null) {
			return getExpType(ctx.exp());
		}

        Type lType = getCondType(ctx.cond(0));
        Type rType = getCondType(ctx.cond(1));
        if (lType.typeMatch(rType)) {
            return lType;
        }
        return null;
    }

    // 输出检查到的错误类型
    public void printSemanticError(String error, int line){
        hasSemanticError = true;
        System.err.println("Error type " + errorType.get(error) + " at Line " + line + ": " + error);
        errors.add(String.valueOf(error + "/"+  line));
    }
}
