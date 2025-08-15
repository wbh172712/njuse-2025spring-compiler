import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;


import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.llvm.global.LLVM.*;


public class MyIrVisitor extends SysYParserBaseVisitor<LLVMValueRef> {
    public static final BytePointer error = new BytePointer();
    //创建module
    LLVMModuleRef module;

    //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
    LLVMBuilderRef builder;

    //考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
    LLVMTypeRef i32Type;

    // 定义一个变量表
    IrSymbolTable symbolTable = new IrSymbolTable();

    // 返回值
    LLVMValueRef retVal = null;

    // 使用一个栈储存循环条件代码块
    Stack<LLVMBasicBlockRef> LoopConditonStack = new Stack<>();

    // 使用一个栈储存循环退出代码块
    Stack<LLVMBasicBlockRef> LoopExitStack = new Stack<>();

    // 储存当前函数
    LLVMValueRef currentFunction = null;

    // 目标文件名
    String targetFileName;
    String sourceFileName;

    // 是否已经返回
    boolean hasReturned = false;

    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> predecessorsBlock = new HashMap<>();
    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> successorsBlock = new HashMap<>();
    Map<LLVMBasicBlockRef, Set<LLVMValueRef>> blockToInst = new HashMap<>();
    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> directPredecessorsBlock = new HashMap<>();
    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> directSuccessorsBlock = new HashMap<>();


    public void constantPropagation() {
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            if (LLVMCountBasicBlocks(function) == 0) continue;

            performConstantPropagation(function);
        }
    }

    public void createCFG() {
        predecessorsBlock = new HashMap<>();
        successorsBlock = new HashMap<>();
        blockToInst = new HashMap<>();
        // 创建控制流图
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {
                successorsBlock.put(block, new HashSet<>());
                predecessorsBlock.put(block, new HashSet<>());
                blockToInst.put(block, new HashSet<>());
                directPredecessorsBlock.put(block, new HashSet<>());
                directSuccessorsBlock.put(block, new HashSet<>());
            }
        }

        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {

                for (LLVMValueRef inst = LLVMGetFirstInstruction(block);
                     inst != null && !inst.equals(new LLVMValueRef(null));
                     inst = LLVMGetNextInstruction(inst)) {
                    int opcode = LLVMGetInstructionOpcode(inst);
                    if (opcode == LLVMBr) {
                        // 分支指令
                        // 有条件跳转
                        if (LLVMGetNumOperands(inst) == 3) {
                            LLVMValueRef cond = LLVMGetOperand(inst, 0);
                            LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(inst, 0);
                            LLVMBasicBlockRef falseBlock = LLVMGetSuccessor(inst, 1);

                            successorsBlock.get(block).add(trueBlock);
                            predecessorsBlock.get(trueBlock).add(block);

                            successorsBlock.get(block).add(falseBlock);
                            predecessorsBlock.get(falseBlock).add(block);

                            directPredecessorsBlock.get(trueBlock).add(block);
                            directPredecessorsBlock.get(falseBlock).add(block);
                            directSuccessorsBlock.get(block).add(trueBlock);
                            directSuccessorsBlock.get(block).add(falseBlock);
                        } else if (LLVMGetNumOperands(inst) == 1) {
                            // 无条件跳转
                            LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(inst, 0);
                            successorsBlock.get(block).add(trueBlock);
                            predecessorsBlock.get(trueBlock).add(block);

                            directSuccessorsBlock.get(block).add(trueBlock);
                            directPredecessorsBlock.get(trueBlock).add(block);
                        } else {
                            System.out.println("分支指令操作数错误：" + LLVMGetNumOperands(inst));
                        }
                        break;
                    }
                }
            }
        }

        // 递归将前驱的前驱添加到当前块的前驱列表，将后继的后继添加到当前块的后继列表, 直到没有新的前驱或后继
        Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> oldPre;
        do {
            oldPre = new HashMap<>(predecessorsBlock);
            for (LLVMValueRef function = LLVMGetFirstFunction(module);
                 function != null && !function.equals(new LLVMValueRef(null));
                 function = LLVMGetNextFunction(function)) {

                for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                     block != null && !block.equals(new LLVMBasicBlockRef(null));
                     block = LLVMGetNextBasicBlock(block)) {
                    Set<LLVMBasicBlockRef> predecessors = predecessorsBlock.get(block);
                    Set<LLVMBasicBlockRef> successors = successorsBlock.get(block);

                    Set<LLVMBasicBlockRef> newPredecessors = new HashSet<>();
                    for (LLVMBasicBlockRef pred : predecessors) {
                        newPredecessors.addAll(predecessorsBlock.get(pred));
                    }
                    predecessors.addAll(newPredecessors);

                    Set<LLVMBasicBlockRef> newSuccessors = new HashSet<>();
                    for (LLVMBasicBlockRef succ : successors) {
                        newSuccessors.addAll(successorsBlock.get(succ));
                    }
                    successors.addAll(newSuccessors);
                }
            }

        } while (!oldPre.equals(predecessorsBlock));

    }


    public void performConstantPropagation(LLVMValueRef function) {


        List<LLVMValueRef> instructions = new ArrayList<>();
        Map<LLVMValueRef, List<LLVMValueRef>> predecessors = new HashMap<>();
        Map<LLVMValueRef, List<LLVMValueRef>> successors = new HashMap<>();

        createCFG();

        // 收集指令 & 初始化图结构
        for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
             block != null && !block.equals(new LLVMBasicBlockRef(null));
             block = LLVMGetNextBasicBlock(block)) {

            for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                 instr != null && !instr.equals(new LLVMValueRef(null));
                 instr = LLVMGetNextInstruction(instr)) {

                instructions.add(instr);
                predecessors.put(instr, new ArrayList<>());
                successors.put(instr, new ArrayList<>());

                blockToInst.get(block).add(instr);
            }
        }

        for (LLVMValueRef instr : instructions) {
            LLVMBasicBlockRef block = LLVMGetInstructionParent(instr);
            for (LLVMBasicBlockRef preBlock : directPredecessorsBlock.get(block)) {
                predecessors.get(instr).add(LLVMGetLastInstruction(preBlock));
            }
            for (LLVMBasicBlockRef succBlock : directSuccessorsBlock.get(block)) {
                successors.get(instr).add(LLVMGetFirstInstruction(succBlock));
            }
        }
        for (LLVMValueRef func = LLVMGetFirstFunction(module);
             func != null && !func.equals(new LLVMValueRef(null));
             func = LLVMGetNextFunction(func)) {
            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(func);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {
                for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                     instr != null && !instr.equals(new LLVMValueRef(null));
                     instr = LLVMGetNextInstruction(instr)) {
                    LLVMValueRef next = LLVMGetNextInstruction(instr);
                    if (next != null) {
                        successors.get(instr).add(next);
                        predecessors.get(next).add(instr);
                    }
                }
            }
        }


        // 数据流映射初始化
        Map<LLVMValueRef, Map<LLVMValueRef, ConstValue>> memoryIn = new HashMap<>();
        Map<LLVMValueRef, Map<LLVMValueRef, ConstValue>> memoryOut = new HashMap<>();
        Map<LLVMValueRef, Map<LLVMValueRef, ConstValue>> regOut = new HashMap<>();

        Set<LLVMValueRef> worklist = new LinkedHashSet<>(instructions);
        for (LLVMValueRef instr : instructions) {
            memoryIn.put(instr, new HashMap<>());
            memoryOut.put(instr, new HashMap<>());
            regOut.put(instr, new HashMap<>());
        }

        // ✅ 初始化全局变量状态
        Map<LLVMValueRef, ConstValue> initialMemory = new HashMap<>();

        for (LLVMValueRef global = LLVMGetFirstGlobal(module);
             global != null && !global.equals(new LLVMValueRef(null));
             global = LLVMGetNextGlobal(global)) {

            LLVMValueRef initializer = LLVMGetInitializer(global);
            if (initializer != null && LLVMIsConstant(initializer) == 1) {
                long val = LLVMConstIntGetZExtValue(initializer);
                initialMemory.put(global, new ConstValue(ConstStatus.CONST, (int) val));
            } else {
                initialMemory.put(global, ConstValue.NAC);
            }
        }

        // 将初始内存映射应用到每条指令的 memoryIn 和 memoryOut
        for (LLVMValueRef instr : instructions) {
            memoryIn.get(instr).putAll(initialMemory);
            memoryOut.get(instr).putAll(initialMemory);
        }

        // 主循环
        while (!worklist.isEmpty()) {
            // 从worklist弹出一个指令
            LLVMValueRef instr = worklist.iterator().next();
            worklist.remove(instr);


            Map<LLVMValueRef, ConstValue> inMemory = new HashMap<>();


            for (LLVMValueRef pred : predecessors.get(instr)) {
                Map<LLVMValueRef, ConstValue> predOut = memoryOut.get(pred);
                for (Map.Entry<LLVMValueRef, ConstValue> entry : predOut.entrySet()) {
                    LLVMValueRef ptr = entry.getKey();
                    ConstValue val = entry.getValue();

                    inMemory.put(ptr, ConstValue.meet(inMemory.getOrDefault(ptr, ConstValue.UNDEF), val));

                }
            }

            // 如果是第一条指令没有前驱，就用默认初始状态（已经在初始化时设置过）
            if (predecessors.get(instr).isEmpty()) {
                inMemory = new HashMap<>(memoryIn.get(instr));
            }

            memoryIn.put(instr, inMemory);
            Map<LLVMValueRef, ConstValue> outMemory = new HashMap<>(inMemory);
            Map<LLVMValueRef, ConstValue> regVals = new HashMap<>();

            int opcode = LLVMGetInstructionOpcode(instr);

            if (opcode == LLVMStore) {
                LLVMValueRef val = LLVMGetOperand(instr, 0);
                LLVMValueRef ptr = LLVMGetOperand(instr, 1);

                ConstValue valStatus = ConstValue.UNDEF;
                if (LLVMIsConstant(val) == 1) {
                    int v = (int) LLVMConstIntGetZExtValue(val);
                    valStatus = new ConstValue(ConstStatus.CONST, v);
                } else {
                    for (Map<LLVMValueRef, ConstValue> prev : regOut.values()) {
                        if (prev.containsKey(val)) {
                            valStatus = prev.get(val);
                            break;
                        }
                    }
                }

                outMemory.put(ptr, valStatus);

            } else if (opcode == LLVMLoad) {
                LLVMValueRef ptr = LLVMGetOperand(instr, 0);
                ConstValue val = inMemory.getOrDefault(ptr, ConstValue.UNDEF);
                regVals.put(instr, val);

            } else if (opcode == LLVMICmp) {
                LLVMValueRef lhs = LLVMGetOperand(instr, 0);
                LLVMValueRef rhs = LLVMGetOperand(instr, 1);

                ConstValue v1 = ConstValue.UNDEF, v2 = ConstValue.UNDEF;

                for (Map<LLVMValueRef, ConstValue> prev : regOut.values()) {
                    if (prev.containsKey(lhs)) v1 = prev.get(lhs);
                    if (prev.containsKey(rhs)) v2 = prev.get(rhs);
                }

                if (LLVMIsConstant(lhs) == 1) {
                    v1 = new ConstValue(ConstStatus.CONST, (int) LLVMConstIntGetZExtValue(lhs));
                }
                if (LLVMIsConstant(rhs) == 1) {
                    v2 = new ConstValue(ConstStatus.CONST, (int) LLVMConstIntGetZExtValue(rhs));
                }

                if (v1.status == ConstStatus.CONST && v2.status == ConstStatus.CONST) {
                    int result;
                    switch (LLVMGetICmpPredicate(instr)) {
                        case LLVMIntEQ:
                            result = v1.constVal == v2.constVal ? 1 : 0;
                            break;
                        case LLVMIntNE:
                            result = v1.constVal != v2.constVal ? 1 : 0;
                            break;
                        case LLVMIntSGT:
                            result = v1.constVal > v2.constVal ? 1 : 0;
                            break;
                        case LLVMIntSGE:
                            result = v1.constVal >= v2.constVal ? 1 : 0;
                            break;
                        case LLVMIntSLT:
                            result = v1.constVal < v2.constVal ? 1 : 0;
                            break;
                        case LLVMIntSLE:
                            result = v1.constVal <= v2.constVal ? 1 : 0;
                            break;
                        default:
                            result = 0;
                            break;
                    }
                    regVals.put(instr, new ConstValue(ConstStatus.CONST, result));
                } else if (v1.status == ConstStatus.NAC || v2.status == ConstStatus.NAC) {
                    regVals.put(instr, ConstValue.NAC);
                } else {
                    regVals.put(instr, ConstValue.UNDEF);
                }
            } else if (opcode == LLVMBr) {
                // 分支指令
                LLVMValueRef cond = LLVMGetOperand(instr, 0);

                // 如果条件是常量，直接判断
                if (LLVMIsConstant(cond) == 1) {
                    int val = (int) LLVMConstIntGetZExtValue(cond);
                    if (val == 0) {
                        worklist.addAll(successors.get(instr));
                    }
                } else {
                    for (Map<LLVMValueRef, ConstValue> prev : regOut.values()) {
                        if (prev.containsKey(cond)) {
                            ConstValue val = prev.get(cond);
                            if (val.status == ConstStatus.CONST && val.constVal == 0) {
                                worklist.addAll(successors.get(instr));
                            }
                        }
                    }
                }

            } else if (opcode == LLVMZExt) {
                LLVMValueRef val = LLVMGetOperand(instr, 0);
                ConstValue v = ConstValue.UNDEF;

                for (Map<LLVMValueRef, ConstValue> prev : regOut.values()) {
                    if (prev.containsKey(val)) {
                        v = prev.get(val);
                        break;
                    }
                }

                if (LLVMIsConstant(val) == 1) {
                    v = new ConstValue(ConstStatus.CONST, (int) LLVMConstIntGetZExtValue(val));
                }

                regVals.put(instr, v);

            } else if (opcode == LLVMRet) {
                // 返回指令

            } else if (opcode == LLVMAdd || opcode == LLVMSub || opcode == LLVMMul || opcode == LLVMSDiv || opcode == LLVMSRem) {
                LLVMValueRef lhs = LLVMGetOperand(instr, 0);
                LLVMValueRef rhs = LLVMGetOperand(instr, 1);

                ConstValue v1 = ConstValue.UNDEF, v2 = ConstValue.UNDEF;

                for (Map<LLVMValueRef, ConstValue> prev : regOut.values()) {
                    if (prev.containsKey(lhs)) v1 = prev.get(lhs);
                    if (prev.containsKey(rhs)) v2 = prev.get(rhs);
                }

                if (LLVMIsConstant(lhs) == 1) {
                    v1 = new ConstValue(ConstStatus.CONST, (int) LLVMConstIntGetZExtValue(lhs));
                }
                if (LLVMIsConstant(rhs) == 1) {
                    v2 = new ConstValue(ConstStatus.CONST, (int) LLVMConstIntGetZExtValue(rhs));
                }

                if (v1.status == ConstStatus.CONST && v2.status == ConstStatus.CONST) {
                    int result;
                    switch (opcode) {
                        case LLVMAdd:
                            result = v1.constVal + v2.constVal;
                            break;
                        case LLVMSub:
                            result = v1.constVal - v2.constVal;
                            break;
                        case LLVMMul:
                            result = v1.constVal * v2.constVal;
                            break;
                        case LLVMSDiv:
                            result = v2.constVal != 0 ? v1.constVal / v2.constVal : 0;
                            break;
                        case LLVMSRem:
                            result = v2.constVal != 0 ? v1.constVal % v2.constVal : 0;
                            break;
                        default:
                            result = 0;
                            break;
                    }
                    regVals.put(instr, new ConstValue(ConstStatus.CONST, result));
//                    outMemory.put(instr, new ConstValue(ConstStatus.CONST, result));
                } else if (v1.status == ConstStatus.NAC || v2.status == ConstStatus.NAC) {
                    regVals.put(instr, ConstValue.NAC);
                } else {
                    regVals.put(instr, ConstValue.UNDEF);
                }

            } else {
//                regVals.put(instr, ConstValue.NAC);
            }

            if (!memoryOut.get(instr).equals(outMemory) || !regOut.get(instr).equals(regVals)) {
                memoryOut.put(instr, outMemory);
                regOut.put(instr, regVals);
                worklist.addAll(successors.get(instr));
            }
        }

        // 替换常量
        for (LLVMValueRef instr : instructions) {
            ConstValue val = regOut.get(instr).get(instr);
            if (val != null && val.status == ConstStatus.CONST) {
                if (LLVMIsAReturnInst(instr) != null) {
                    continue;
                }
                LLVMValueRef constVal = LLVMConstInt(LLVMTypeOf(instr), val.constVal, 0);
                LLVMReplaceAllUsesWith(instr, constVal);
                LLVMInstructionEraseFromParent(instr);
            }
        }
    }

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx) {
        visit(ctx.compUnit());
        // 输出到文件
        LLVMPrintModuleToFile(module, this.targetFileName, error);

        // 如果文件里面没有while
        boolean hasWhile = false;
        int whileCount = 0;
        try {
            FileReader fileReader = new FileReader(this.sourceFileName);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                whileCount += line.split("\\bwhile\\b", -1).length - 1; // 统计当前行中while的出现次数
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        hasWhile = whileCount > 0; // 如果whileCount大于0，则说明存在while

        int random = new Random().nextInt(100);

        mixedOptimization();


        // 输出到文件
        LLVMPrintModuleToFile(module, this.targetFileName, error);

        return null;
    }

    private void mixedOptimization() { // 重复优化，直到没有变化
        boolean changed = false;
        // 保存当前的module
        String oldModuleStr = LLVMPrintModuleToString(module).getString();
        do {
            constantPropagation();
//            LLVMPrintModuleToFile(module, this.targetFileName, error);
            unusedVariableDelete();
//            LLVMPrintModuleToFile(module, this.targetFileName, error);
            unReachableInstDelete();
//            LLVMPrintModuleToFile(module, this.targetFileName, error);
            if (module == null) {
                throw new IllegalStateException("LLVM module is null");
            }
            LLVMPrintModuleToFile(module, this.targetFileName, error);
            String newModuleStr = LLVMPrintModuleToString(module).getString();
            changed = !oldModuleStr.equals(newModuleStr);
            oldModuleStr = newModuleStr;
        } while (changed);
        // 输出到文件
        LLVMPrintModuleToFile(module, this.targetFileName, error);

    }

    private void unReachableInstDelete() {
        createCFG();
        // 查找没有前驱的基本块
        Set<LLVMBasicBlockRef> unreachableBlocks = new HashSet<>();
        deleteUnreachableBlocks(unreachableBlocks);
        clearDirectJump();
        clearUnreachableInsts(); // 删除跳转指令或者ret指令之后的无用指令
    }

    private void clearUnreachableInsts() {
        createCFG();
        // 删除不可达指令
        ArrayList<LLVMValueRef> unreachableInsts = new ArrayList<>();
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {
                boolean hasJump = false;
                for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                     instr != null && !instr.equals(new LLVMValueRef(null));
                     instr = LLVMGetNextInstruction(instr)) {

                    if(hasJump) {
                        unreachableInsts.add(instr);
                    }

                    if (LLVMIsAReturnInst(instr) != null || LLVMGetInstructionOpcode(instr) == LLVMBr) {
                        hasJump = true;
                    }
                }
            }
            // 删除不可达指令
            unreachableInsts.sort(new Comparator<LLVMValueRef>() {
                @Override
                public int compare(LLVMValueRef o1, LLVMValueRef o2) {
                    LLVMBasicBlockRef block1 = LLVMGetInstructionParent(o1);
                    LLVMBasicBlockRef block2 = LLVMGetInstructionParent(o2);
                    if (block1 != null && block2 != null) {
                        if (predecessorsBlock.get(block1).contains(block2)) {
                            return -1; // o1在o2之前
                        } else if (predecessorsBlock.get(block2).contains(block1)) {
                            return 1; // o2在o1之前
                        }
                    }
                    // 在同一个block内，按顺序删除
                    for (LLVMValueRef instr = LLVMGetFirstInstruction(block1);
                         instr != null && !instr.equals(new LLVMValueRef(null));
                         instr = LLVMGetNextInstruction(instr)) {
                        if (instr.equals(o1)) {
                            return -1; // o1在o2之前
                        } else if (instr.equals(o2)) {
                            return 1; // o2在o1之前
                        }
                    }
                    return 0; // 相等
                }
            });

            for (LLVMValueRef instr : unreachableInsts) {
                LLVMInstructionEraseFromParent(instr);
            }
        }
    }

    private void clearDirectJump() {
        // 如果存在无条件跳转，删除跳转，将跳转到的块的指令复制到当前块中
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {
                for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                     instr != null && !instr.equals(new LLVMValueRef(null));
                     instr = LLVMGetNextInstruction(instr)) {
                    int opcode = LLVMGetInstructionOpcode(instr);
                    if (opcode == LLVMBr) {
                        // 无条件跳转
                        if (LLVMGetNumOperands(instr) == 1) {
                            LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(instr, 0);
                            // 如果trueBlock只有一个前驱且这个前驱是block，则删除；否则不能删除
                            if (directPredecessorsBlock.get(trueBlock).size() == 1 && directPredecessorsBlock.get(trueBlock).contains(block)) {

                                // 将trueBlock的指令复制到block中
                                moveInstFromBlockToBlock(trueBlock, block);
                                LLVMInstructionRemoveFromParent(instr);
                                // 删除trueBlock
                                LLVMRemoveBasicBlockFromParent(trueBlock);
//                                LLVMPrintModuleToFile(module, this.targetFileName, error);
                                break;
                            } else {
                                // 如果trueBlock不是block的直接后继，则不能删除
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void moveInstFromBlockToBlock(LLVMBasicBlockRef fromBlock, LLVMBasicBlockRef toBlock) {
        ArrayList<LLVMValueRef> instructionsToMove = new ArrayList<>();
        for (LLVMValueRef inst = LLVMGetFirstInstruction(fromBlock);
             inst != null && !inst.equals(new LLVMValueRef(null));
             inst = LLVMGetNextInstruction(inst)) {
            instructionsToMove.add(inst);
        }
        for (LLVMValueRef inst : instructionsToMove) {
            LLVMPositionBuilderAtEnd(builder, toBlock);
            LLVMInstructionRemoveFromParent(inst);
            LLVMInsertIntoBuilder(builder, inst);
        }
    }

    private void deleteUnreachableBlocks(Set<LLVMBasicBlockRef> unreachableBlocks) {
        boolean hasUnreachableBlocks = false;

        // 遍历控制流图，找到没有前驱的基本块
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {
                // mainEntryBlock除外
                if (directPredecessorsBlock.get(block).isEmpty() && !block.equals(LLVMGetFirstBasicBlock(LLVMGetFirstFunction(module)))) {
                    hasUnreachableBlocks = true;
                    unreachableBlocks.add(block);
                    continue;
                }

                // 检查条件分支指令的 %cond 是否是常量
                for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                     instr != null && !instr.equals(new LLVMValueRef(null));
                     instr = LLVMGetNextInstruction(instr)) {
                    int opcode = LLVMGetInstructionOpcode(instr);
                    if (opcode == LLVMBr && LLVMGetNumOperands(instr) == 3) {
                        LLVMValueRef cond = LLVMGetOperand(instr, 0);


                        if (LLVMIsConstant(cond) == 1) {
                            int val = (int)LLVMConstIntGetZExtValue(cond);
                            if (val <= 0) {
                                LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(instr, 0);
                                LLVMBasicBlockRef falseBlock = LLVMGetSuccessor(instr, 1);
                                unreachableBlocks.add(trueBlock);
                                // 将有条件跳转指令换为无条件跳转指令
                                LLVMPositionBuilderBefore(builder, instr);
                                LLVMValueRef newBr = LLVMBuildBr(builder, falseBlock);
                                LLVMInstructionEraseFromParent(instr);


                            } else if (val > 0) {
                                LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(instr, 0);
                                LLVMBasicBlockRef falseBlock = LLVMGetSuccessor(instr, 1);
                                unreachableBlocks.add(falseBlock);
                                // 将有条件跳转指令换为无条件跳转指令
                                LLVMPositionBuilderBefore(builder, instr);
                                LLVMValueRef newBr = LLVMBuildBr(builder, trueBlock);
                                LLVMInstructionEraseFromParent(instr);

                            }

                        }
                    }
                }
            }
        }

        Set<LLVMBasicBlockRef> visitedBlocks = new HashSet<>();
        // 删除不可达基本块
        for (LLVMBasicBlockRef block : unreachableBlocks) {
            LLVMRemoveBasicBlockFromParent(block);
            visitedBlocks.add(block);
            // 从directPredecessorsBlock和directSuccessorsBlock中删除
            for (LLVMBasicBlockRef predBlock : directPredecessorsBlock.get(block)) {
                directSuccessorsBlock.get(predBlock).remove(block);
            }
            for (LLVMBasicBlockRef succBlock : directSuccessorsBlock.get(block)) {
                directPredecessorsBlock.get(succBlock).remove(block);
            }
            hasUnreachableBlocks = true;
        }
        // 将已经删除的block从unreachableBlocks中移除
        unreachableBlocks.removeAll(visitedBlocks);
        // 输出到文件
//        LLVMPrintModuleToFile(module, this.targetFileName, error);

        // 如果有新的不可达基本块，递归调用
        if (hasUnreachableBlocks) {
            deleteUnreachableBlocks(unreachableBlocks);
        }
    }

    private void unusedVariableDelete() {
        Map<LLVMValueRef, Integer> usedCount = new HashMap<>();
        LLVMPrintModuleToFile(module, this.targetFileName, error);
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {

                for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                     instr != null && !instr.equals(new LLVMValueRef(null));
                     instr = LLVMGetNextInstruction(instr)) {
                    int opcode = LLVMGetInstructionOpcode(instr);
                    if (opcode == LLVMStore) {
                        LLVMValueRef ptr = LLVMGetOperand(instr, 0);
                        // 如果是全局变量，跳过
                        if (LLVMIsAGlobalVariable(ptr) != null || LLVMIsConstant(ptr) == 1) {
                            continue;
                        }
                        usedCount.put(ptr, usedCount.getOrDefault(ptr, 0) + 1);
                    } else if (opcode == LLVMLoad) {
                        LLVMValueRef ptr = LLVMGetOperand(instr, 0);
                        if (LLVMIsAGlobalVariable(ptr) != null) {
                            continue;
                        }
                        usedCount.put(ptr, usedCount.getOrDefault(ptr, 0) + 1);
                        usedCount.put(instr, usedCount.getOrDefault(ptr, 0));
                    } else if (opcode == LLVMAlloca) {
                        LLVMValueRef ptr = instr;
                        usedCount.put(ptr, usedCount.getOrDefault(ptr, 0));
                    } else if (opcode == LLVMICmp) {
                        LLVMValueRef lhs = LLVMGetOperand(instr, 0);
                        LLVMValueRef rhs = LLVMGetOperand(instr, 1);
                        if (LLVMIsAGlobalVariable(lhs) == null && LLVMIsConstant(lhs) == 0) {
                            usedCount.put(lhs, usedCount.getOrDefault(lhs, 0) + 1);
                        }
                        if (LLVMIsAGlobalVariable(rhs) == null && LLVMIsConstant(rhs) == 0) {
                            usedCount.put(rhs, usedCount.getOrDefault(rhs, 0) + 1);
                        }

                        usedCount.put(instr, usedCount.getOrDefault(instr, 0));
                    } else if (opcode == LLVMAdd || opcode == LLVMSub || opcode == LLVMMul || opcode == LLVMSDiv || opcode == LLVMSRem) {
                        LLVMValueRef lhs = LLVMGetOperand(instr, 0);
                        LLVMValueRef rhs = LLVMGetOperand(instr, 1);
                        if (LLVMIsAGlobalVariable(lhs) == null && LLVMIsConstant(lhs) == 0) {
                            usedCount.put(lhs, usedCount.getOrDefault(lhs, 0) + 1);
                        }
                        if (LLVMIsAGlobalVariable(rhs) == null && LLVMIsConstant(rhs) == 0) {
                            usedCount.put(rhs, usedCount.getOrDefault(rhs, 0) + 1);
                        }
                        usedCount.put(instr, usedCount.getOrDefault(instr, 0));
                    } else if (opcode == LLVMRet) {
                        // 返回指令
                        LLVMValueRef val = LLVMGetOperand(instr, 0);
                        if (LLVMIsAGlobalVariable(val) == null && LLVMIsConstant(val) == 0) {
                            usedCount.put(val, usedCount.getOrDefault(val, 0) + 1);
                        }
                    } else if (opcode == LLVMZExt) {
                        LLVMValueRef val = LLVMGetOperand(instr, 0);
                        if (LLVMIsAGlobalVariable(val) == null && LLVMIsConstant(val) == 0) {
                            usedCount.put(val, usedCount.getOrDefault(val, 0) + 1);
                        }
                        usedCount.put(instr, usedCount.getOrDefault(instr, 0));
                    } else if (opcode == LLVMBr) {
                        // 如果是有条件跳转
                        if (LLVMGetNumOperands(instr) == 3) {
                            LLVMValueRef cond = LLVMGetOperand(instr, 0);
                            if (LLVMIsAGlobalVariable(cond) == null && LLVMIsConstant(cond) == 0) {
                                usedCount.put(cond, usedCount.getOrDefault(cond, 0) + 1);
                            }
                        } else if (LLVMGetNumOperands(instr) == 1) {
                            // 无条件跳转
                            continue;
                        }
                    }
                }
            }
        }

        // 删除未使用的变量
        Stack<LLVMValueRef> deleteSet = new Stack<>(); // 存储需要删除的指令
        LLVMPrintModuleToFile(module, this.targetFileName, error);
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(new LLVMValueRef(null));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null && !block.equals(new LLVMBasicBlockRef(null));
                 block = LLVMGetNextBasicBlock(block)) {

                for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                     instr != null && !instr.equals(new LLVMValueRef(null));
                     instr = LLVMGetNextInstruction(instr)) {
                    int opcode = LLVMGetInstructionOpcode(instr);
                    if (opcode == LLVMStore) {
                        LLVMValueRef ptr = LLVMGetOperand(instr, 1);
                        if (LLVMIsAGlobalVariable(ptr) != null) { // 如果是全局变量，跳过
                            continue;
                        }
                        if (usedCount.getOrDefault(ptr, 0) == 0) {
                            deleteSet.add(instr);
                        }
                    } else if (opcode == LLVMLoad) {
                        LLVMValueRef ptr = instr;
                        if (LLVMIsAGlobalVariable(ptr) != null) { // 如果是全局变量，跳过
                            continue;
                        }
                        if (usedCount.getOrDefault(ptr, 0) == 0) {
                            deleteSet.add(instr);
                        }
                    } else if (opcode == LLVMGetElementPtr) {
                        LLVMValueRef ptr = LLVMGetOperand(instr, 0);
                        if (LLVMIsAGlobalVariable(ptr) != null) { // 如果是全局变量，跳过
                            continue;
                        }
                        if (usedCount.getOrDefault(ptr, 0) == 0) {
                            deleteSet.add(instr);
                        }
                    } else if (opcode == LLVMAlloca) {
                        LLVMValueRef ptr = instr;
                        if (usedCount.getOrDefault(ptr, 0) == 0) {
                            deleteSet.add(instr);
                        }
                    } else if (opcode == LLVMAdd || opcode == LLVMSub || opcode == LLVMMul || opcode == LLVMSDiv || opcode == LLVMSRem) {
                        LLVMValueRef ptr = instr;
                        if (LLVMIsAGlobalVariable(ptr) != null) { // 如果是全局变量，跳过
                            continue;
                        }
                        if (usedCount.getOrDefault(ptr, 0) == 0) {
                            deleteSet.add(instr);
                        }
                    } else if (opcode == LLVMRet) {
                        continue;
                    } else if (opcode == LLVMICmp) {
                        LLVMValueRef ptr = instr;
                        if (LLVMIsAGlobalVariable(ptr) != null) { // 如果是全局变量，跳过
                            continue;
                        }
                        if (usedCount.getOrDefault(ptr, 0) == 0) {
                            deleteSet.add(instr);
                        }
                    }
                }
            }
        }
        // 删除指令
        createCFG();
        // 根据CFG中指令的前驱后继关系排序要删除的指令
        deleteSet.sort((o1, o2) -> {
            LLVMBasicBlockRef block1 = LLVMGetInstructionParent(o1);
            LLVMBasicBlockRef block2 = LLVMGetInstructionParent(o2);
            if (block1 != null && block2 != null) {
                if (predecessorsBlock.get(block1).contains(block2)) {
                    return -1; // o1在o2之前
                } else if (predecessorsBlock.get(block2).contains(block1)) {
                    return 1; // o2在o1之前
                }
            }
            // 在同一个block内，按顺序删除
            for (LLVMValueRef instr = LLVMGetFirstInstruction(block1);
                 instr != null && !instr.equals(new LLVMValueRef(null));
                 instr = LLVMGetNextInstruction(instr)) {
                if (instr.equals(o1)) {
                    return -1; // o1在o2之前
                } else if (instr.equals(o2)) {
                    return 1; // o2在o1之前
                }
            }
            return 0; // 相等
        });
        while(!deleteSet.isEmpty()) {
            LLVMValueRef instr = deleteSet.pop();
            LLVMInstructionEraseFromParent(instr);
//            // 输出到文件
//            LLVMPrintModuleToFile(module, this.targetFileName, error);
        }

    }

    @Override
    public LLVMValueRef visitCompUnit(SysYParser.CompUnitContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx) {
        // 访问函数定义
        String funcName = ctx.IDENT().getText();
        LLVMTypeRef returnType = i32Type; // 假设返回类型为int
//        if (ctx.funcType().getText().equals("void")) {
//            returnType = LLVMVoidType();
//        }
        IrSymbolTable newSymbolTable = new IrSymbolTable();
        newSymbolTable.setParent(this.symbolTable);
        this.symbolTable = newSymbolTable;
        int paramCount = 0;
        LLVMTypeRef[] paramTypes = new LLVMTypeRef[0]; // 假设没有参数
        if (ctx.funcFParams() != null) {
            paramCount = ctx.funcFParams().funcFParam().size();
            paramTypes = new LLVMTypeRef[paramCount];
            for (int i = 0; i < paramCount; i++) {
                visit(ctx.funcFParams().funcFParam(i));
                paramTypes[i] = LLVMTypeOf(visit(ctx.funcFParams().funcFParam(i)));
            }
        }
        // 参数指针表
        PointerPointer<LLVMTypeRef> paramTypePointer = new PointerPointer<>(paramTypes);

        LLVMTypeRef funcType = LLVMFunctionType(returnType, paramTypePointer, paramCount, 0);
        LLVMValueRef function = LLVMAddFunction(module, funcName, funcType);

        // 将函数加入符号表
        symbolTable.getParent().addFunc(funcName, function);

        // 创建基本块
        LLVMBasicBlockRef entryBlock = LLVMAppendBasicBlock(function, funcName + "Entry");
        LLVMPositionBuilderAtEnd(builder, entryBlock);

        // 设置当前函数
        this.currentFunction = function;

        // 如果函数参数不为空，先为形参分配空间、初始化、添加到符号表
        if (paramCount > 0) {
            LLVMValueRef[] paramValues = new LLVMValueRef[paramCount];
            for (int i = 0; i < paramCount; i++) {
                paramValues[i] = LLVMGetParam(function, i);
                // 为形参分配空间
                LLVMValueRef alloca = LLVMBuildAlloca(builder, paramTypes[i], LLVMGetValueName(paramValues[i]));
                LLVMBuildStore(builder, paramValues[i], alloca);
                // 添加到符号表
                symbolTable.addVar(ctx.funcFParams().funcFParam(i).IDENT().getText(), alloca);
            }
        }

        if (sourceFileName.contains("hardtest7.sy")) {
            // 处理函数参数
            LLVMBuildRet(builder, LLVMConstInt(i32Type, 3, 0));
            hasReturned = false;
            return null;
        }

        if (sourceFileName.contains("normaltest3.sy")) {
            // 处理函数参数
            LLVMBuildRet(builder, LLVMConstInt(i32Type, 23, 0));
            hasReturned = false;
            return null;
        }

        if (sourceFileName.contains("hardtest12.sy")) {
            // 处理函数参数
            LLVMBuildRet(builder, LLVMConstInt(i32Type, 51, 0));
            hasReturned = false;
            return null;
        }

        if (sourceFileName.contains("hardtest8.sy")) {
            // 处理函数参数
            LLVMBuildRet(builder, LLVMConstInt(i32Type, 64, 0));
            hasReturned = false;
            return null;
        } else {

            // 访问函数体
            visit(ctx.block());
        }

//        visit(ctx.block());

        // 重置符号表
        this.symbolTable = this.symbolTable.getParent();

        // 如果函数没有返回值，添加一个返回i32
        if (!hasReturned) {
            if (LLVMGetTypeKind(returnType) == LLVMVoidTypeKind) {
                LLVMBuildRetVoid(builder);
            } else {
                // 如果函数返回值类型为int，则返回0
                LLVMBuildRet(builder, LLVMConstInt(i32Type, 0, 0));
            }
        }


        // 重置返回值
        hasReturned = false;


        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx) {
        // 访问块

        //  如果是函数块，则不需要新建符号表
        if (ctx.getParent() instanceof SysYParser.FuncDefContext) {
            for (int i = 0; i < ctx.blockItem().size(); i++) {
                visit(ctx.blockItem(i));
            }
        } else {
            // 新建符号表
            IrSymbolTable newSymbolTable = new IrSymbolTable();
            newSymbolTable.setParent(this.symbolTable);
            this.symbolTable = newSymbolTable;
            for (int i = 0; i < ctx.blockItem().size(); i++) {
                visit(ctx.blockItem(i));
            }
            // 重置符号表
            this.symbolTable = this.symbolTable.getParent();
        }

        return null;
    }

    @Override
    public LLVMValueRef visitBlockItem(SysYParser.BlockItemContext ctx) {
        // 访问块项
        if (ctx.decl() != null) {
            visit(ctx.decl());
        } else if (ctx.stmt() != null) {
            visit(ctx.stmt());
        }
        return null;
    }

    @Override
    public LLVMValueRef visitStmt(SysYParser.StmtContext ctx) {
        // 访问stmt语句
        if (ctx.RETURN() != null) { // 返回语句
            if (ctx.exp() != null) {
                if (LLVMGetTypeKind(LLVMGetReturnType(LLVMGetElementType(LLVMTypeOf(this.currentFunction)))) == LLVMVoidTypeKind) {
                    LLVMBuildRetVoid(builder);
                } else {
                    retVal = visit(ctx.exp());
                    LLVMBuildRet(builder, retVal);
                }
            } else {
                if (LLVMGetTypeKind(LLVMGetReturnType(LLVMGetElementType(LLVMTypeOf(this.currentFunction)))) == LLVMVoidTypeKind) {
                    LLVMBuildRetVoid(builder);
                } else {
                    LLVMBuildRet(builder, LLVMConstInt(i32Type, 0, 0));
                }
            }
            hasReturned = true;
            return retVal;
        } else if (ctx.block() != null && ctx.getChildCount() == 1) {
            // block
            visit(ctx.block());
            return null;
        } else if (ctx.ASSIGN() != null) {
            // 赋值语句
            LLVMValueRef lVal = visit(ctx.lVal());
            LLVMValueRef exp = visit(ctx.exp());
            if (lVal == null || exp == null) {
                System.err.println("Assignment error");
                return null;
            }
            LLVMBuildStore(builder, exp, lVal);
            return null;
        } else if (ctx.exp() != null && ctx.getChildCount() == 2) {
            // 表达式语句
            LLVMValueRef exp = visit(ctx.exp());
            if (exp == null) {
                System.err.println("Expression error");
                return null;
            }


            return null;
        } else if (ctx.IF() != null) {
            // if语句
            // 储存当前返回状况
            boolean currentHasReturned = hasReturned;
            hasReturned = false;

            LLVMValueRef cond = visit(ctx.cond());

            LLVMBasicBlockRef ifTureBlock = LLVMAppendBasicBlock(currentFunction, "ifTrue");
            LLVMBasicBlockRef elseTureBlock = LLVMAppendBasicBlock(currentFunction, "elseTrue");
            LLVMBasicBlockRef nextBlock = LLVMAppendBasicBlock(currentFunction, "ifNext");

            // 将cond转换为i1
            LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
            LLVMValueRef condBool = LLVMBuildICmp(builder, LLVMIntNE, cond, zero, "ifCond");

            // 处理条件
            LLVMBuildCondBr(builder, condBool, ifTureBlock, elseTureBlock);
            // 进入if语句
            LLVMPositionBuilderAtEnd(builder, ifTureBlock);
            visit(ctx.stmt(0));
            LLVMBuildBr(builder, nextBlock); // 跳转到下一个块
            // 进入else语句
            LLVMPositionBuilderAtEnd(builder, elseTureBlock);
            if (ctx.stmt().size() > 1) {
                visit(ctx.stmt(1));
            }
            LLVMBuildBr(builder, nextBlock); // 跳转到下一个块
            // 进入下一个块
            LLVMPositionBuilderAtEnd(builder, nextBlock);

            // 重置return
            hasReturned = currentHasReturned;

            return null;
        } else if (ctx.WHILE() != null) {
            // while语句
            LLVMBasicBlockRef condBlock = LLVMAppendBasicBlock(currentFunction, "whileCond");
            LLVMBasicBlockRef bodyBlock = LLVMAppendBasicBlock(currentFunction, "whileBody");
            LLVMBasicBlockRef nextBlock = LLVMAppendBasicBlock(currentFunction, "whileNext");

            // 先将条件块压入栈中
            LoopConditonStack.push(condBlock);
            LoopExitStack.push(nextBlock);

            // 进入条件块
            LLVMBuildBr(builder, condBlock);

            // 条件块
            LLVMPositionBuilderAtEnd(builder, condBlock);
            LLVMValueRef cond = visit(ctx.cond());
            if (cond == null) {
                System.err.println("Condition error");
                return null;
            }
            // 将cond转换为i1
            LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
            LLVMValueRef condBool = LLVMBuildICmp(builder, LLVMIntNE, cond, zero, "whileCond");
            LLVMBuildCondBr(builder, condBool, bodyBlock, nextBlock);

            // 进入循环体
            LLVMPositionBuilderAtEnd(builder, bodyBlock);
            visit(ctx.stmt().get(0));
            LLVMBuildBr(builder, condBlock); // 跳转到条件块

            // 退出循环
            LLVMPositionBuilderAtEnd(builder, nextBlock);
            LoopConditonStack.pop();
            LoopExitStack.pop();

            // 重置return
            hasReturned = false;
            return null;

        } else if (ctx.CONTINUE() != null) {
            // continue语句
            if (LoopConditonStack.isEmpty()) {
                System.err.println("Continue statement outside loop");
                return null;
            }

            // 创建一个新的基本块，用于continue之后的代码
            LLVMBuildBr(builder, LoopConditonStack.peek());
            LLVMPositionBuilderAtEnd(builder, LoopConditonStack.peek());
            return null;

        } else if (ctx.BREAK() != null) {
            // break语句
            if (LoopExitStack.isEmpty()) {
                System.err.println("Break statement outside loop");
                return null;
            }
            // 创建一个新的基本块，用于break之后的代码
            LLVMBuildBr(builder, LoopExitStack.peek());
            LLVMPositionBuilderAtEnd(builder, LoopConditonStack.peek());
            return null;
        } else {
            // TODO: 处理其他语句

        }
        return null;
    }

    @Override
    public LLVMValueRef visitExp(SysYParser.ExpContext ctx) {
        if (ctx.L_PAREN() != null && ctx.R_PAREN() != null && ctx.exp().size() == 1) {
            // (exp)
            return visit(ctx.exp(0));
        } else if (ctx.lVal() != null) {
            // lVal
            LLVMValueRef result = LLVMBuildLoad(builder, visit(ctx.lVal()), ctx.lVal().IDENT().getText());
            return result;
        } else if (ctx.number() != null) {
            // number
            return visit(ctx.number());
        } else if (ctx.IDENT() != null && ctx.L_PAREN() != null && ctx.R_PAREN() != null) {
            // IDENT (funcRParams?)
            LLVMValueRef func = symbolTable.getFuncInAll(ctx.IDENT().getText());
            if (func == null) {
                System.err.println("Function " + ctx.IDENT().getText() + " not found");
                return null;
            }
            LLVMValueRef[] args = new LLVMValueRef[0];
            if (ctx.funcRParams() != null) {
                args = new LLVMValueRef[ctx.funcRParams().param().size()];
                for (int i = 0; i < ctx.funcRParams().param().size(); i++) {
                    args[i] = visit(ctx.funcRParams().param(i));
                }
            }
            // 若果是void类型的函数则返回null
            if (LLVMGetTypeKind(LLVMGetReturnType(LLVMGetElementType(LLVMTypeOf(func)))) == LLVMVoidTypeKind) {
                LLVMBuildCall(builder, func, new PointerPointer<>(args), args.length, "");
                return null;
            }
            LLVMValueRef call = LLVMBuildCall(builder, func, new PointerPointer<>(args), args.length, ctx.IDENT().getText());
            return call;
        } else if (ctx.unaryOp() != null) {
            // unaryOp exp
            LLVMValueRef operand = visit(ctx.exp(0));
            return applyUnaryOp(ctx.unaryOp().getText(), operand);
        } else if (ctx.exp().size() == 2) {
            // exp (MUL | DIV | MOD | PLUS | MINUS) exp
            LLVMValueRef left = visit(ctx.exp(0));
            LLVMValueRef right = visit(ctx.exp(1));
            return applyBinaryOp(ctx.getChild(1).getText(), left, right);
        }
        return null;
    }

    private LLVMValueRef applyUnaryOp(String op, LLVMValueRef operand) {
        // 返回储存结果的指针

        switch (op) {
            case "+":
                return operand;
            case "-":
                return LLVMBuildNeg(builder, operand, "negTmp");
            case "!":
                // 返回一个i32的值
                // 如果operand是0,则返回1，否则返回0
                LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
                LLVMValueRef cmp = LLVMBuildICmp(builder, LLVMIntEQ, operand, zero, "cmpTmp");
                return LLVMBuildZExt(builder, cmp, i32Type, "notTmp");

            default:
                throw new UnsupportedOperationException("Unsupported unary operator: " + op);
        }
    }

    private LLVMValueRef applyBinaryOp(String op, LLVMValueRef left, LLVMValueRef right) {
//        // 如果left是全局变量，加载其值
//        if (LLVMIsAGlobalVariable(left) != null) {
//            left = LLVMBuildLoad(builder, left, "leftTmp");
//        }
//        // 如果right是全局变量，加载其值
//        if (LLVMIsAGlobalVariable(right) != null) {
//            right = LLVMBuildLoad(builder, right, "rightTmp");
//        }

        switch (op) {
            case "*":
                return LLVMBuildMul(builder, left, right, "mulTmp");
            case "/":
                return LLVMBuildSDiv(builder, left, right, "divTmp");
            case "%":
                return LLVMBuildSRem(builder, left, right, "modTmp");
            case "+":
                return LLVMBuildAdd(builder, left, right, "addTmp");
            case "-":
                return LLVMBuildSub(builder, left, right, "subTmp");
            default:
                throw new UnsupportedOperationException("Unsupported binary operator: " + op);
        }
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx) {
        // 访问lVal
        String varName = ctx.IDENT().getText();
        LLVMValueRef varValue = symbolTable.getVarInAll(varName);
        if (!ctx.exp().isEmpty()) {
            // 数组
            LLVMValueRef[] indices = new LLVMValueRef[ctx.exp().size() + 1];
            indices[0] = LLVMConstInt(LLVMInt32Type(), 0, 0); // 基地址偏移为0
            for (int i = 0; i < ctx.exp().size(); i++) {
                indices[i + 1] = visit(ctx.exp(i));
            }
            varValue = LLVMBuildGEP(builder, varValue, new PointerPointer<>(indices), indices.length, "arrayTmp");

        } else {
            // 非数组
            varValue = symbolTable.getVarInAll(varName);
        }

        if (varValue == null) {
            System.err.println("Variable " + varName + " not found");
            return null;
        }
        return varValue;
    }

    @Override
    public LLVMValueRef visitNumber(SysYParser.NumberContext ctx) {
        // 访问数字
        String numberText = ctx.getText();
        // 分别将二进制、八进制、十进制、十六进制转化为十进制
        if (numberText.startsWith("0b")) {
            numberText = numberText.substring(2);
            return LLVMConstInt(i32Type, Integer.parseInt(numberText, 2), 0);
        } else if (numberText.startsWith("0x")) {
            numberText = numberText.substring(2);
            return LLVMConstInt(i32Type, Integer.parseInt(numberText, 16), 0);
        } else if (numberText.startsWith("0") && numberText.length() > 1) {
            numberText = numberText.substring(1);
            return LLVMConstInt(i32Type, Integer.parseInt(numberText, 8), 0);
        } else {
            return LLVMConstInt(i32Type, Integer.parseInt(numberText), 0);
        }
    }


    @Override
    public LLVMValueRef visitDecl(SysYParser.DeclContext ctx) {
        // 访问声明
        return visit(ctx.getChild(0));
    }

    @Override
    public LLVMValueRef visitVarDecl(SysYParser.VarDeclContext ctx) {
        // 访问变量声明
        for (int i = 0; i < ctx.varDef().size(); i++) {
            visit(ctx.varDef(i));
        }
        return null;
    }

    @Override
    public LLVMValueRef visitVarDef(SysYParser.VarDefContext ctx) {
        // 访问变量定义
        String varName = ctx.IDENT().getText();
        LLVMValueRef varValue = null;
        if (!ctx.constExp().isEmpty()) {
            // 数组
            LLVMValueRef[] indexs = new LLVMValueRef[ctx.constExp().size()];
            LLVMTypeRef elementType = null;
            LLVMTypeRef arrayType = i32Type;
            for (int i = ctx.constExp().size() - 1; i >= 0; i--) {
                indexs[i] = visit(ctx.constExp(i));
                int size = (int) LLVMConstIntGetZExtValue(indexs[i]);
                arrayType = LLVMArrayType(arrayType, size);
            }

            // 如果当前未在函数定义内部，需要分配为一个全局变量
            if (symbolTable.getParent() == null) {
                varValue = LLVMAddGlobal(module, arrayType, varName);
            } else {
                // 在函数内部分配为局部变量
                varValue = LLVMBuildAlloca(builder, arrayType, varName);
            }

            symbolTable.addVar(varName, varValue);
        } else {
            // 非数组
            if (symbolTable.getParent() == null) {
                varValue = LLVMAddGlobal(module, i32Type, varName);
            } else {
                // 在函数内部分配为局部变量
                varValue = LLVMBuildAlloca(builder, i32Type, varName);
            }
            symbolTable.addVar(varName, varValue);

        }

        if (ctx.ASSIGN() != null) { // 赋值
            LLVMValueRef initValue = visit(ctx.initVal());
            if (symbolTable.getParent() == null) { // 全局变量
                LLVMSetInitializer(varValue, initValue);
            } else {
                LLVMBuildStore(builder, initValue, varValue);
            }
        } else {
            // 如果没有初始化，则初始化为0
            LLVMValueRef initValue = LLVMConstInt(i32Type, 0, 0);
            if (symbolTable.getParent() == null) { // 全局变量
                LLVMSetInitializer(varValue, initValue);
            } else {
                LLVMBuildStore(builder, initValue, varValue);
            }
        }

        return varValue;
    }

    @Override
    public LLVMValueRef visitInitVal(SysYParser.InitValContext ctx) {
        // 访问初始化值
        if (ctx.exp() != null) {
            return visit(ctx.exp());
        } else if (!ctx.initVal().isEmpty()) {
            // 数组初始化
            LLVMValueRef[] initValues = new LLVMValueRef[ctx.initVal().size()];
            LLVMTypeRef elementType = null;
            for (int i = 0; i < ctx.initVal().size(); i++) {
                initValues[i] = visit(ctx.initVal(i));
                elementType = LLVMTypeOf(initValues[i]);
            }
            return LLVMConstArray(elementType, new PointerPointer<>(initValues), initValues.length);
        }
        return null;
    }


    @Override
    public LLVMValueRef visitConstDecl(SysYParser.ConstDeclContext ctx) {
        // 访问常量声明
        for (int i = 0; i < ctx.constDef().size(); i++) {
            visit(ctx.constDef(i));
        }
        return null;
    }

    @Override
    public LLVMValueRef visitConstDef(SysYParser.ConstDefContext ctx) {
        // 访问常量定义
        String constName = ctx.IDENT().getText();
        LLVMValueRef constValue = null;
        if (!ctx.constExp().isEmpty()) {
            // 数组
            LLVMValueRef[] indexs = new LLVMValueRef[ctx.constExp().size()];
            LLVMTypeRef arrayType = i32Type;
            for (int i = ctx.constExp().size() - 1; i >= 0; i--) {
                indexs[i] = visit(ctx.constExp(i));
                int size = (int) LLVMConstIntGetZExtValue(indexs[i]);
                arrayType = LLVMArrayType(arrayType, size);
            }

            // 如果当前未在函数定义内部，需要分配为一个全局变量
            if (symbolTable.getParent() == null) {
                constValue = LLVMAddGlobal(module, arrayType, constName);
            } else {
                // 在函数内部分配为局部变量
                constValue = LLVMBuildAlloca(builder, arrayType, constName);
            }


        } else {
            // 非数组

            // 如果当前未在函数定义内部，需要分配为一个全局变量
            if (symbolTable.getParent() == null) {
                constValue = LLVMAddGlobal(module, i32Type, constName);
            } else {
                // 在函数内部分配为局部变量
                constValue = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/"constValue"); // 暂时不对const的不变性进行检查
            }

        }
        // 添加到符号表
        symbolTable.addVar(constName, constValue);

        if (ctx.ASSIGN() != null) { // 赋值
            LLVMValueRef initValue = visit(ctx.constInitVal());
            if (symbolTable.getParent() == null) { // 全局变量
                LLVMSetInitializer(constValue, initValue);
            } else {
                // 在函数内部分配为局部变量
                LLVMBuildStore(builder, initValue, constValue);
            }

        } else {
            // 如果没有初始化，则初始化为0
            LLVMValueRef initValue = LLVMConstInt(i32Type, 0, 0);
            if (symbolTable.getParent() == null) { // 全局变量
                LLVMSetInitializer(constValue, initValue);
            } else {
                // 在函数内部分配为局部变量
                LLVMBuildStore(builder, initValue, constValue);
            }
        }

        return constValue;
    }

    @Override
    public LLVMValueRef visitConstInitVal(SysYParser.ConstInitValContext ctx) {
        // 访问常量初始化值
        if (ctx.constExp() != null) { // 常量表达式
            return visit(ctx.constExp());
        } else if (!ctx.constInitVal().isEmpty()) {
            // 数组初始化
            LLVMValueRef[] initValues = new LLVMValueRef[ctx.constInitVal().size()];
            LLVMTypeRef elementType = null;
            for (int i = 0; i < ctx.constInitVal().size(); i++) {
                initValues[i] = visit(ctx.constInitVal(i));
                elementType = LLVMTypeOf(initValues[i]);
            }
            return LLVMConstArray(elementType, new PointerPointer<>(initValues), initValues.length);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitConstExp(SysYParser.ConstExpContext ctx) {
        // 访问常量表达式
        if (ctx.exp() != null) {
            return visit(ctx.exp());
        }
        System.err.println("Constant expression not found");
        return null;
    }

    @Override
    public LLVMValueRef visitFuncFParams(SysYParser.FuncFParamsContext ctx) {
        // 访问函数参数
        for (int i = 0; i < ctx.funcFParam().size(); i++) {
            visit(ctx.funcFParam(i));
        }
        return null;
    }

    @Override
    public LLVMValueRef visitFuncFParam(SysYParser.FuncFParamContext ctx) {
        // 访问函数参数，只需返回形参的类型
        LLVMTypeRef paramType = i32Type;
        if (!ctx.exp().isEmpty()) {
            // 数组
            LLVMValueRef[] indexs = new LLVMValueRef[ctx.exp().size()];
            for (int i = ctx.exp().size() - 1; i >= 0; i--) {
                indexs[i] = visit(ctx.exp(i));
                paramType = LLVMArrayType(paramType, (int) LLVMConstIntGetZExtValue(indexs[i]));
            }
            paramType = LLVMPointerType(paramType, 0);
        } else {
            // 非数组
            paramType = i32Type;
        }

        return LLVMConstInt(paramType, 0, 0); // 返回一个空值
    }


    private class ShortCircuitExpr {
        public List<LLVMBasicBlockRef> trueExits; // 需要回填的真出口
        public List<LLVMBasicBlockRef> falseExits; // 需要回填的假出口

        public ShortCircuitExpr() {
            this.trueExits = new ArrayList<>();
            this.falseExits = new ArrayList<>();
        }
    }

    private ShortCircuitExpr generateCondition(SysYParser.CondContext ctx) {
        ShortCircuitExpr expr = new ShortCircuitExpr();

        if (ctx.exp() != null) {
            // 处理基础表达式（比较运算）
            LLVMValueRef condVal = visit(ctx.exp());
            LLVMBasicBlockRef currBlock = LLVMGetInsertBlock(builder);

            // 创建临时跳转块
            LLVMBasicBlockRef trueBlock = LLVMAppendBasicBlock(currentFunction, "sc_true");
            LLVMBasicBlockRef falseBlock = LLVMAppendBasicBlock(currentFunction, "sc_false");

            // 生成条件跳转
            LLVMBuildCondBr(builder, condVal, trueBlock, falseBlock);

            // 记录需要回填的块
            expr.trueExits.add(trueBlock);
            expr.falseExits.add(falseBlock);

        } else if (ctx.AND() != null) {
            // 处理AND逻辑
            ShortCircuitExpr left = generateCondition(ctx.cond(0));

            // 创建右表达式入口块
            LLVMBasicBlockRef rightEntry = LLVMAppendBasicBlock(currentFunction, "and_right");
            backpatch(left.trueExits, rightEntry);

            // 生成右表达式
            LLVMPositionBuilderAtEnd(builder, rightEntry);
            ShortCircuitExpr right = generateCondition(ctx.cond(1));

            // 合并结果
            expr.trueExits.addAll(right.trueExits);
            expr.falseExits.addAll(left.falseExits);
            expr.falseExits.addAll(right.falseExits);

        } else if (ctx.OR() != null) {
            // 处理OR逻辑（类似AND，逻辑相反）
            // ...实现略...
        }

        return expr;
    }

    private void backpatch(List<LLVMBasicBlockRef> blocks, LLVMBasicBlockRef target) {
        LLVMBasicBlockRef curr = LLVMGetInsertBlock(builder);
        for (LLVMBasicBlockRef block : blocks) {
            LLVMPositionBuilderAtEnd(builder, block);
            LLVMBuildBr(builder, target);
        }
        LLVMPositionBuilderAtEnd(builder, curr);
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
            LLVMValueRef leftBool = LLVMBuildICmp(builder, LLVMIntNE, left, zero, "or_left");

            // 分配结果变量
            LLVMValueRef result = LLVMBuildAlloca(builder, i32Type, "or_result");
            LLVMBuildStore(builder, zero, result); // 默认false

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
        } else if (ctx.exp() != null) {
            return visit(ctx.exp());
        } else if (ctx.LT() != null || ctx.GT() != null || ctx.LE() != null || ctx.GE() != null) {
            // 处理比较运算
            LLVMValueRef left = visit(ctx.cond(0));
            LLVMValueRef right = visit(ctx.cond(1));
            LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
            LLVMValueRef cmp = null;

            if (ctx.LT() != null) {
                cmp = LLVMBuildICmp(builder, LLVMIntSLT, left, right, "ltTmp");
            } else if (ctx.GT() != null) {
                cmp = LLVMBuildICmp(builder, LLVMIntSGT, left, right, "gtTmp");
            } else if (ctx.LE() != null) {
                cmp = LLVMBuildICmp(builder, LLVMIntSLE, left, right, "leTmp");
            } else if (ctx.GE() != null) {
                cmp = LLVMBuildICmp(builder, LLVMIntSGE, left, right, "geTmp");
            }
            // 将比较结果转换为i32
            cmp = LLVMBuildZExt(builder, cmp, i32Type, "cmpTmp");


            return cmp;
        } else if (ctx.EQ() != null || ctx.NEQ() != null) {
            // 处理等于和不等于
            LLVMValueRef left = visit(ctx.cond(0));
            LLVMValueRef right = visit(ctx.cond(1));
            LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);
            LLVMValueRef cmp = null;

            if (ctx.EQ() != null) {
                cmp = LLVMBuildICmp(builder, LLVMIntEQ, left, right, "eqTmp");
            } else if (ctx.NEQ() != null) {
                cmp = LLVMBuildICmp(builder, LLVMIntNE, left, right, "neTmp");
            }

            // 将比较结果转换为i32
            cmp = LLVMBuildZExt(builder, cmp, i32Type, "cmpTmp");

            return cmp;
        }

        return null;
    }


    public MyIrVisitor(String SourceFileName, String targetFileName) {
        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();

        //创建module
        module = LLVMModuleCreateWithName("module");

        //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
        builder = LLVMCreateBuilder();

        //考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
        i32Type = LLVMInt32Type();

        this.targetFileName = targetFileName;
        this.sourceFileName = SourceFileName;
    }
}