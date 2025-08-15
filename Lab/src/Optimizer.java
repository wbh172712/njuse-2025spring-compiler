import org.bytedeco.llvm.LLVM.*;

import java.util.*;

import static org.bytedeco.llvm.global.LLVM.*;

class Optimizer {

    private final LLVMModuleRef module;

    public static final int UNDEF = 0;
    public static final int CONST = 1;
    public static final int NAC = 2;

    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> predecessorsBlock;
    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> successorsBlock;
    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> directPredecessorsBlock;
    Map<LLVMBasicBlockRef, Set<LLVMBasicBlockRef>> directSuccessorsBlock;

    public Optimizer(LLVMModuleRef module) {
        this.module = module;
    }

    public void run() {

        // 运行直到模块内容不再发生变化
        boolean changed;
        String prevIR = LLVMPrintModuleToString(module).getString();

        do {
            // 常量传播优化
            constantPropagation(LLVMGetFirstFunction(module));

            // 删除未使用的变量
            unusedElimination(LLVMGetFirstFunction(module));

//            unusedVariableDelete();

            String currIR = LLVMPrintModuleToString(module).getString();
            changed = !prevIR.equals(currIR); // 判断模块是否有变动
            prevIR = currIR;
        } while (changed);

    }

    public void createCFG() {
        predecessorsBlock = new HashMap<>();
        successorsBlock = new HashMap<>();
        directPredecessorsBlock = new HashMap<>();
        directSuccessorsBlock = new HashMap<>();

        // 初始化所有基本块
        LLVMValueRef function = LLVMGetFirstFunction(module);
        for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
             block != null && !block.equals(new LLVMBasicBlockRef(null));
             block = LLVMGetNextBasicBlock(block)) {
            successorsBlock.put(block, new HashSet<>());
            predecessorsBlock.put(block, new HashSet<>());
            directPredecessorsBlock.put(block, new HashSet<>());
            directSuccessorsBlock.put(block, new HashSet<>());
        }

        // 构建直接前驱和后继关系
        for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
             block != null && !block.equals(new LLVMBasicBlockRef(null));
             block = LLVMGetNextBasicBlock(block)) {

            LLVMValueRef terminator = LLVMGetLastInstruction(block);
            if (terminator == null || terminator.equals(new LLVMValueRef(null))) {
                continue;
            }

            int opcode = LLVMGetInstructionOpcode(terminator);

            if (opcode == LLVMBr) {
                // 有条件跳转
                if (LLVMGetNumOperands(terminator) == 3) {
                    LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(terminator, 0);
                    LLVMBasicBlockRef falseBlock = LLVMGetSuccessor(terminator, 1);

                    directSuccessorsBlock.get(block).add(trueBlock);
                    directSuccessorsBlock.get(block).add(falseBlock);
                    directPredecessorsBlock.get(trueBlock).add(block);
                    directPredecessorsBlock.get(falseBlock).add(block);

                } else if (LLVMGetNumOperands(terminator) == 1) {
                    // 无条件跳转
                    LLVMBasicBlockRef targetBlock = LLVMGetSuccessor(terminator, 0);
                    directSuccessorsBlock.get(block).add(targetBlock);
                    directPredecessorsBlock.get(targetBlock).add(block);
                }
            }
        }

        // 递归计算所有前驱和后继关系
        for (LLVMBasicBlockRef block : directSuccessorsBlock.keySet()) {
            computeTransitiveSuccessors(block, new HashSet<>());
        }

        for (LLVMBasicBlockRef block : directPredecessorsBlock.keySet()) {
            computeTransitivePredecessors(block, new HashSet<>());
        }
    }

    // 递归计算传递后继
    private void computeTransitiveSuccessors(LLVMBasicBlockRef block, Set<LLVMBasicBlockRef> visited) {
        if (visited.contains(block)) {
            return;
        }
        visited.add(block);

        for (LLVMBasicBlockRef directSucc : directSuccessorsBlock.get(block)) {
            successorsBlock.get(block).add(directSucc);
            computeTransitiveSuccessors(directSucc, visited);

            // 添加后继的后继
            for (LLVMBasicBlockRef succSucc : successorsBlock.get(directSucc)) {
                successorsBlock.get(block).add(succSucc);
            }
        }
    }

    // 递归计算传递前驱
    private void computeTransitivePredecessors(LLVMBasicBlockRef block, Set<LLVMBasicBlockRef> visited) {
        if (visited.contains(block)) {
            return;
        }
        visited.add(block);

        for (LLVMBasicBlockRef directPred : directPredecessorsBlock.get(block)) {
            predecessorsBlock.get(block).add(directPred);
            computeTransitivePredecessors(directPred, visited);

            // 添加前驱的前驱
            for (LLVMBasicBlockRef predPred : predecessorsBlock.get(directPred)) {
                predecessorsBlock.get(block).add(predPred);
            }
        }
    }

    public void constantPropagation(LLVMValueRef function) {


        List<LLVMValueRef> instructions = new ArrayList<>();
        Map<LLVMValueRef, List<LLVMValueRef>> predInsts = new HashMap<>();
        Map<LLVMValueRef, List<LLVMValueRef>> succInsts = new HashMap<>();

        createCFG();

        // 收集指令 & 初始化图结构
        for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
             block != null && !block.equals(new LLVMBasicBlockRef(null));
             block = LLVMGetNextBasicBlock(block)) {

            for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                 instr != null && !instr.equals(new LLVMValueRef(null));
                 instr = LLVMGetNextInstruction(instr)) {
                instructions.add(instr);
                predInsts.put(instr, new ArrayList<>());
                succInsts.put(instr, new ArrayList<>());
            }
        }

        // 构建指令间的前驱后继关系
        for (LLVMValueRef instr : instructions) {
            LLVMBasicBlockRef block = LLVMGetInstructionParent(instr);
            for (LLVMBasicBlockRef preBlock : directPredecessorsBlock.get(block)) {
                predInsts.get(instr).add(LLVMGetLastInstruction(preBlock));
            }
            for (LLVMBasicBlockRef succBlock : directSuccessorsBlock.get(block)) {
                succInsts.get(instr).add(LLVMGetFirstInstruction(succBlock));
            }

            LLVMValueRef next = LLVMGetNextInstruction(instr);
            if (next != null) {
                succInsts.get(instr).add(next);
                predInsts.get(next).add(instr);
            }
        }


        // 初始化数据流映射
        Map<LLVMValueRef, Map<LLVMValueRef, LatticeValue>> memIn = new HashMap<>();
        Map<LLVMValueRef, Map<LLVMValueRef, LatticeValue>> memOut = new HashMap<>();
        Map<LLVMValueRef, Map<LLVMValueRef, LatticeValue>> reg = new HashMap<>();

        Set<LLVMValueRef> worklist = new LinkedHashSet<>(instructions);
        for (LLVMValueRef instr : instructions) {
            memIn.put(instr, new HashMap<>());
            memOut.put(instr, new HashMap<>());
            reg.put(instr, new HashMap<>());
        }

        // 初始化全局变量状态
        Map<LLVMValueRef, LatticeValue> initialMemory = new HashMap<>();

        for (LLVMValueRef global = LLVMGetFirstGlobal(module);
             global != null && !global.equals(new LLVMValueRef(null));
             global = LLVMGetNextGlobal(global)) {

            LLVMValueRef initializer = LLVMGetInitializer(global);
            if (initializer != null && LLVMIsConstant(initializer) == 1) {
                long value = LLVMConstIntGetZExtValue(initializer);
                initialMemory.put(global, new LatticeValue(CONST, (int) value));
            } else {
                initialMemory.put(global, new LatticeValue(NAC, 0));
            }
        }

        // 将初始内存映射应用到每条指令的 memIn 和 memOut
        for (LLVMValueRef instr : instructions) {
            memIn.get(instr).putAll(initialMemory);
            memOut.get(instr).putAll(initialMemory);
        }

        // 主循环
        while (!worklist.isEmpty()) {
            // 从worklist弹出一个指令
            LLVMValueRef instr = worklist.iterator().next();
            worklist.remove(instr);

            Map<LLVMValueRef, LatticeValue> inMemory = new HashMap<>();
            for (LLVMValueRef pred : predInsts.get(instr)) {
                Map<LLVMValueRef, LatticeValue> predOut = memOut.get(pred);
                for (Map.Entry<LLVMValueRef, LatticeValue> entry : predOut.entrySet()) {
                    LLVMValueRef ptr = entry.getKey();
                    LatticeValue val = entry.getValue();

                    inMemory.put(ptr, LatticeValue.meet(inMemory.getOrDefault(ptr, new LatticeValue(UNDEF, 0)), val));
                }
            }

            // 如果是第一条指令没有前驱，就用默认初始状态（已经在初始化时设置过）
            if (predInsts.get(instr).isEmpty()) {
                inMemory = new HashMap<>(memIn.get(instr));
            }

            memIn.put(instr, inMemory);
            Map<LLVMValueRef, LatticeValue> outMemory = new HashMap<>(inMemory);
            Map<LLVMValueRef, LatticeValue> regVals = new HashMap<>();

            int opcode = LLVMGetInstructionOpcode(instr);

            // 处理不同的指令类型
            if (opcode == LLVMStore) {

                LLVMValueRef value = LLVMGetOperand(instr, 0);
                LLVMValueRef ptr = LLVMGetOperand(instr, 1);
                LatticeValue val = getOperandLatticeValue(value, reg, memIn.get(instr));
                outMemory.put(ptr, val);

            } else if (opcode == LLVMLoad) {
                LLVMValueRef ptr = LLVMGetOperand(instr, 0);
                LatticeValue val = inMemory.getOrDefault(ptr, new LatticeValue(UNDEF, 0));
                regVals.put(instr, val);

            } else if (opcode == LLVMBr) {

                if (LLVMIsConditional(instr) != 0) {
                    LLVMValueRef cond = LLVMGetCondition(instr);
                    LatticeValue condVal = getOperandLatticeValue(cond, reg, memIn.get(instr));

                    // 如果条件为常量，控制流可简化（可选，仅更新 successors 图）
                    if (condVal.type == CONST) {
                        LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(instr, 0);
                        LLVMBasicBlockRef falseBlock = LLVMGetSuccessor(instr, 1);
                        LLVMBasicBlockRef targetBlock = (condVal.value != 0) ? trueBlock : falseBlock;

                        // 更新 successors，仅保留实际可能跳转的路径
                        succInsts.get(instr).clear();
                        LLVMValueRef targetInstr = LLVMGetFirstInstruction(targetBlock);
                        if (targetInstr != null) {
                            succInsts.get(instr).add(targetInstr);
                        }


                    } else {
                        // 保持两个分支（无法确定条件）
                        LLVMBasicBlockRef trueBlock = LLVMGetSuccessor(instr, 0);
                        LLVMBasicBlockRef falseBlock = LLVMGetSuccessor(instr, 1);

                        succInsts.get(instr).clear();
                        LLVMValueRef tInstr = LLVMGetFirstInstruction(trueBlock);
                        LLVMValueRef fInstr = LLVMGetFirstInstruction(falseBlock);
                        if (tInstr != null) succInsts.get(instr).add(tInstr);
                        if (fInstr != null) succInsts.get(instr).add(fInstr);
                    }
                }

            } else if (opcode == LLVMZExt) {

                LLVMValueRef src = LLVMGetOperand(instr, 0);
                LatticeValue srcVal = getOperandLatticeValue(src, reg, memIn.get(instr));

                if (srcVal.type == CONST) {
                    regVals.put(instr, new LatticeValue(CONST, srcVal.value));
                } else {
                    regVals.put(instr, new LatticeValue(srcVal.type, 0));
                }

            } else if (opcode == LLVMICmp) {

                int predicate = LLVMGetICmpPredicate(instr);
                LLVMValueRef lhs = LLVMGetOperand(instr, 0);
                LLVMValueRef rhs = LLVMGetOperand(instr, 1);
                LatticeValue lval = getOperandLatticeValue(lhs, reg, memIn.get(instr));
                LatticeValue rval = getOperandLatticeValue(rhs, reg, memIn.get(instr));

                if (lval.type == CONST && rval.type == CONST) {
                    boolean result;
                    switch (predicate) {
                        case LLVMIntEQ:
                            result = lval.value == rval.value;
                            break;
                        case LLVMIntNE:
                            result = lval.value != rval.value;
                            break;
                        case LLVMIntSGT:
                            result = lval.value > rval.value;
                            break;
                        case LLVMIntSGE:
                            result = lval.value >= rval.value;
                            break;
                        case LLVMIntSLT:
                            result = lval.value < rval.value;
                            break;
                        case LLVMIntSLE:
                            result = lval.value <= rval.value;
                            break;
                        default:
                            result = false; // handle more cases as needed
                            System.err.println("Unexpected ICmp predicate: " + LLVMGetICmpPredicate(instr));
                            break;
                    }
                    regVals.put(instr, new LatticeValue(CONST, result ? 1 : 0));
                } else {
                    regVals.put(instr, LatticeValue.meet(lval, rval));
                }
            } else if (opcode == LLVMAdd || opcode == LLVMSub || opcode == LLVMMul || opcode == LLVMSDiv || opcode == LLVMSRem) {
                LLVMValueRef op1 = LLVMGetOperand(instr, 0);
                LLVMValueRef op2 = LLVMGetOperand(instr, 1);
                LatticeValue val1 = getOperandLatticeValue(op1, reg, memIn.get(instr));
                LatticeValue val2 = getOperandLatticeValue(op2, reg, memIn.get(instr));

                if (val1.type == CONST && val2.type == CONST) {
                    int result = 0;
                    switch (opcode) {
                        case LLVMAdd: result = val1.value + val2.value; break;
                        case LLVMSub: result = val1.value - val2.value; break;
                        case LLVMMul: result = val1.value * val2.value; break;
                        case LLVMSDiv: result = val1.value / val2.value; break;
                        case LLVMSRem: result = val1.value % val2.value; break;
                    }
                    regVals.put(instr, new LatticeValue(CONST, result));
                } else {
                    regVals.put(instr, LatticeValue.meet(val1, val2));
                }

            }

            if (!memOut.get(instr).equals(outMemory) || !reg.get(instr).equals(regVals)) {
                memOut.put(instr, outMemory);
                reg.put(instr, regVals);
                worklist.addAll(succInsts.get(instr));
            }
        }

        // 替换常量
        for (LLVMValueRef instr : instructions) {
            LatticeValue val = reg.get(instr).get(instr);
            if (val != null && val.type == CONST) {
                if (LLVMIsAReturnInst(instr) != null) {
                    continue;
                }
                LLVMValueRef constVal = LLVMConstInt(LLVMTypeOf(instr), val.value, 0);
                LLVMReplaceAllUsesWith(instr, constVal);
                LLVMInstructionEraseFromParent(instr);
            }
        }
    }

    private LatticeValue getOperandLatticeValue(LLVMValueRef operand,
                                                Map<LLVMValueRef, Map<LLVMValueRef, LatticeValue>> regOut,
                                                Map<LLVMValueRef, LatticeValue> memory) {

        for (Map<LLVMValueRef, LatticeValue> prev : regOut.values()) {
            if (prev.containsKey(operand)) {
                return prev.get(operand);
            }
        }

        if (LLVMIsConstant(operand) == 1) {
            return new LatticeValue(CONST, (int) LLVMConstIntGetZExtValue(operand));
        } else if (memory.containsKey(operand)) {
            return memory.get(operand);
        } else {
            return new LatticeValue(UNDEF, 0);
        }
    }


    private void unusedVariableDelete() {
        Map<LLVMValueRef, Integer> usedCount = new HashMap<>();

        LLVMValueRef function = LLVMGetFirstFunction(module);

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

        // 删除未使用的变量
        Stack<LLVMValueRef> deleteSet = new Stack<>(); // 存储需要删除的指令

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

    public void unusedElimination(LLVMValueRef function) {
        // 统计变量使用次数
        Map<LLVMValueRef, Integer> useCounts = new HashMap<>();

        // 第一次遍历：统计所有变量的使用次数
        countVariableUses(function, useCounts);

        // 第二次遍历：收集需要删除的指令
        List<LLVMValueRef> instructionsToDelete = collectUnusedInstructions(function, useCounts);

        // 按照正确顺序删除指令
        deleteInstructionsSafely(instructionsToDelete);
    }

    private void countVariableUses(LLVMValueRef function, Map<LLVMValueRef, Integer> useCounts) {
        for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
             block != null && !block.equals(new LLVMBasicBlockRef(null));
             block = LLVMGetNextBasicBlock(block)) {

            for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                 instr != null && !instr.equals(new LLVMValueRef(null));
                 instr = LLVMGetNextInstruction(instr)) {

                int opcode = LLVMGetInstructionOpcode(instr);

                // 初始化定义指令的使用计数
                if (isDefinitionInstruction(opcode)) {
                    useCounts.put(instr, 0);
                }

                // 统计操作数的使用情况
                countOperandUses(instr, opcode, useCounts);
            }
        }
    }

    private boolean isDefinitionInstruction(int opcode) {
        return opcode == LLVMAlloca ||
                opcode == LLVMLoad ||
                opcode == LLVMAdd || opcode == LLVMSub || opcode == LLVMMul ||
                opcode == LLVMSDiv || opcode == LLVMSRem ||
                opcode == LLVMICmp ||
                opcode == LLVMZExt ||
                opcode == LLVMGetElementPtr;
    }

    private void countOperandUses(LLVMValueRef instr, int opcode, Map<LLVMValueRef, Integer> useCounts) {
        // 处理特殊指令类型
        if (opcode == LLVMStore) {
            LLVMValueRef value = LLVMGetOperand(instr, 0);
            LLVMValueRef ptr = LLVMGetOperand(instr, 1);
            if (!isGlobalOrConstant(value)) {
                useCounts.put(value, useCounts.getOrDefault(value, 0) + 1);
            }
            return;
        }

        // 处理分支指令
        if (opcode == LLVMBr && LLVMIsConditional(instr) != 0) {
            LLVMValueRef cond = LLVMGetCondition(instr);
            if (!isGlobalOrConstant(cond)) {
                useCounts.put(cond, useCounts.getOrDefault(cond, 0) + 1);
            }
            return;
        }

        // 处理返回指令
        if (opcode == LLVMRet && LLVMGetNumOperands(instr) > 0) {
            LLVMValueRef retVal = LLVMGetOperand(instr, 0);
            if (!isGlobalOrConstant(retVal)) {
                useCounts.put(retVal, useCounts.getOrDefault(retVal, 0) + 1);
            }
            return;
        }

        // 处理普通指令的操作数
        for (int i = 0; i < LLVMGetNumOperands(instr); i++) {
            LLVMValueRef operand = LLVMGetOperand(instr, i);
            if (!isGlobalOrConstant(operand)) {
                useCounts.put(operand, useCounts.getOrDefault(operand, 0) + 1);
            }
        }
    }

    private boolean isGlobalOrConstant(LLVMValueRef value) {
        return LLVMIsAGlobalVariable(value) != null || LLVMIsConstant(value) == 1;
    }

    private List<LLVMValueRef> collectUnusedInstructions(LLVMValueRef function, Map<LLVMValueRef, Integer> useCounts) {
        List<LLVMValueRef> instructionsToDelete = new ArrayList<>();

        for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
             block != null && !block.equals(new LLVMBasicBlockRef(null));
             block = LLVMGetNextBasicBlock(block)) {

            for (LLVMValueRef instr = LLVMGetFirstInstruction(block);
                 instr != null && !instr.equals(new LLVMValueRef(null));
                 instr = LLVMGetNextInstruction(instr)) {

                if (shouldDeleteInstruction(instr, useCounts)) {
                    instructionsToDelete.add(instr);
                }
            }
        }

        return instructionsToDelete;
    }

    private boolean shouldDeleteInstruction(LLVMValueRef instr, Map<LLVMValueRef, Integer> useCounts) {
        int opcode = LLVMGetInstructionOpcode(instr);

        // 保留关键指令
        if (opcode == LLVMRet || opcode == LLVMBr) {
            return false;
        }

        // 检查是否是定义指令且未被使用
        if (isDefinitionInstruction(opcode)) {
            return useCounts.getOrDefault(instr, 0) == 0;
        }

        // 处理store指令的特殊情况
        if (opcode == LLVMStore) {
            LLVMValueRef ptr = LLVMGetOperand(instr, 1);
            return !isGlobalOrConstant(ptr) && useCounts.getOrDefault(ptr, 0) == 0;
        }

        return false;
    }

    private void deleteInstructionsSafely(List<LLVMValueRef> instructionsToDelete) {
        // 构建CFG以便确定删除顺序
        createCFG();

        // 按照基本块和指令顺序排序，确保安全删除
        instructionsToDelete.sort((o1, o2) -> {
            LLVMBasicBlockRef block1 = LLVMGetInstructionParent(o1);
            LLVMBasicBlockRef block2 = LLVMGetInstructionParent(o2);

            // 不同基本块，根据CFG关系排序
            if (!block1.equals(block2)) {
                if (predecessorsBlock.get(block1).contains(block2)) {
                    return 1; // block2是block1的前驱，先删block1的指令
                } else if (predecessorsBlock.get(block2).contains(block1)) {
                    return -1; // block1是block2的前驱，先删block2的指令
                }
            }

            // 同一基本块内，按指令顺序排序（后定义的先删除）
            LLVMValueRef current = LLVMGetFirstInstruction(block1);
            while (current != null && !current.equals(new LLVMValueRef(null))) {
                if (current.equals(o1)) return 1;
                if (current.equals(o2)) return -1;
                current = LLVMGetNextInstruction(current);
            }

            return 0;
        });

        // 执行删除操作
        for (LLVMValueRef instr : instructionsToDelete) {
            LLVMInstructionEraseFromParent(instr);
        }
    }
}

