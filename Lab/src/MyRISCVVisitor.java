import org.bytedeco.llvm.LLVM.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class MyRISCVVisitor {
    public final LLVMModuleRef module;
    public final String destPath;
    private final AsmBuilder asmBuilder;

    // 活跃区间管理
    private final Map<LLVMValueRef, LiveInterval> liveIntervals;

    private int instructionCounter;

    private int stackSize;

    public MyRISCVVisitor(LLVMModuleRef module, String destPath) {
        this.module = module;
        this.destPath = destPath;
        this.asmBuilder = new AsmBuilder();
        this.liveIntervals = new HashMap<>();
    }

    public void generateRiscVCode() {

        // 处理全局变量
        asmBuilder.createDataSection();
        for (LLVMValueRef value = LLVMGetFirstGlobal(module); value != null; value = LLVMGetNextGlobal(value)) {
            asmBuilder.label(LLVMGetValueName(value).getString());
            LLVMValueRef init = LLVMGetInitializer(value);
            if (init != null && LLVMIsAConstantInt(init) != null) {
                asmBuilder.addString("  .word " +  LLVMConstIntGetSExtValue(init) + "\n");
            } else {
                asmBuilder.addString("  .word " + "0\n");
            }
        }

        asmBuilder.addString("\n");

        // 处理函数
        asmBuilder.createTextSection();
        for (LLVMValueRef func = LLVMGetFirstFunction(module); func != null; func = LLVMGetNextFunction(func)) {
            processFunction(func);
        }

        // 输出到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(destPath))) {
            writer.write(asmBuilder.getAsmString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processFunction(LLVMValueRef func) {
        // 1. 收集活跃区间信息
        collectLiveIntervals(func);

        // 2. 执行线性扫描寄存器分配
        LinearScanAllocator allocator = new LinearScanAllocator(new ArrayList<>(liveIntervals.values()));
        allocator.allocate();
        this.stackSize = allocator.getStackSize();

        // 3. 生成函数代码
        generateFunctionCode(func);
    }

    private void collectLiveIntervals(LLVMValueRef func) {
        liveIntervals.clear();
        instructionCounter = 0;

        // 第一遍遍历：收集定义和使用点
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                instructionCounter++;

                // 处理定义
                if (isDefinition(inst)) {
                    LLVMValueRef def = getDefinedValue(inst);
                    if (def != null && !liveIntervals.containsKey(def)) {
                        liveIntervals.put(def, new LiveInterval(def, instructionCounter, instructionCounter));
                    }
                }

                // 处理使用
                for (LLVMValueRef op : getOperands(inst)) {
                    if (op != null && liveIntervals.containsKey(op)) {
                        liveIntervals.get(op).end = instructionCounter;
                    }
                }
            }
        }
    }

    private void generateFunctionCode(LLVMValueRef func) {
        String funcName = LLVMGetValueName(func).getString();
        asmBuilder.globalSymbol(funcName);
        asmBuilder.label(funcName);

        // 函数prologue
        asmBuilder.op2("addi", "sp", "sp", String.valueOf(-stackSize));

        // 生成基本块代码
        for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
            asmBuilder.label(LLVMGetBasicBlockName(bb).getString());

            for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                generateInstruction(inst);
                asmBuilder.addString("\n");
            }
        }
    }

    private void generateInstruction(LLVMValueRef inst) {
        int opcode = LLVMGetInstructionOpcode(inst);
        int operandNum = LLVMGetNumOperands(inst);
        LLVMValueRef op1 =  null;
        LLVMValueRef op2 =  null;
        LLVMValueRef op3 =  null;

        if (operandNum == 3) {
            op1 = LLVMGetOperand(inst, 0);
            op2 = LLVMGetOperand(inst, 1);
            op3 = LLVMGetOperand(inst, 2);
        } else if (operandNum == 2) {
            op1 = LLVMGetOperand(inst, 0);
            op2 = LLVMGetOperand(inst, 1);
        } else if (operandNum == 1) {
            op1 = LLVMGetOperand(inst, 0);
        }

        switch (opcode) {
            case LLVMDICommonBlockMetadataKind:
                break;
            case LLVMICmp:
                generateICmp(inst, op1, op2, op3);
                break;
            case LLVMBr:
                generateBranch(inst, op1, op2);
                break;
            case LLVMRet:
                generateReturn(op1);
                break;
            case LLVMAlloca:
                // 已经在活跃区间收集中处理
                break;
            case LLVMLoad:
                generateLoad(inst, op1);
                break;
            case LLVMStore:
                generateStore(op1, op2);
                break;
            case LLVMAdd:
            case LLVMSub:
            case LLVMMul:
            case LLVMSDiv:
            case LLVMSRem:
                generateBinaryOp(inst, opcode, op1, op2);
                break;
            default:
                System.out.println("Unsupported instruction: " + LLVMGetValueName(inst).getString());
                throw new RuntimeException("Unsupported opcode: " + opcode);
        }
    }

    private void generateReturn(LLVMValueRef retValue) {
        if (retValue != null) {
            LiveInterval interval = liveIntervals.get(retValue);
            if (interval != null) {
                if (interval.register != null) {
                    asmBuilder.op1("mv", "a0", interval.register);
                } else {
                    asmBuilder.op1("lw", "a0", String.format("%d(sp)", interval.stackOffset));
                }
            } else if (LLVMIsAConstantInt(retValue) != null) {
                asmBuilder.op1("li", "a0", String.valueOf(LLVMConstIntGetSExtValue(retValue)));
            }
        }

        // 函数epilogue
        asmBuilder.op2("addi", "sp", "sp", String.valueOf(stackSize));

        asmBuilder.op1("li", "a7", "93");
        asmBuilder.addString("  ecall\n");

//        int interpreterValue = new LLVMInterpreter(module).processBlock(LLVMGetEntryBasicBlock(LLVMGetNamedFunction(module, "main")));
//
//        asmBuilder.op1("li", "a0", String.valueOf(interpreterValue));
//
//        // 函数epilogue
//        asmBuilder.op2("addi", "sp", "sp", String.valueOf(stackSize));
//
//        asmBuilder.op1("li", "a7", "93");
//        asmBuilder.addString("  ecall\n");
    }

    private void generateICmp(LLVMValueRef inst, LLVMValueRef op1, LLVMValueRef op2, LLVMValueRef op3) {
        LiveInterval dest = liveIntervals.get(inst);
        if (dest == null) return;

        int predicate = LLVMGetICmpPredicate(inst);
        String op1Reg = getOperandRegister(op1, "t0");
        String op2Reg = getOperandRegister(op2, "t1");

        String resultReg = dest.register != null ? dest.register : "t2";

        switch (predicate) {
            case LLVMIntEQ:  // ==
                asmBuilder.op2("xor", resultReg, op1Reg, op2Reg);
                asmBuilder.op1("seqz", resultReg, resultReg);
                break;
            case LLVMIntNE:  // !=
                asmBuilder.op2("xor", resultReg, op1Reg, op2Reg);
                asmBuilder.op1("snez", resultReg, resultReg);
                break;
            case LLVMIntSLT: // < (signed)
                asmBuilder.op2("slt", resultReg, op1Reg, op2Reg);
                break;
            case LLVMIntSLE: // <= (signed)
                asmBuilder.op2("sgt", resultReg, op1Reg, op2Reg);
                asmBuilder.op1("seqz", resultReg, resultReg);
                break;
            case LLVMIntSGT: // > (signed)
                asmBuilder.op2("sgt", resultReg, op1Reg, op2Reg);
                break;
            case LLVMIntSGE: // >= (signed)
                asmBuilder.op2("slt", resultReg, op1Reg, op2Reg);
                asmBuilder.op1("seqz", resultReg, resultReg);
                break;
            case LLVMIntULT: // < (unsigned)
                asmBuilder.op2("sltu", resultReg, op1Reg, op2Reg);
                break;
            case LLVMIntULE: // <= (unsigned)
                asmBuilder.op2("sgtu", resultReg, op1Reg, op2Reg);
                asmBuilder.op2("xori", resultReg, resultReg, "1");
                break;
            case LLVMIntUGT: // > (unsigned)
                asmBuilder.op2("sgtu", resultReg, op1Reg, op2Reg);
                break;
            case LLVMIntUGE: // >= (unsigned)
                asmBuilder.op2("sltu", resultReg, op1Reg, op2Reg);
                asmBuilder.op2("xori", resultReg, resultReg, "1");
                break;
            default:
                throw new RuntimeException("Unsupported ICmp predicate: " + predicate);
        }

        if (dest.register == null) {
            asmBuilder.op1("sw", resultReg, String.format("%d(sp)", dest.stackOffset));
        }
    }

    private void generateBranch(LLVMValueRef inst, LLVMValueRef trueBB, LLVMValueRef falseBB) {
        int numOperands = LLVMGetNumOperands(inst);
        if (numOperands == 1) {
            LLVMValueRef dest = LLVMGetOperand(inst, 0);
            String loop_label = LLVMGetValueName(dest).getString();
            asmBuilder.op0("j", loop_label);
        } else if (numOperands == 3) {
            LLVMValueRef cond = LLVMGetOperand(inst, 0);
            String condReg = getOperandRegister(cond, "t0");
            String trueLabel = LLVMGetValueName(LLVMGetOperand(inst, 2)).getString();
            String falseLabel = LLVMGetValueName(LLVMGetOperand(inst, 1)).getString();

            asmBuilder.op1("bnez", condReg, trueLabel);
            asmBuilder.op0("j", falseLabel);
        }
    }


    private void generateLoad(LLVMValueRef inst, LLVMValueRef ptr) {
        LiveInterval dest = liveIntervals.get(inst);
        if (dest == null) return;

        if (LLVMIsAGlobalVariable(ptr) != null) {
            // 加载全局变量
            String globalName = LLVMGetValueName(ptr).getString();
            if (dest.register != null) {
                asmBuilder.op1("la", "t0", globalName);
                asmBuilder.op1("lw", dest.register, "0(t0)");
            } else {
                asmBuilder.op1("la", "t0", globalName);
                asmBuilder.op1("lw", "t0", "0(t0)");
                asmBuilder.op1("sw", "t0", String.format("%d(sp)", dest.stackOffset));
            }
        } else {
            // 加载栈变量
            LiveInterval src = liveIntervals.get(ptr);
            if (src != null) {
                if (dest.register != null) {
                    if (src.register != null) {
                        asmBuilder.op1("mv", dest.register, src.register);
                    } else {
                        asmBuilder.op1("lw", dest.register, String.format("%d(sp)", src.stackOffset));
                    }
                } else {
                    if (src.register != null) {
                        asmBuilder.op1("sw", src.register, String.format("%d(sp)", dest.stackOffset));
                    } else {
                        asmBuilder.op1("lw", "t0", String.format("%d(sp)", src.stackOffset));
                        asmBuilder.op1("sw", "t0", String.format("%d(sp)", dest.stackOffset));
                    }
                }
            }
        }
    }

    private void generateStore(LLVMValueRef value, LLVMValueRef ptr) {
        // 处理存储的值
        String valueReg = null;
        if (LLVMIsAConstantInt(value) != null) {
            asmBuilder.op1("li", "t0", String.valueOf(LLVMConstIntGetSExtValue(value)));
            valueReg = "t0";
        } else {
            LiveInterval valueInterval = liveIntervals.get(value);
            if (valueInterval != null) {
                valueReg = valueInterval.register != null ? valueInterval.register :
                        String.format("%d(sp)", valueInterval.stackOffset);
            }
        }
        if (valueReg == null) return;

        // 处理存储目标
        if (LLVMIsAGlobalVariable(ptr) != null) {
            // 存储到全局变量
            String globalName = LLVMGetValueName(ptr).getString();
            asmBuilder.op1("la", "t1", globalName);
            if (valueReg.startsWith("t") || valueReg.startsWith("a") || valueReg.startsWith("s")) {
                asmBuilder.op1("sw", valueReg, "0(t1)");
            } else {
                asmBuilder.op1("lw", "t0", valueReg);
                asmBuilder.op1("sw", "t0", "0(t1)");
            }
        } else {
            // 存储到栈变量
            LiveInterval ptrInterval = liveIntervals.get(ptr);
            if (ptrInterval != null) {
                if (ptrInterval.register != null) {
                    if (valueReg.startsWith("t") || valueReg.startsWith("a") || valueReg.startsWith("s")) {
                        asmBuilder.op1("mv", ptrInterval.register, valueReg);
                    } else {
                        asmBuilder.op1("lw", "t0", valueReg);
                        asmBuilder.op1("mv", ptrInterval.register, "t0");
                    }
                } else {
                    if (valueReg.startsWith("t") || valueReg.startsWith("a") || valueReg.startsWith("s")) {
                        asmBuilder.op1("sw", valueReg, String.format("%d(sp)", ptrInterval.stackOffset));
                    } else {
                        asmBuilder.op1("lw", "t0", valueReg);
                        asmBuilder.op1("sw", "t0", String.format("%d(sp)", ptrInterval.stackOffset));
                    }
                }
            }
        }
    }

    private void generateBinaryOp(LLVMValueRef inst, int opcode, LLVMValueRef op1, LLVMValueRef op2) {
        LiveInterval dest = liveIntervals.get(inst);
        if (dest == null) return;

        String op;
        switch (opcode) {
            case LLVMAdd: op = "add"; break;
            case LLVMSub: op = "sub"; break;
            case LLVMMul: op = "mul"; break;
            case LLVMSDiv: op = "div"; break;
            case LLVMSRem: op = "rem"; break;
            default: return;
        }

        // 获取操作数1
        String op1Reg = getOperandRegister(op1, "t0");
        // 获取操作数2
        String op2Reg = getOperandRegister(op2, "t1");

        if (dest.register != null) {
            asmBuilder.op2(op, dest.register, op1Reg, op2Reg);
        } else {
            asmBuilder.op2(op, "t2", op1Reg, op2Reg);
            asmBuilder.op1("sw", "t2", String.format("%d(sp)", dest.stackOffset));
        }
    }


    private String getOperandRegister(LLVMValueRef operand, String tempReg) {
//        if (LLVMIsAConstantInt(operand) != null) {
//            asmBuilder.op1("li", tempReg, String.valueOf(LLVMConstIntGetSExtValue(operand)));
//            return tempReg;
//        }
//
//        LiveInterval interval = liveIntervals.get(operand);
//        if (interval != null) {
//            if (interval.register != null) {
//                return interval.register;
//            } else {
//                asmBuilder.op1("lw", tempReg, String.format("%d(sp)", interval.stackOffset));
//                return tempReg;
//            }
//        }
//
//        return tempReg;

        if (LLVMIsAConstantInt(operand) != null) {
            asmBuilder.op1("li", tempReg, String.valueOf(LLVMConstIntGetSExtValue(operand)));
            return tempReg;
        } else if (LLVMIsAInstruction(operand) != null) {
            LiveInterval interval = liveIntervals.get(operand);
            if (interval != null) {
                return interval.register != null ? interval.register :
                        String.format("%d(sp)", interval.stackOffset);
            }
        } else if (LLVMIsAGlobalVariable(operand) != null) {
            return LLVMGetValueName(operand).getString();
        }
        return tempReg;
    }

    // Helper methods
    private boolean isDefinition(LLVMValueRef inst) {
        return LLVMIsAInstruction(inst) != null &&
                (LLVMIsALoadInst(inst) != null ||
                        LLVMIsABinaryOperator(inst) != null ||
                        LLVMIsAAllocaInst(inst) != null ||
                        LLVMIsAICmpInst(inst) != null);
    }

    private LLVMValueRef getDefinedValue(LLVMValueRef inst) {
        if (LLVMIsAInstruction(inst) != null) {
            return inst;
        }
        return null;
    }

    private List<LLVMValueRef> getOperands(LLVMValueRef inst) {
        List<LLVMValueRef> operands = new ArrayList<>();
        int numOperands = LLVMGetNumOperands(inst);
        for (int i = 0; i < numOperands; i++) {
            LLVMValueRef op = LLVMGetOperand(inst, i);
            if (op != null) {
                operands.add(op);
            }
        }
        return operands;
    }

    // LiveInterval 类
    private static class LiveInterval implements Comparable<LiveInterval> {
        final LLVMValueRef value;
        final int start;
        int end;
        String register;
        int stackOffset;

        LiveInterval(LLVMValueRef value, int start, int end) {
            this.value = value;
            this.start = start;
            this.end = end;
            this.register = null;
            this.stackOffset = -1;
        }

        @Override
        public int compareTo(LiveInterval other) {
            return Integer.compare(this.start, other.start);
        }
    }

    // 线性扫描分配器
    private static class LinearScanAllocator {
        private final List<LiveInterval> intervals;
        private final List<String> availableRegs = new ArrayList<>(Arrays.asList(
                "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7",
                "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11",
                "t3", "t4", "t5", "t6"
        ));
        private final List<LiveInterval> active;
        private int stackOffset;

        public LinearScanAllocator(List<LiveInterval> intervals) {
            this.intervals = new ArrayList<>(intervals);
            this.active = new ArrayList<>();
            this.stackOffset = 0;
        }

        public void allocate() {
            // 按起始点排序
            Collections.sort(intervals);

            for (LiveInterval current : intervals) {
                // 1. 释放已结束的区间
                expireOldIntervals(current.start);

                // 2. 检查寄存器是否足够
                if (active.size() == availableRegs.size()) {
                    // 需要溢出
                    spillAtInterval(current);
                } else {
                    // 分配寄存器
                    allocateRegister(current);
                }
            }
        }

        private void expireOldIntervals(int currentPoint) {
            Iterator<LiveInterval> iterator = active.iterator();
            while (iterator.hasNext()) {
                LiveInterval interval = iterator.next();
                if (interval.end < currentPoint) {
                    // 释放寄存器
                    availableRegs.add(interval.register);
                    iterator.remove();
                }
            }
            // 保持active按end排序
            active.sort(Comparator.comparingInt(i -> i.end));
        }

        private void spillAtInterval(LiveInterval current) {
            // 找到最后一个active区间(即end最大的)
            LiveInterval spill = active.get(active.size() - 1);

            if (spill.end > current.end) {
                // 让当前区间占用spill的寄存器，spill溢出到栈
                current.register = spill.register;
                spill.register = null;
                spill.stackOffset = allocateStackSlot();

                active.remove(spill);
                active.add(current);
            } else {
                // 直接溢出当前区间
                current.stackOffset = allocateStackSlot();
            }
        }

        private void allocateRegister(LiveInterval interval) {
            if (!availableRegs.isEmpty()) {
                interval.register = availableRegs.remove(0);
                active.add(interval);
                // 保持active按end排序
                active.sort(Comparator.comparingInt(i -> i.end));
            }
        }

        private int allocateStackSlot() {
            int offset = stackOffset;
            stackOffset += 4; // 每个变量占4字节
            return offset;
        }

        public int getStackSize() {
            // 对齐到16字节
            return (stackOffset + 15) & ~15;
        }
    }
}