import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class MyLLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef> {

    private String targetPath;

    public LLVMModuleRef module = LLVMModuleCreateWithName("module");

    private LLVMBuilderRef builder = LLVMCreateBuilder();

    private LLVMTypeRef i32Type = LLVMInt32Type();

    private LLVMTypeRef voidType = LLVMVoidType();

    private LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);

    private LLVMValueRef currentFunction;

    private HashMap<String, LLVMValueRef> symbolTable = new HashMap<>();

    private HashMap<String, LLVMTypeRef> funcTable = new HashMap<>();


    public static final BytePointer error = new BytePointer();

    private LLVMBasicBlockRef currentBreakTarget;

    private LLVMBasicBlockRef currentContinueTarget;

    public MyLLVMVisitor(String target) {
        // Initialize LLVM
        LLVMLinkInMCJIT();
        LLVMInitializeNativeTarget();
        LLVMInitializeNativeAsmPrinter();
        targetPath = target;
    }


    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        // Visit all compilation units
        super.visitProgram(ctx);

        Optimizer optimizer = new Optimizer(module);
        optimizer.run();

        // 写入输出文件
        if (LLVMPrintModuleToFile(module, targetPath, error) != 0) {
            LLVMDisposeMessage(error);
        }



        return null;
    }

    @Override
    public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
        String varName = ctx.IDENT().getText();
        LLVMValueRef initVal = visit(ctx.constInitVal());

        if (currentFunction == null) {
            // Global constant
            LLVMValueRef global = LLVMAddGlobal(module, i32Type, varName);
            LLVMSetInitializer(global, initVal);
            LLVMSetGlobalConstant(global, 1);
            LLVMSetInitializer(global, initVal);
            return global;
        } else {
            // Local constant (treated same as variable in LLVM)
            LLVMValueRef alloca = LLVMBuildAlloca(builder, i32Type, varName);
            LLVMBuildStore(builder, initVal, alloca);
            symbolTable.put(varName, alloca);
            return alloca;
        }
    }

    @Override
    public LLVMValueRef visitConstInitVal(SysYParser.ConstInitValContext ctx) {
        if (ctx.constExp() != null) {
            return visit(ctx.constExp());
        }
        // Handle array initialization (simplified)
        return LLVMConstInt(i32Type, 0, 0);
    }


    @Override
    public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
        String varName = ctx.IDENT().getText();

        if (currentFunction == null) {
            // Global variable
            LLVMValueRef initVal = null;
            if (ctx.initVal() != null) {
                initVal = visit(ctx.initVal());
            } else {
                initVal = LLVMConstInt(i32Type, 0, 0); // Default to 0
            }
            LLVMValueRef global = LLVMAddGlobal(module, i32Type, varName);
            LLVMSetInitializer(global, initVal);
            return global;
        } else {
            // Local variable
            LLVMValueRef alloca = LLVMBuildAlloca(builder, i32Type, varName);

            LLVMValueRef initVal = null;
            if (ctx.initVal() != null) {
                initVal = visit(ctx.initVal());
            } else {
                initVal = LLVMConstInt(i32Type, 0, 0); // Default to 0
            }

            LLVMBuildStore(builder, initVal, alloca);
            symbolTable.put(varName, alloca);
            return alloca;
        }
    }

    @Override
    public LLVMValueRef visitInitVal(SysYParser.InitValContext ctx) {
        if (ctx.exp() != null) {
            return visit(ctx.exp());
        }
        // Handle array initialization (simplified)
        return LLVMConstInt(i32Type, 0, 0);
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {

        symbolTable = new HashMap<>();

        // Get function name and return type
        String funcName = ctx.IDENT().getText();

        LLVMTypeRef returnType = ctx.funcType().VOID() != null ? voidType : i32Type;

        // Handle function parameters
        int paramsNum = ctx.funcFParams() != null ? ctx.funcFParams().funcFParam().size() : 0;
        PointerPointer<Pointer> paramTypes = new PointerPointer<>(paramsNum);
        if (ctx.funcFParams() != null) {
            for(int i = 0; i < paramsNum; i++){
                paramTypes.put(i, i32Type);
            }
        }

        // Create function type (return type, param types, isVarArg)
        LLVMTypeRef funcType = LLVMFunctionType(returnType, paramTypes, paramsNum, 0);

        // Create function
        currentFunction = LLVMAddFunction(module, funcName, funcType);

        // Create entry basic block
        LLVMBasicBlockRef entryBlock = LLVMAppendBasicBlock(currentFunction, funcName + "Entry");
        LLVMPositionBuilderAtEnd(builder, entryBlock);

        // Allocate and store parameters
        if (ctx.funcFParams() != null) {
            int paramIdx = 0;
            for (SysYParser.FuncFParamContext param : ctx.funcFParams().funcFParam()) {
                String paramName = param.IDENT().getText();
                LLVMValueRef alloca = LLVMBuildAlloca(builder, i32Type, paramName);
                LLVMValueRef paramVal = LLVMGetParam(currentFunction, paramIdx);
                LLVMBuildStore(builder, paramVal, alloca);
                paramIdx++;
                symbolTable.put(paramName, alloca);
            }
        }

        funcTable.put(funcName, funcType);

        // Visit function body
        visit(ctx.block());

        // Add implicit void return if needed
        if (ctx.funcType().VOID() != null &&
                LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildRetVoid(builder);
        }

        // If still no terminator, insert unreachable
        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildUnreachable(builder);
        }

        symbolTable = null;
        currentFunction = null;

        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        // Visit all statements in the block
        return visitChildren(ctx);
    }

    @Override
    public LLVMValueRef visitStmt(SysYParser.StmtContext ctx) {
        if (ctx.ASSIGN() != null) {
            // Assignment statement
            String varName = ctx.lVal().IDENT().getText();
            LLVMValueRef exp = visit(ctx.exp());

            if (symbolTable.get(varName) != null){
                LLVMBuildStore(builder, exp, symbolTable.get(varName));
            } else {
                LLVMBuildStore(builder, exp, LLVMGetNamedGlobal(module, varName));
            }
            return null;

        } else if (ctx.RETURN() != null) {
            // Return statement
            if (ctx.exp() != null) {
                LLVMValueRef retVal = visit(ctx.exp());
                return LLVMBuildRet(builder, retVal);
            } else {
                return LLVMBuildRetVoid(builder);
            }

        } else if (ctx.IF() != null){
            // If statement
            LLVMValueRef cond = visit(ctx.cond());
            LLVMBasicBlockRef thenBlock = LLVMAppendBasicBlock(currentFunction, "true");
            LLVMBasicBlockRef elseBlock = LLVMAppendBasicBlock(currentFunction, "false");
            LLVMBasicBlockRef mergeBlock = LLVMAppendBasicBlock(currentFunction, "merge");

            LLVMValueRef neg_cond = LLVMBuildICmp(builder, LLVMIntNE, zero, cond, "tmp");

            LLVMBuildCondBr(builder, neg_cond, thenBlock, elseBlock);

            // Then block
            LLVMPositionBuilderAtEnd(builder, thenBlock);
            visit(ctx.stmt(0));
            LLVMBuildBr(builder, mergeBlock);

            // Else block
            LLVMPositionBuilderAtEnd(builder, elseBlock);
            if (ctx.stmt().size() > 1) {
                visit(ctx.stmt(1));
            }
            LLVMBuildBr(builder, mergeBlock);

            // Merge block
            LLVMPositionBuilderAtEnd(builder, mergeBlock);

            return null;

        } else if (ctx.exp() != null) {
            // Expression statement
            return visit(ctx.exp());

        } else if (ctx.block() != null) {
            // Block statement
            return visit(ctx.block());

        } else if (ctx.WHILE() != null) {
            LLVMValueRef func = LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder));

            // Save current break/continue targets
            LLVMBasicBlockRef oldBreakTarget = currentBreakTarget;
            LLVMBasicBlockRef oldContinueTarget = currentContinueTarget;

            // Create blocks
            LLVMBasicBlockRef condBlock = LLVMAppendBasicBlock(func, "while_cond");
            LLVMBasicBlockRef bodyBlock = LLVMAppendBasicBlock(func, "while_body");
            LLVMBasicBlockRef endBlock = LLVMAppendBasicBlock(func, "while_end");

            // Set new break/continue targets
            currentBreakTarget = endBlock;
            currentContinueTarget = condBlock;

            // Jump to condition block
            LLVMBuildBr(builder, condBlock);

            // Emit condition block
            LLVMPositionBuilderAtEnd(builder, condBlock);
            LLVMValueRef cond = visit(ctx.cond());
            LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
            LLVMValueRef boolCond = LLVMBuildICmp(builder, LLVMIntNE, cond, zero, "while_cond");
            LLVMBuildCondBr(builder, boolCond, bodyBlock, endBlock);

            // Emit body block
            LLVMPositionBuilderAtEnd(builder, bodyBlock);
            visit(ctx.stmt(0));
            if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
                LLVMBuildBr(builder, condBlock); // Loop back to condition
            }

            // Position builder at end block
            LLVMPositionBuilderAtEnd(builder, endBlock);

            // Restore old break/continue targets
            currentBreakTarget = oldBreakTarget;
            currentContinueTarget = oldContinueTarget;

            return null;

        } else if (ctx.BREAK() != null) {
            // Break statement
            if (currentBreakTarget != null) {
                LLVMBuildBr(builder, currentBreakTarget);
                // Create unreachable block for code after break
                LLVMBasicBlockRef unreachable = LLVMAppendBasicBlock(
                        LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder)),
                        "unreachable");
                LLVMPositionBuilderAtEnd(builder, unreachable);
            }
            return null;

        } else if (ctx.CONTINUE() != null) {
            // Continue statement
            if (currentContinueTarget != null) {
                LLVMBuildBr(builder, currentContinueTarget);
                // Create unreachable block for code after continue
                LLVMBasicBlockRef unreachable = LLVMAppendBasicBlock(
                        LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder)),
                        "unreachable");
                LLVMPositionBuilderAtEnd(builder, unreachable);
            }
            return null;
        }
        return null;
    }


    @Override
    public LLVMValueRef visitExp(SysYParser.ExpContext ctx) {
        if (ctx.lVal() != null) {
            return visit(ctx.lVal());

        } else if (ctx.number() != null) {
            return visit(ctx.number());

        } else if (ctx.IDENT() != null) {
            // Function call
            // arguments
            LLVMValueRef[] args = new LLVMValueRef[0];
            if (ctx.funcRParams() != null) {
                args = new LLVMValueRef[ctx.funcRParams().param().size()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = visit(ctx.funcRParams().param(i));
                }
            }

            PointerPointer<Pointer> argumentList = new PointerPointer<>(args);
            LLVMValueRef func = LLVMGetNamedFunction(module, ctx.IDENT().getText());

            LLVMTypeRef returnType = LLVMGetReturnType(LLVMGetElementType(LLVMTypeOf(func)));
            if (LLVMGetTypeKind(returnType) == LLVMVoidTypeKind) {
                return LLVMBuildCall2(builder, funcTable.get(ctx.IDENT().getText()), func, argumentList, args.length, "");
            }
            return LLVMBuildCall2(builder, funcTable.get(ctx.IDENT().getText()), func, argumentList, args.length, "return_tmp");

        } else if (ctx.unaryOp() != null) {
            LLVMValueRef val = visitExp(ctx.exp(0));
            if (ctx.unaryOp().MINUS() != null) {
                return LLVMBuildNeg(builder, val, "neg_tmp");
            } else if (ctx.unaryOp().NOT() != null) {
                LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
                LLVMValueRef cmp = LLVMBuildICmp(builder, LLVMIntNE, val, zero, "tmp");
                LLVMValueRef tmp = LLVMBuildXor(builder, cmp, LLVMConstInt(LLVMInt1Type(), 1, 0), "tmp");
                return LLVMBuildZExt(builder, tmp, i32Type, "tmp");
            }
            return val; // For PLUS unary op

        } else if (ctx.MUL() != null || ctx.DIV() != null || ctx.MOD() != null) {
            LLVMValueRef left = visit(ctx.exp(0));
            LLVMValueRef right = visit(ctx.exp(1));
            if (ctx.MUL() != null) {
                return LLVMBuildMul(builder, left, right, "mul_tmp");
            } else if (ctx.DIV() != null) {
                return LLVMBuildSDiv(builder, left, right, "div_tmp");
            } else {
                return LLVMBuildSRem(builder, left, right, "mod_tmp");
            }

        } else if (ctx.PLUS() != null || ctx.MINUS() != null) {
            LLVMValueRef left = visit(ctx.exp(0));
            LLVMValueRef right = visit(ctx.exp(1));
            if (ctx.PLUS() != null) {
                return LLVMBuildAdd(builder, left, right, "add_tmp");
            } else {
                return LLVMBuildSub(builder, left, right, "sub_tmp");
            }

        } else if (ctx.L_PAREN() != null) {
            return visit(ctx.exp(0)); // Parenthesized expression
        }
        return null;
    }

    @Override
    public LLVMValueRef visitCond(SysYParser.CondContext ctx) {
        if (ctx.AND() != null) {
            // 创建基本块
            LLVMValueRef func = LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder));
            LLVMBasicBlockRef rightBlock = LLVMAppendBasicBlock(func, "and_right");
            LLVMBasicBlockRef mergeBlock = LLVMAppendBasicBlock(func, "and_merge");

            // 计算左操作数
            LLVMValueRef left = visit(ctx.cond(0));
            LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
            LLVMValueRef leftBool = LLVMBuildICmp(builder, LLVMIntNE, left, zero, "and_left");

            // 分配结果变量
            LLVMValueRef result = LLVMBuildAlloca(builder, i32Type, "and_result");
            LLVMBuildStore(builder, zero, result); // 默认false

            // 条件分支
            LLVMBuildCondBr(builder, leftBool, rightBlock, mergeBlock);

            // 右操作数块
            LLVMPositionBuilderAtEnd(builder, rightBlock);
            LLVMValueRef right = visit(ctx.cond(1));
            LLVMValueRef rightBool = LLVMBuildICmp(builder, LLVMIntNE, right, zero, "and_right");

            // 存储右操作数结果
            // 将i1转换为i32后再存储
            LLVMValueRef rightBoolExt = LLVMBuildZExt(builder, rightBool, i32Type, "and_right_ext");
            LLVMBuildStore(builder, rightBoolExt, result);
            LLVMBuildBr(builder, mergeBlock);

            // 合并块
            LLVMPositionBuilderAtEnd(builder, mergeBlock);
            return LLVMBuildLoad2(builder, i32Type, result, "and_result");

        } else if (ctx.OR() != null) {
            // 创建基本块
            LLVMValueRef func = LLVMGetBasicBlockParent(LLVMGetInsertBlock(builder));
            LLVMBasicBlockRef rightBlock = LLVMAppendBasicBlock(func, "or_right");
            LLVMBasicBlockRef mergeBlock = LLVMAppendBasicBlock(func, "or_merge");

            // 计算左操作数
            LLVMValueRef left = visit(ctx.cond(0));
            LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
            LLVMValueRef one = LLVMConstInt(i32Type, 1, 0);
            LLVMValueRef leftBool = LLVMBuildICmp(builder, LLVMIntNE, left, zero, "or_left");

            // 分配结果变量
            LLVMValueRef result = LLVMBuildAlloca(builder, i32Type, "or_result");
            LLVMBuildStore(builder, one, result); // 默认true

            // 条件分支
            LLVMBuildCondBr(builder, leftBool, mergeBlock, rightBlock);

            // 右操作数块
            LLVMPositionBuilderAtEnd(builder, rightBlock);
            LLVMValueRef right = visit(ctx.cond(1));
            LLVMValueRef rightBool = LLVMBuildICmp(builder, LLVMIntNE, right, zero, "or_right");

            // 存储右操作数结果
            // 将i1转换为i32后再存储
            LLVMValueRef rightBoolExt = LLVMBuildZExt(builder, rightBool, i32Type, "or_right_ext");
            LLVMBuildStore(builder, rightBoolExt, result);
            LLVMBuildBr(builder, mergeBlock);

            // 合并块
            LLVMPositionBuilderAtEnd(builder, mergeBlock);
            return LLVMBuildLoad2(builder, i32Type, result, "or_result");

        } else if (ctx.getChildCount() == 1) {
            return visit(ctx.getChild(0));

        } else {
            // 处理关系表达式
            LLVMValueRef left = visit(ctx.cond(0));
            LLVMValueRef right = visit(ctx.cond(1));

            int pred;
            if (ctx.LT() != null) pred = LLVMIntSLT;
            else if (ctx.GT() != null) pred = LLVMIntSGT;
            else if (ctx.LE() != null) pred = LLVMIntSLE;
            else if (ctx.GE() != null) pred = LLVMIntSGE;
            else if (ctx.EQ() != null) pred = LLVMIntEQ;
            else pred = LLVMIntNE; // NEQ

            LLVMValueRef cmp = LLVMBuildICmp(builder, pred, left, right, "cmp_tmp");
            return LLVMBuildZExt(builder, cmp, i32Type, "cmp_ext");
        }
    }


    @Override
    public LLVMValueRef visitNumber(SysYParser.NumberContext ctx) {
        int value = 0;
        if (ctx.getText().startsWith("0x")) {
            value = Integer.parseInt(ctx.getText().substring(2), 16);
        } else if (ctx.getText().startsWith("0")) {
            value = Integer.parseInt(ctx.getText(), 8);
        } else {
            value = Integer.parseInt(ctx.getText());
        }
        return LLVMConstInt(i32Type, value, 0);
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        String varName = ctx.IDENT().getText();

        LLVMValueRef value;
        if (symbolTable.get(varName) != null){
            value = LLVMBuildLoad(builder, symbolTable.get(varName), varName);
        } else {
            value = LLVMBuildLoad(builder, LLVMGetNamedGlobal(module, varName), varName);
        }
        return value;

    }

}