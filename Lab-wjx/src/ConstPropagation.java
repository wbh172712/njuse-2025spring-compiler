import org.bytedeco.llvm.LLVM.*;


import java.util.HashMap;
import java.util.Map;

import static org.bytedeco.llvm.global.LLVM.*;


public class ConstPropagation {
    // 用于记录寄存器变量的常量值
    Map<String, Integer> constVars = new HashMap<>();
    // 用于记录内存地址（alloca）的最后存储的常量值
    Map<String, Integer> constMemory = new HashMap<>();
    // 记录变量定义指令（用于后续替换）
    Map<String, LLVMValueRef> valueMap = new HashMap<>();

    public void run(LLVMModuleRef module) {
        for (LLVMValueRef function = LLVMGetFirstFunction(module);
             function != null && !function.equals(LLVMGetLastFunction(module).equals(function));
             function = LLVMGetNextFunction(function)) {

            for (LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
                 block != null;
                 block = LLVMGetNextBasicBlock(block)) {

                LLVMValueRef instr = LLVMGetFirstInstruction(block);
                while (instr != null) {
                    int opcode = LLVMGetInstructionOpcode(instr);

                    switch (opcode) {
                        case LLVMAlloca:
                            // 记录 alloca 指令，变量名指向地址
                            valueMap.put(LLVMGetValueName(instr).getString(), instr);
                            break;

                        case LLVMStore: {
                            LLVMValueRef val = LLVMGetOperand(instr, 0);
                            LLVMValueRef ptr = LLVMGetOperand(instr, 1);
                            if (LLVMIsConstant(val) != 0) {
                                String ptrName = LLVMGetValueName(ptr).getString();
                                int constant = (int) LLVMConstIntGetZExtValue(val);
                                constMemory.put(ptrName, constant);
                            } else {
                                // 非常量写入，清除该地址
                                constMemory.remove(LLVMGetValueName(ptr).getString());
                            }
                            break;
                        }

                        case LLVMLoad: {
                            LLVMValueRef ptr = LLVMGetOperand(instr, 0);
                            String ptrName = LLVMGetValueName(ptr).getString();
                            if (constMemory.containsKey(ptrName)) {
                                int val = constMemory.get(ptrName);
                                // 替换当前load指令为常量
                                LLVMValueRef constVal = LLVMConstInt(LLVMInt32Type(), val, 0);
                                replaceAllUsesWith(instr, constVal);
                            }
                            break;
                        }

                        case LLVMAdd: {
                            LLVMValueRef op0 = LLVMGetOperand(instr, 0);
                            LLVMValueRef op1 = LLVMGetOperand(instr, 1);
                            if (LLVMIsConstant(op0) != 0 && LLVMIsConstant(op1) != 0) {
                                int val0 = (int) LLVMConstIntGetZExtValue(op0);
                                int val1 = (int) LLVMConstIntGetZExtValue(op1);
                                LLVMValueRef result = LLVMConstInt(LLVMInt32Type(), val0 + val1, 0);
                                replaceAllUsesWith(instr, result);
                            }
                            break;
                        }

                        default:
                            break;
                    }

                    instr = LLVMGetNextInstruction(instr);
                }
            }
        }
    }

    // 用 LLVM API 替换某指令为常量
    private void replaceAllUsesWith(LLVMValueRef oldVal, LLVMValueRef newVal) {
        LLVMReplaceAllUsesWith(oldVal, newVal);
        // 注意：还需要将旧指令从基本块中删除（可选）
        LLVMInstructionEraseFromParent(oldVal);
    }
}
