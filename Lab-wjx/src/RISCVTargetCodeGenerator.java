import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.llvm.LLVM.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class RISCVTargetCodeGenerator {
    private final LLVMModuleRef module;
    private final String target;
    private Map<LLVMBasicBlockRef, Integer> blockMap = new HashMap<>(); // 基本块->块编号


    public RISCVTargetCodeGenerator(LLVMModuleRef module, String target) {
        this.module = module;
        this.target = target;
    }

    public void generate() {
        try (FileWriter writer = new FileWriter(target)) {
            // .data 段 - 全局变量
            for (LLVMValueRef globalVar = LLVMGetFirstGlobal(module); globalVar != null; globalVar = LLVMGetNextGlobal(globalVar)) {
                String varName = LLVMGetValueName(globalVar).getString();
                if (varName.isEmpty()) continue;
                writer.write("    .data\n");
                writer.write(varName + ":\n");
                if (!LLVMIsAGlobalVariable(globalVar).isNull()) {
                    LLVMValueRef initializer = LLVMGetInitializer(globalVar);
                    if (!initializer.isNull()) {
                        long value = LLVMConstIntGetSExtValue(initializer);
                        writer.write("    .word " + value + "\n\n");
                    }
                }
            }

            // .text 段 - 函数体
            writer.write("    .text\n    .globl main\nmain:\n");

            for (LLVMValueRef func = LLVMGetFirstFunction(module); func != null; func = LLVMGetNextFunction(func)) {
                RegisterAllocator allocator = new RegisterAllocator(func, writer);
                this.blockMap = allocator.getBlockNumbers();
                int stackSize = allocator.getMaxSize();
                writer.write("    addi sp, sp, -" + stackSize + "\n");
                ArrayList<LLVMBasicBlockRef> blocks = new ArrayList<>();
                // 按照编号将基本块排序
                for (LLVMBasicBlockRef bb = LLVMGetFirstBasicBlock(func); bb != null; bb = LLVMGetNextBasicBlock(bb)) {
                    blocks.add(bb);
                }
                blocks.sort(Comparator.comparingInt(blockMap::get));

                for (LLVMBasicBlockRef bb : blocks) {
                    // 获取基本块名称
                    String blockName = LLVMGetBasicBlockName(bb).getString();
                    if (blockName.equals("while_body")) {
                        int a = 1;
                    } else if (blockName.equals("mainEntry")){
                        int a = 2;
                    }
                    if (blockName.isEmpty()) blockName = "%tmp" + bb.address();
                    writer.write(blockName + ":\n");
                    for (LLVMValueRef inst = LLVMGetFirstInstruction(bb); inst != null; inst = LLVMGetNextInstruction(inst)) {
                        generateInst(inst, writer, allocator, stackSize);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateInst(LLVMValueRef inst, FileWriter writer, RegisterAllocator allocator, int stackSize) throws IOException {
        if (LLVMIsAAllocaInst(inst) != null) {
            String varName = LLVMGetValueName(inst).getString(); // 储存结果的变量名称
            if (varName.isEmpty()) varName = "%tmp" + inst.address();
            allocator.allocate(varName);
//                            int offset = allocator.getVarInStack(varName);
//                            writer.write("    .inst " + offset + "\n");


        } else if (LLVMIsAStoreInst(inst) != null) {
            LLVMValueRef val = LLVMGetOperand(inst, 0);
            LLVMValueRef ptr = LLVMGetOperand(inst, 1);
            String valName = getOperandString(val);
            String ptrName = getOperandString(ptr);
            if (LLVMIsAGlobalVariable(val) != null) {
                // 分配一个临时寄存器，存放全局变量的值
                if (LLVMIsAGlobalVariable(ptr) != null) {
                    String tempVarReg = allocator.allocateTempVar();
                    writer.write("    la " + tempVarReg + ", " + ptrName + "\n");
                    writer.write("    sw " + valName + ", 0(" + tempVarReg + ")" + "\n");
                    allocator.freeTempVarReg(tempVarReg);
                } else {
                    // 如果ptr在寄存器中，则直接存储，否则从栈中加载
                    String ptrReg = allocator.getVarInReg(ptrName);
                    writer.write("    mv " + ptrReg + ", " + valName + "\n");
                }
            }

            if (LLVMIsAConstantInt(val) != null) { // 立即数
                if (LLVMIsAGlobalVariable(ptr) != null) {
                    String tempVarReg = allocator.allocateTempVar();
                    writer.write("    li " + tempVarReg + ", " + LLVMConstIntGetSExtValue(val) + "\n");
                    String tempVarReg1 = allocator.allocateTempVar();
                    writer.write("    la " + tempVarReg1 + ", " + ptrName + "\n");
                    writer.write("    sw " + tempVarReg + ", 0(" + tempVarReg1 + ")" + "\n");
                    allocator.freeTempVarReg(tempVarReg1);
                    allocator.freeTempVarReg(tempVarReg); // 释放临时寄存器
                } else {
                    // 如果ptr在寄存器中，则直接存储，否则从栈中加载
                    String ptrReg = allocator.getVarInReg(ptrName);
                    if (ptrReg != null) {
                        writer.write("    li " + ptrReg + ", " + LLVMConstIntGetSExtValue(val) + "\n");
                    } else {
                        int offset = allocator.getVarInStack(ptrName);
                        String tempVarReg = allocator.allocateTempVar();
                        writer.write("    li " + tempVarReg + ", " + LLVMConstIntGetSExtValue(val) + "\n");
                        writer.write("    sw " + tempVarReg + ", " + offset + "(sp)\n");
                        allocator.freeTempVarReg(tempVarReg); // 释放临时寄存器
                    }

                }


            } else {
                String reg = allocator.getVarInReg(valName);
                if (reg != null) {
                    if (LLVMIsAGlobalVariable(ptr) != null) {
                        String tempVarReg = allocator.allocateTempVar();
                        writer.write("    la " + tempVarReg + ", " + ptrName + "\n");
                        writer.write("    sw " + reg + ", 0(" + tempVarReg + ")" + "\n");
                        allocator.freeTempVarReg(tempVarReg);
                    } else {
                        // 如果ptr在寄存器中，则直接存储，否则从栈中加载
                        String ptrReg = allocator.getVarInReg(ptrName);
                        if (ptrReg != null) {
                            writer.write("    mv " + ptrReg + ", " + reg + "\n");
                        } else {
                            int offset = allocator.getVarInStack(ptrName);
                            writer.write("    sw " + reg + ", " + offset + "(sp)\n");
                        }
                    }
                } else {
                    int regOffset = allocator.getVarInStack(valName);
                    String tempValReg = allocator.allocateTempVar();
                    writer.write("    lw " + tempValReg + ", " + regOffset + "(sp)\n");
                    if (tempValReg == null) {
                        int a = 1;
                    }
                    if (LLVMIsAGlobalVariable(ptr) != null) {
                        String tempVarReg = allocator.allocateTempVar();
                        writer.write("    la " + tempVarReg + ", " + ptrName + "\n");
                        writer.write("    sw " + tempValReg + ", 0(" + tempVarReg + ")" + "\n");
                        allocator.freeTempVarReg(tempVarReg);
                    } else {
                        // 如果ptr在寄存器中，则直接存储，否则从栈中加载
                        String ptrReg = allocator.getVarInReg(ptrName);
                        if (ptrReg != null) {
                            writer.write("    mv " + ptrReg + ", " + tempValReg + "\n");
                        } else {
                            int offset = allocator.getVarInStack(ptrName);
                            writer.write("    sw " + tempValReg + ", " + offset + "(sp)\n");
                        }
                    }
                    allocator.freeTempVarReg(tempValReg); // 释放临时寄存器
                }


            }
        } else if (LLVMIsALoadInst(inst) != null) {
            BytePointer dst = LLVMGetValueName(inst);
            LLVMValueRef ptr = LLVMGetOperand(inst, 0);
            String dstName = dst.getString();
            String ptrName = getOperandString(ptr);
            if (Objects.equals(ptrName, "x")) {
                int a = 1;
            }
            allocator.allocate(dstName); // 为目标变量分配栈空间或者寄存器
            String dstReg = allocator.getVarInReg(dstName);
            boolean dstInStack = false;
            if (dstReg == null) { // 未分配到寄存器
                dstInStack = true;
                int regOffset = allocator.getVarInStack(dstName);
                dstReg = allocator.allocateTempVar();
                writer.write("    lw " + dstReg + ", " + regOffset + "(sp)\n");
                if (dstReg == null) {
                    int a = 1;
                }
            }
            if (LLVMIsAGlobalVariable(ptr) != null) {
                String tempVarReg = allocator.allocateTempVar();
                writer.write("    la " + tempVarReg + ", " + LLVMGetValueName(ptr).getString() + "\n");
                writer.write("    lw " + dstReg + ", 0(" + tempVarReg + ")" + "\n");
                if (dstReg == null) {
                    int a = 1;
                }
                allocator.freeTempVarReg(tempVarReg);
            } else {
                // 如果ptr在寄存器中，则直接存储，否则从栈中加载
                String ptrReg = allocator.getVarInReg(ptrName);
                if (ptrReg != null) {
                    writer.write("    mv " + dstReg + ", " + ptrReg + "\n");
                } else {
                    int offset = allocator.getVarInStack(ptrName);
                    writer.write("    lw " + dstReg + ", " + offset + "(sp)\n");
                    if (dstReg == null) {
                        int a = 1;
                    }
                }
            }
            if (dstInStack) {
                allocator.freeTempVarReg(dstReg); // 释放临时寄存器
            }
        } else if (LLVMIsAReturnInst(inst) != null) {
            LLVMValueRef retVal = LLVMGetOperand(inst, 0);
            if (!retVal.isNull()) {
                String src;
                if (LLVMIsAConstantInt(retVal) != null) {
                    src = String.valueOf(LLVMConstIntGetSExtValue(retVal));
                    writer.write("    li a0, " + src + "\n");
                } else {
                    src = allocator.getVarInReg(getOperandString(retVal));
                    writer.write("    mv a0, " + src + "\n");
                }

            }
            writer.write("    addi sp, sp, " + stackSize + "\n");
            writer.write("    li a7, 93\n    ecall\n");
        } else if (LLVMIsABinaryOperator(inst) != null) {
            if (LLVMIsABinaryOperator(inst) != null) {
                int opcode = LLVMGetInstructionOpcode(inst);
                String op = null;
                switch (opcode) {
                    case LLVMAdd:
                        op = "add";
                        break;
                    case LLVMSub:
                        op = "sub";
                        break;
                    case LLVMMul:
                        op = "mul";
                        break;
                    case LLVMSDiv:
                        op = "div";
                        break;
                    case LLVMSRem:
                        op = "rem";
                        break;
                    case LLVMAnd:
                        op = "and";
                        break;
                    case LLVMOr:
                        op = "or";
                        break;
                    case LLVMXor:
                        op = "xor";
                        break;
                    default:
                        return;
                }

                String lhs = getOperandString(LLVMGetOperand(inst, 0));
                String rhs = getOperandString(LLVMGetOperand(inst, 1));
                String dst = LLVMGetValueName(inst).getString();
                if (dst.isEmpty()) dst = "%tmp" + inst.address();

                // 为目标变量分配栈空间
                allocator.allocate(dst);

                String dstReg = allocator.getVarInReg(dst);

                // 如果左、右操作数不为立即数，则需要分配寄存器
                String regLhs;
                if (LLVMIsAConstantInt(LLVMGetOperand(inst, 0)) != null) { // 立即数
                    regLhs = allocator.allocateTempVar();  // 临时寄存器
                    writer.write("    li " + regLhs + ", " + lhs + "\n");
                } else {
                    regLhs = allocator.getVarInReg(lhs);
                }

                String regRhs;
                if (LLVMIsAConstantInt(LLVMGetOperand(inst, 1)) != null) { // 立即数
                    regRhs = allocator.allocateTempVar();  // 临时寄存器
                    writer.write("    li " + regRhs + ", " + rhs + "\n");
                } else {
                    regRhs = allocator.getVarInReg(rhs);
                }


                if (dstReg != null) {
                    // 执行二元运算并保存结果
                    writer.write("    " + op + " " + dstReg + ", " + regLhs + ", " + regRhs + "\n");
                } else {
                    // 如果目标变量在栈中，则需要分配一个临时寄存器
                    String tempVarReg = allocator.allocateTempVar();
                    writer.write("    " + op + " " + tempVarReg + ", " + regLhs + ", " + regRhs + "\n");
                    int offset = allocator.getVarInStack(dst);
                    writer.write("    sw " + tempVarReg + ", " + offset + "(sp)\n");
                    allocator.freeTempVarReg(tempVarReg); // 释放临时寄存器
                }


                if (LLVMIsAConstantInt(LLVMGetOperand(inst, 0)) != null) { // 立即数
                    allocator.freeTempVarReg(regLhs);
                }

                if (LLVMIsAConstantInt(LLVMGetOperand(inst, 1)) != null) { // 立即数
                    allocator.freeTempVarReg(regRhs);
                }
            }
        } else if (LLVMIsABranchInst(inst) != null) {
            int numOperands = LLVMGetNumOperands(inst);
            if (numOperands == 1) { // 无条件跳转
                String targetBlockName = LLVMGetValueName(LLVMGetOperand(inst, 0)).getString();
                writer.write("    j " + targetBlockName + "\n");
            } else if (numOperands == 3) { // 条件跳转
                LLVMValueRef condition = LLVMGetOperand(inst, 0);
                String trueBlockName = LLVMGetValueName(LLVMGetOperand(inst, 1)).getString();
                String falseBlockName = LLVMGetValueName(LLVMGetOperand(inst, 2)).getString();
                String conditionName = getOperandString(condition);

                String conditionReg = allocator.getVarInReg(conditionName);
                if (conditionReg == null) {
                    int offset = allocator.getVarInStack(conditionName);
                    conditionReg = allocator.allocateTempVar();
                    writer.write("    lw " + conditionReg + ", " + offset + "(sp)\n");
                    writer.write("    bnez " + conditionReg + ", " + falseBlockName + "\n");
                    writer.write("    j " + trueBlockName + "\n");
                    if (conditionReg == null) {
                        int a = 1;
                    }
                    allocator.freeTempVarReg(conditionReg);
                } else {
                    writer.write("    bnez " + conditionReg + ", " + falseBlockName + "\n");
                    writer.write("    j " + trueBlockName + "\n");
                }

            }
        } else if (LLVMIsAICmpInst(inst) != null) {
            // 处理比较指令
            int cmpOpcode = LLVMGetICmpPredicate(inst);
            String dstName = LLVMGetValueName(inst).getString();
            if (dstName.isEmpty()) dstName = "%tmp" + inst.address();
            allocator.allocate(dstName); // 为目标变量分配栈空间或者寄存器
            String lhs = getOperandString(LLVMGetOperand(inst, 0));
            String rhs = getOperandString(LLVMGetOperand(inst, 1));
            String LhsReg = allocator.getVarInReg(lhs);
            String RhsReg = allocator.getVarInReg(rhs);
            boolean lshInStack = false;
            boolean rhsInStack = false;
            boolean lshImm = false;
            boolean rhsImm = false;
            if (LhsReg == null) {
                if (LLVMIsAConstantInt(LLVMGetOperand(inst, 0)) != null) {
                    lshImm = true;
                    LhsReg = allocator.allocateTempVar();
                    writer.write("    li " + LhsReg + ", " + LLVMConstIntGetSExtValue(LLVMGetOperand(inst, 0)) + "\n");
                } else {
                    int offset = allocator.getVarInStack(lhs);
                    LhsReg = allocator.allocateTempVar();
                    writer.write("    lw " + LhsReg + ", " + offset + "(sp)\n");
                    if (LhsReg == null) {
                        int a = 1;
                    }
                    lshInStack = true;
                }
            }
            if (RhsReg == null) {
                if (LLVMIsAConstantInt(LLVMGetOperand(inst, 1)) != null) {
                    rhsImm = true;
                    RhsReg = allocator.allocateTempVar();
                    writer.write("    li " + RhsReg + ", " + LLVMConstIntGetSExtValue(LLVMGetOperand(inst, 1)) + "\n");
                } else {
                    int offset = allocator.getVarInStack(rhs);
                    RhsReg = allocator.allocateTempVar();
                    writer.write("    lw " + RhsReg + ", " + offset + "(sp)\n");
                    if (RhsReg == null) {
                        int a = 1;
                    }
                    rhsInStack = true;
                }
            }
            String dstReg = allocator.getVarInReg(dstName);

            switch (cmpOpcode) {
                case LLVMIntEQ:
                    writer.write("    xor " + dstReg + ", " + LhsReg + ", " + RhsReg + "\n");
                    writer.write("    seqz " + dstReg + ", " + dstReg + "\n");
                    break;

                case LLVMIntNE:// xor + snez 实现判断是否不相等
                    writer.write("    xor " + dstReg + ", " + LhsReg + ", " + RhsReg + "\n");
                    writer.write("    snez " + dstReg + ", " + dstReg + "\n");
                    break;

                case LLVMIntSGT: // 大于
                    writer.write("    slt " + dstReg + ", " + RhsReg + ", " + LhsReg + "\n");
                    break;

                case LLVMIntSGE: // 大于等于,将小于的结果取反
                    writer.write("   slt " + dstReg + ", " + LhsReg + ", " + RhsReg + "\n");
                    writer.write("    xori " + dstReg + ", " + dstReg + ", 1\n");

                    break;
                case LLVMIntSLT: // 小于
                    writer.write("    slt " + dstReg + ", " + LhsReg + ", " + RhsReg + "\n");

                    break;
                case LLVMIntSLE: // 小于等于,将大于的结果取反
                    writer.write("    slt " + dstReg + ", " + RhsReg + ", " + LhsReg + "\n");
                    writer.write("    xori " + dstReg + ", " + dstReg + ", 1\n");
                    break;
                default:
                    return;
            }
            if (lshInStack || lshImm) {
                allocator.freeTempVarReg(LhsReg);
            }
            if (rhsInStack || rhsImm) {
                allocator.freeTempVarReg(RhsReg);
            }
        } else if (LLVMIsAZExtInst(inst) != null) {
            // 处理类型转换指令
            LLVMValueRef src = LLVMGetOperand(inst, 0);
            String srcName = getOperandString(src);
            String dstName = LLVMGetValueName(inst).getString();
            if (dstName.isEmpty()) dstName = "%tmp" + inst.address();
            allocator.allocate(dstName); // 为目标变量分配栈空间或者寄存器
            String dstReg = allocator.getVarInReg(dstName);
            String srcReg = allocator.getVarInReg(srcName);
            if (srcReg != null) {
                // 如果源操作数在寄存器中，则直接存储，否则从栈中加载
                writer.write("    mv " + dstReg + ", " + srcReg + "\n");
            } else {
                int offset = allocator.getVarInStack(srcName);
                writer.write("    lw " + dstReg + ", " + offset + "(sp)\n");
                if (dstReg == null) {
                    int a = 1;
                }
            }
        }
    }

    private String getOperandString(LLVMValueRef operand) {
        if (LLVMIsAConstantInt(operand) != null) {
            return String.valueOf(LLVMConstIntGetSExtValue(operand));
        }

        String name = LLVMGetValueName(operand).getString();
        if (name.isEmpty()) name = "%tmp" + operand.address();


        return name;
    }
}





