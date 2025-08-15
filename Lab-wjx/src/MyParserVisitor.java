
import java.util.ArrayList;
import java.util.List;

public class MyParserVisitor extends SysYParserBaseVisitor<Type> {
    static SymbolTable symbolTable = new SymbolTable();
    static SymbolTable curSymbolTable = symbolTable;
    static Type returnType = null;

    @Override
    public Type visitFuncDef(SysYParser.FuncDefContext ctx) {
        String funcName = ctx.IDENT().getText();
        if (curSymbolTable.getSymbol(funcName) != null) { // curScope为当前的作用域
            OutputHelper.printSemanticError(ErrorType.REDEF_FUNC, ctx.IDENT().getSymbol().getLine(),
                    ctx.IDENT().getText());
            return null;
        }

        Type retType = new Type(Type.TypeEnum.VOID_TYPE);
        String typeStr = ctx.getChild(0).getText();
        if (typeStr.equals("int")) {
            retType.type = Type.TypeEnum.INT_TYPE;     // 返回值类型为int32
        }


        curSymbolTable = new SymbolTable(curSymbolTable); // 新建一个符号表，作为函数参数和定义的作用域
        List<Type> paramTypes = new ArrayList<>();
        if (ctx.funcFParams() != null) { // 如有入参，处理形参，添加形参信息等
            for (SysYParser.FuncFParamContext param : ctx.funcFParams().funcFParam()) {
                Type paramType = visitFuncFParam(param);
                if (paramType == null ) { // 对于函数参数与已有参数重名，仅抛弃重复的参数
                    continue;
                }
                paramTypes.add(paramType);
                curSymbolTable.addSymbol(param.IDENT().getText(), paramType);
            }
        }

        FunctionType functionType = new FunctionType(retType, paramTypes);
        //顶层作用域中压入此函数
        curSymbolTable.parent.addSymbol(funcName, functionType);

        returnType = retType;
        visitBlock(ctx.block());
        returnType = null;
        curSymbolTable = curSymbolTable.parent;
        return null;
    }

    @Override
    public Type visitFuncFParam(SysYParser.FuncFParamContext ctx) { // 保证测试用例中的函数参数不会为多维（二维及以上）数组
        Type type = new Type(Type.TypeEnum.INT_TYPE);
        String paramName = ctx.IDENT().getText();
        if (curSymbolTable.getSymbolInCurrentScope(paramName) != null) {
            OutputHelper.printSemanticError(ErrorType.REDEF_VAR, ctx.IDENT().getSymbol().getLine(),
                    ctx.IDENT().getText());
            return null;
        }
        if (!ctx.L_BRACKT().isEmpty()) {
            for (int i = ctx.L_BRACKT().size() - 1; i >= 0; i--) {
                type = new ArrayType(type, 10, ctx.L_BRACKT().size() - i); // TODO: 数组大小暂时写死
            }
        }
        return type;
    }

    @Override
    public Type visitBlock(SysYParser.BlockContext ctx) {
        if (ctx.getParent() instanceof SysYParser.FuncDefContext) { // 如果是函数定义，需要将参数也放入该作用域
            for (SysYParser.BlockItemContext blockItem : ctx.blockItem()) {
                visit(blockItem);
            }
            return null;
        }

        curSymbolTable = new SymbolTable(curSymbolTable);
        for (SysYParser.BlockItemContext blockItem : ctx.blockItem()) {
            visit(blockItem);
        }
        curSymbolTable = curSymbolTable.parent;
        return null;
    }

    @Override
    public Type visitBlockItem(SysYParser.BlockItemContext ctx) {
        if (ctx.decl() != null) {
            visit(ctx.decl());
        } else if (ctx.stmt() != null) {
            visit(ctx.stmt());
        }
        return null;
    }

    @Override
    public Type visitDecl(SysYParser.DeclContext ctx) {
        if (ctx.constDecl() != null) {
            visit(ctx.constDecl());
        } else if (ctx.varDecl() != null) {
            visit(ctx.varDecl());
        }
        return null;
    }

    @Override
    public Type visitConstDecl(SysYParser.ConstDeclContext ctx) {
        for (SysYParser.ConstDefContext constDef : ctx.constDef()) {
            visit(constDef);
        }
        return null;
    }

    @Override
    public Type visitConstDef(SysYParser.ConstDefContext ctx) {
        if (curSymbolTable.getSymbolInCurrentScope(ctx.IDENT().getText()) != null) {
            OutputHelper.printSemanticError(ErrorType.REDEF_VAR, ctx.IDENT().getSymbol().getLine(),
                    ctx.IDENT().getText());
            return null;
        }
        Type type = new Type(Type.TypeEnum.INT_TYPE);
        if (!ctx.L_BRACKT().isEmpty()) {
            for (int i = ctx.L_BRACKT().size() - 1; i >= 0; i--) {
                type = new ArrayType(type, 10, ctx.L_BRACKT().size() - i); // TODO: 数组大小暂时写死
            }
            // 不要求对数组定义语句的右值进行语义检查

//            return type;
        }
        if (ctx.constInitVal() != null) {
            Type initType = visit(ctx.constInitVal());
            if (ctx.constInitVal() == null) {
                return null;
            }
            if (initType instanceof ArrayType) {
                OutputHelper.printSemanticError(ErrorType.ASSIGN_TYPE_MISMATCH, ctx.IDENT().getSymbol().getLine(),
                        ctx.IDENT().getText());
                return null;
            }
            if (initType instanceof FunctionType && ((FunctionType) initType).returnType.type != Type.TypeEnum.INT_TYPE) {
                OutputHelper.printSemanticError(ErrorType.ASSIGN_TYPE_MISMATCH, ctx.IDENT().getSymbol().getLine(),
                        ctx.IDENT().getText());
                return null;
            }
        }
        curSymbolTable.addSymbol(ctx.IDENT().getText(), type);

        return type;
    }

    @Override
    public Type visitConstInitVal(SysYParser.ConstInitValContext ctx) {
        if (ctx.constExp() != null) {
            return visit(ctx.constExp()); // TODO: 等一下写visitExp
        }
        return null;
    }

    @Override
    public Type visitConstExp(SysYParser.ConstExpContext ctx) {
        if (ctx.exp() != null) {
            return visit(ctx.exp());
        }
        return null;
    }

    @Override
    public Type visitVarDecl(SysYParser.VarDeclContext ctx) {
        for (SysYParser.VarDefContext varDef : ctx.varDef()) {
            visit(varDef);
        }
        return null;
    }

    @Override
    public Type visitVarDef(SysYParser.VarDefContext ctx) {
        if (curSymbolTable.getSymbolInCurrentScope(ctx.IDENT().getText()) != null) {
            OutputHelper.printSemanticError(ErrorType.REDEF_VAR, ctx.IDENT().getSymbol().getLine(),
                    ctx.IDENT().getText());
            return null;
        }
        Type type = new Type(Type.TypeEnum.INT_TYPE);
        if (!ctx.L_BRACKT().isEmpty()) {
            for (int i = ctx.L_BRACKT().size() - 1; i >= 0; i--) {
                type = new ArrayType(type, 10, ctx.L_BRACKT().size() - i); // TODO: 数组大小暂时写死
            }
//            return type;
        }
        if (ctx.initVal() != null && type.type != Type.TypeEnum.ARRAY_TYPE) {
            Type initType = visit(ctx.initVal());
            if (initType == null) {
                return null;
            }
            if (initType instanceof ArrayType) {
                OutputHelper.printSemanticError(ErrorType.ASSIGN_TYPE_MISMATCH, ctx.IDENT().getSymbol().getLine(),
                        ctx.IDENT().getText());
                return null;
            }
            if (initType instanceof FunctionType && ((FunctionType) initType).returnType.type != Type.TypeEnum.INT_TYPE) {
                OutputHelper.printSemanticError(ErrorType.ASSIGN_TYPE_MISMATCH, ctx.IDENT().getSymbol().getLine(),
                        ctx.IDENT().getText());
                return null;
            }
        }
        curSymbolTable.addSymbol(ctx.IDENT().getText(), type);
        return type;
    }

    @Override
    public Type visitInitVal(SysYParser.InitValContext ctx) {
        if (ctx.exp() != null) {
            return visit(ctx.exp());
        }
        return null;
    }

    @Override
    public Type visitStmt(SysYParser.StmtContext ctx) {
        if (ctx.RETURN() != null) {
            Type thisReturnType;
            if (ctx.exp() != null) {
                thisReturnType = visit(ctx.exp());
            } else {
                thisReturnType = new Type(Type.TypeEnum.VOID_TYPE);
            }
            if (thisReturnType != null && thisReturnType.type != returnType.type) {
                OutputHelper.printSemanticError(ErrorType.RETURN_TYPE_MISMATCH, ctx.RETURN().getSymbol().getLine(), "");
                return null;
            }

            return thisReturnType;
        } else if (ctx.ASSIGN() != null) {
            Type leftType = visit(ctx.lVal());
            Type rightType = visit(ctx.exp());
            if (leftType == null || rightType == null) {
                return null;
            }
            if (leftType instanceof ArrayType) {
                if (rightType instanceof ArrayType && ((ArrayType) leftType).dimension == ((ArrayType) rightType).dimension) {
                    return null;
                } else {
                    OutputHelper.printSemanticError(ErrorType.ASSIGN_TYPE_MISMATCH, ctx.ASSIGN().getSymbol().getLine(), "");
                }
            } else if (leftType instanceof FunctionType) {
                OutputHelper.printSemanticError(ErrorType.ASSIGN_TO_NON_VAR, ctx.ASSIGN().getSymbol().getLine(), "");
            } else if (leftType.type != rightType.type) {
                OutputHelper.printSemanticError(ErrorType.ASSIGN_TYPE_MISMATCH, ctx.ASSIGN().getSymbol().getLine(), "");
            }
        } else {
            try {
                if (ctx.exp() != null) {
                    visitExp(ctx.exp());
                }

            } catch (Exception e) {
                System.out.println(e);
            }
            if (ctx.block() != null) {
                visitBlock(ctx.block());
            }


            if (!ctx.stmt().isEmpty()) {
                for (SysYParser.StmtContext stmt : ctx.stmt()) {
                    visitStmt(stmt);
                }
            }
            if (ctx.cond() != null) {
                visitCond(ctx.cond());
            }
        }

        return null;
    }

    @Override
    public Type visitLVal(SysYParser.LValContext ctx) {
        Type symbol = curSymbolTable.getSymbol(ctx.IDENT().getText());
        if (symbol == null) {
            OutputHelper.printSemanticError(ErrorType.NODEF_VAR, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
            return null;
        }
        if (!ctx.exp().isEmpty()) {
            if (!ctx.exp().isEmpty() && (!(symbol instanceof ArrayType) || ctx.exp().size() > ((ArrayType)symbol).dimension)) {
                OutputHelper.printSemanticError(ErrorType.INDEX_ON_NON_ARRAY, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
                return null;
            }
            for (int i = 0; i < ctx.exp().size(); i++) {
                Type expType = visit(ctx.exp(i));
                if (expType == null) {
                    return null;
                }
                if (expType.type != Type.TypeEnum.INT_TYPE) {
                    OutputHelper.printSemanticError(ErrorType.OPERAND_TYPE_MISMATCH, ctx.exp(i).getStart().getLine(), "");
                    return null;
                }
                symbol = ((ArrayType) symbol).elementtype;
            }
            return symbol;
        }

        return symbol;

    }

    @Override
    public Type visitExp(SysYParser.ExpContext ctx) {
        if (ctx.lVal() != null) {
            return visit(ctx.lVal());
        } else if (ctx.L_PAREN() != null && ctx.exp().size() == 1) {
            return visit(ctx.exp().get(0));
        } else if (ctx.number() != null) {
            return new Type(Type.TypeEnum.INT_TYPE);
        } else if (ctx.IDENT() != null) {
            Type symbol = curSymbolTable.getSymbol(ctx.IDENT().getText());
            FunctionType functionType = (FunctionType)curSymbolTable.getSymbol(ctx.IDENT().getText(), Type.TypeEnum.FUNCTION_TYPE);
            if (symbol != null && symbol.type != Type.TypeEnum.FUNCTION_TYPE) {
                OutputHelper.printSemanticError(ErrorType.CALL_NON_FUNC, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
                return null;
            }

            if (functionType == null) {
                OutputHelper.printSemanticError(ErrorType.NODEF_FUNC, ctx.IDENT().getSymbol().getLine(), ctx.IDENT().getText());
                return null;
            }
            if (!functionType.paramTypes.isEmpty() || ctx.funcRParams() != null) {
                if (ctx.funcRParams() == null || ctx.funcRParams().param().size() != functionType.paramTypes.size()) {
                    OutputHelper.printSemanticError(ErrorType.FUNC_ARG_MISMATCH, ctx.IDENT().getSymbol().getLine(), "");
                    return null;
                }
                for (int i = 0; i < ctx.funcRParams().param().size(); i++) {
                    Type paramType = visit(ctx.funcRParams().param(i)); // TODO: visitParam
                    if (paramType == null) {
                        return null;
                    }
                    if (paramType.type != functionType.paramTypes.get(i).type) {
                        OutputHelper.printSemanticError(ErrorType.FUNC_ARG_MISMATCH, ctx.IDENT().getSymbol().getLine(), "");
                        return null;
                    }
                }
            }
            return functionType.returnType;
        } else if (!ctx.exp().isEmpty()) { // 假设所有的运算符都对int类型进行计算
            for (int i = 0; i < ctx.exp().size(); i++) {
                Type expType = visit(ctx.exp(i));
                if (expType == null) {
                    return null;
                }
                if (expType.type != Type.TypeEnum.INT_TYPE) {
                    OutputHelper.printSemanticError(ErrorType.OPERAND_TYPE_MISMATCH, ctx.exp(i).getStart().getLine(), "");
                    return null;
                }
            }
            return new Type(Type.TypeEnum.INT_TYPE);
        } else {
            return null;
        }
    }

    @Override
    public Type visitFuncRParams(SysYParser.FuncRParamsContext ctx) {
        for (SysYParser.ParamContext param : ctx.param()) {
            visit(param);
        }
        return null;
    }

    @Override
    public Type visitParam(SysYParser.ParamContext ctx) {
        if (ctx.exp() != null) {
            return visit(ctx.exp());
        }
        return null;
    }

    @Override
    public Type visitCond(SysYParser.CondContext ctx) {
        if (ctx.exp() != null) {
            return visit(ctx.exp());
        } else {
            Type leftType = visit(ctx.cond(0));
            Type rightType = visit(ctx.cond(1));
            if (leftType != null && rightType != null) {
                if (leftType.type != rightType.type || (leftType instanceof ArrayType && rightType instanceof ArrayType && ((ArrayType) leftType).dimension != ((ArrayType) rightType).dimension)) {
                    OutputHelper.printSemanticError(ErrorType.OPERAND_TYPE_MISMATCH, ctx.cond(0).getStart().getLine(), "");
                    return null;
                }
            }
            return new Type(Type.TypeEnum.INT_TYPE);
        }
    }


}
