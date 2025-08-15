import org.bytedeco.llvm.LLVM.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.bytedeco.llvm.global.LLVM.*;

public class MyIrRunner {
    LLVMModuleRef module;
    Map<String, Integer> varNameToValue;
    Map<String, LLVMValueRef> functionNameToFunction;
    Map<String, LLVMBasicBlockRef> blockNameToBlockRef;
    int returnValue;
    boolean hasReturned;
    // 定义正则表达式
    String storePattern = "store i32 (([@%]\\w+)|-?\\d+), i32\\* ([@%]\\w+), align \\d+";
    String allocaPattern = "([@%]\\w+) = alloca i32, align \\d+";
    String loadPattern = "([@%]\\w+) = load i32, i32\\* ([@%]\\w+), align \\d+";
    String brPattern1 = "br label ([@%]\\w+)";
    String brPattern2 = "br i1 ([@%]\\w+), label ([@%]\\w+), label ([@%]\\w+)";
    String retPattern = "ret i32 (([@%]\\w+)|-?\\d+)";
    String callPattern = "([@%]\\w+) = call i32 @\\w+\\(([@%]\\w+)\\)";
    String icmpPattern = "([@%]\\w+) = icmp (\\w+) i32 (([@%]\\w+)|-?\\d+), (([@%]\\w+)|-?\\d+)";
    String zextPattern = "([@%]\\w+) = zext i1 ([@%]\\w+) to i32";

    String addPattern = "([@%]\\w+) = add i32 (([@%]\\w+)|-?\\d+), (([@%]\\w+)|-?\\d+)";
    String subPattern = "([@%]\\w+) = sub i32 (([@%]\\w+)|-?\\d+), (([@%]\\w+)|-?\\d+)";
    String mulPattern = "([@%]\\w+) = mul i32 (([@%]\\w+)|-?\\d+), (([@%]\\w+)|-?\\d+)";
    String divPattern = "([@%]\\w+) = sdiv i32 (([@%]\\w+)|-?\\d+), (([@%]\\w+)|-?\\d+)";

    Pattern storeRegex = Pattern.compile(storePattern);
    Pattern allocaRegex = Pattern.compile(allocaPattern);
    Pattern loadRegex = Pattern.compile(loadPattern);
    Pattern brRegex1 = Pattern.compile(brPattern1);
    Pattern brRegex2 = Pattern.compile(brPattern2);
    Pattern retRegex = Pattern.compile(retPattern);
    Pattern callRegex = Pattern.compile(callPattern);
    Pattern icmpRegex = Pattern.compile(icmpPattern);
    Pattern zextRegex = Pattern.compile(zextPattern);
    Pattern addRegex = Pattern.compile(addPattern);
    Pattern subRegex = Pattern.compile(subPattern);
    Pattern mulRegex = Pattern.compile(mulPattern);
    Pattern divRegex = Pattern.compile(divPattern);

    List<LLVMValueRef> instructions = new ArrayList<>(); // 记录指令运行顺序


    public MyIrRunner(LLVMModuleRef module) {
        this.module = module;
        this.varNameToValue = new java.util.HashMap<>();
        this.functionNameToFunction = new java.util.HashMap<>();
        this.blockNameToBlockRef = new java.util.HashMap<>();
        this.returnValue = 0;
        this.hasReturned = false;
    }

    public void runModule() {
        // 扫描module中的所有基础块
        LLVMValueRef function = LLVMGetFirstFunction(module);
        while(function != null) {
            String functionName = LLVMGetValueName(function).getString();
            functionNameToFunction.put(functionName, function);
            LLVMBasicBlockRef block = LLVMGetFirstBasicBlock(function);
            while (block != null) {
                String blockName = LLVMGetBasicBlockName(block).getString();
                blockNameToBlockRef.put(blockName, block);
                block = LLVMGetNextBasicBlock(block);
            }
            // 扫描函数中的所有指令
            function = LLVMGetNextFunction(function);
        }

        // 将全局变量添加到varNameToValue中
        LLVMValueRef globalVar = LLVMGetFirstGlobal(module);
        while (globalVar != null) {
            String varName = LLVMGetValueName(globalVar).getString();
            Integer value = Math.toIntExact(LLVMConstIntGetSExtValue(LLVMGetInitializer(globalVar)));
            varNameToValue.put("@" + varName, value);
            globalVar = LLVMGetNextGlobal(globalVar);
        }

        // 执行main函数
        LLVMValueRef mainFunction = functionNameToFunction.get("main");
        if (mainFunction != null) {
            LLVMBasicBlockRef firstBlock = LLVMGetFirstBasicBlock(mainFunction);
            if (firstBlock != null) {
                runBlock(firstBlock);
            }
        }

    }


    public void runBlock(LLVMBasicBlockRef block) {
        if (hasReturned) {
            // 如果已经返回，直接退出
            return;
        }
        // 从block中逐行读取指令
        LLVMValueRef instruction = LLVMGetFirstInstruction(block);
        for (; instruction != null; instruction = LLVMGetNextInstruction(instruction)) {
            String instructionStr = LLVMPrintValueToString(instruction).getString();
            // 忽略instructinoStr中开头的空格
            instructionStr = instructionStr.trim();
            // 匹配指令
            if (storeRegex.matcher(instructionStr).find()) {
                // 处理store指令
                handleStore(instructionStr);
            } else if (allocaRegex.matcher(instructionStr).find()) {
                // 处理alloca指令
                handleAlloca(instructionStr);
            } else if (loadRegex.matcher(instructionStr).find()) {
                // 处理load指令
                handleLoad(instructionStr);
            } else if (brRegex1.matcher(instructionStr).find()) {
                // 处理br指令
                handleBr1(instructionStr);
            } else if (brRegex2.matcher(instructionStr).find()) {
                // 处理br指令
                handleBr2(instructionStr);
            } else if (retRegex.matcher(instructionStr).find()) {
                // 处理ret指令
                handleRet(instructionStr);
                if (hasReturned) {
                    // 如果有返回值，退出当前函数
                    return;
                }
            } else if (callRegex.matcher(instructionStr).find()) {
                // 处理call指令
                handleCall(instructionStr);
            } else if (zextRegex.matcher(instructionStr).find()) {
                // 处理zext指令
                handleZext(instructionStr);
            } else if (icmpRegex.matcher(instructionStr).find()) {
                // 处理icmp指令
                handleIcmp(instructionStr);
            }  else if (addRegex.matcher(instructionStr).find()) {
                // 处理add指令
                handleAdd(instructionStr);
            } else if (subRegex.matcher(instructionStr).find()) {
                // 处理sub指令
                handleSub(instructionStr);
            } else if (mulRegex.matcher(instructionStr).find()) {
                // 处理mul指令
                handleMul(instructionStr);
            } else if (divRegex.matcher(instructionStr).find()) {
                // 处理div指令
                handleDiv(instructionStr);
            } else {
                System.out.println("Unknown instruction: " + instructionStr);
            }

            this.instructions.add(instruction);
        }

    }

    private void handleZext(String instructionStr) {
        // %\\w+ = zext i1 (%\\w+) to i32
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String zextVarName = parts[4];

        Integer value = varNameToValue.get(zextVarName);
        if (value != null) {
            varNameToValue.put(varName, value);
        }
    }

    private void handleDiv(String instructionStr) {
        // %\\w+ = sdiv i32 (%\\w+)|\\d+, (%\\w+)|\\d+
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String leftVarName = parts[4].split(",")[0];
        String rightVarName = parts[5];

        Integer leftValue = varNameToValue.get(leftVarName);
        Integer rightValue = varNameToValue.get(rightVarName);
        // 如果左操作数是立即数
        if (leftVarName.matches("-?\\d+")) {
            leftValue = Integer.parseInt(leftVarName);
        }
        // 如果右操作数是立即数
        if (rightVarName.matches("-?\\d+")) {
            rightValue = Integer.parseInt(rightVarName);
        }
        if (leftValue != null && rightValue != null) {
            varNameToValue.put(varName, leftValue / rightValue);
        }
    }

    private void handleMul(String instructionStr) {
        // %\\w+ = mul nsw i32 (%\\w+)|\\d+, (%\\w+)|\\d+
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String leftVarName = parts[4].split(",")[0];
        String rightVarName = parts[5];

        Integer leftValue = varNameToValue.get(leftVarName);
        Integer rightValue = varNameToValue.get(rightVarName);
        // 如果左操作数是立即数
        if (leftVarName.matches("-?\\d+")) {
            leftValue = Integer.parseInt(leftVarName);
        }
        // 如果右操作数是立即数
        if (rightVarName.matches("-?\\d+")) {
            rightValue = Integer.parseInt(rightVarName);
        }
        if (leftValue != null && rightValue != null) {
            varNameToValue.put(varName, leftValue * rightValue);
        }
    }

    private void handleSub(String instructionStr) {
        // %\\w+ = sub nsw i32 (%\\w+)|\\d+, (%\\w+)|\\d+
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String leftVarName = parts[4].split(",")[0];
        String rightVarName = parts[5];

        Integer leftValue = varNameToValue.get(leftVarName);
        Integer rightValue = varNameToValue.get(rightVarName);
        // 如果左操作数是立即数
        if (leftVarName.matches("-?\\d+")) {
            leftValue = Integer.parseInt(leftVarName);
        }
        // 如果右操作数是立即数
        if (rightVarName.matches("-?\\d+")) {
            rightValue = Integer.parseInt(rightVarName);
        }
        if (leftValue != null && rightValue != null) {
            varNameToValue.put(varName, leftValue - rightValue);
        }
    }

    private void handleAdd(String instructionStr) {
        // %\\w+ = add nsw i32 (%\\w+)|\\d+, (%\\w+)|\\d+
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String leftVarName = parts[4].split(",")[0];
        String rightVarName = parts[5];

        Integer leftValue = varNameToValue.get(leftVarName);
        Integer rightValue = varNameToValue.get(rightVarName);
        // 如果左操作数是立即数
        if (leftVarName.matches("-?\\d+")) {
            leftValue = Integer.parseInt(leftVarName);
        }
        // 如果右操作数是立即数
        if (rightVarName.matches("-?\\d+")) {
            rightValue = Integer.parseInt(rightVarName);
        }
        if (leftValue != null && rightValue != null) {
            varNameToValue.put(varName, leftValue + rightValue);
        }
    }

    private void handleIcmp(String instructionStr) {
        // %\\w+ = icmp (\\w+) i32 (%\\w+)|\\d+, (%\\w+)|\\d+
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String cmpType = parts[3];
        String leftVarName = parts[5].split(",")[0];
        String rightVarName = parts[6];

        Integer leftValue = varNameToValue.get(leftVarName);
        Integer rightValue = varNameToValue.get(rightVarName);
        // 如果左操作数是立即数
        if (leftVarName.matches("-?\\d+")) {
            leftValue = Integer.parseInt(leftVarName);
        }
        // 如果右操作数是立即数
        if (rightVarName.matches("-?\\d+")) {
            rightValue = Integer.parseInt(rightVarName);
        }
        if (leftValue != null && rightValue != null) {
            int result;
            switch (cmpType) {
                case "eq":
                    result = leftValue.equals(rightValue) ? 1 : 0;
                    break;
                case "ne":
                    result = !leftValue.equals(rightValue) ? 1 : 0;
                    break;
                case "lt":
                case "slt":
                    result = leftValue < rightValue ? 1 : 0;
                    break;
                case "gt":
                case "sgt":
                    result = leftValue > rightValue ? 1 : 0;
                    break;
                case "le":
                case "sle":
                    result = leftValue <= rightValue ? 1 : 0;
                    break;
                case "ge":
                case "uge":
                    result = leftValue >= rightValue ? 1 : 0;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown comparison type: " + cmpType);
            }
            varNameToValue.put(varName, result);
        }
    }

    private void handleCall(String instructionStr) {
        // %\\w+ = call i32 @\\w+\\((%\\w+)\\)
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String functionName = parts[2].split("\\(")[0];
        String argVarName = parts[3].split("\\)")[0];

        LLVMValueRef functionRef = functionNameToFunction.get(functionName);
        Integer argValue = varNameToValue.get(argVarName);
        if (functionRef != null && argValue != null) {
            // 调用函数
            runFunction(functionRef, argValue);
        }
    }

    private void runFunction(LLVMValueRef functionRef, Integer argValue) {
        // 执行函数, 暂时假设函数没有参数
        if (hasReturned) {
            // 如果已经返回，直接退出
            return;
        }
        LLVMBasicBlockRef firstBlock = LLVMGetFirstBasicBlock(functionRef);
        if (firstBlock != null) {
            // 执行第一个基本块
            runBlock(firstBlock);
        }
    }


    private void handleRet(String instructionStr) {
        // ret i32 (([@%]//w+)|\\d+)
        String[] parts = instructionStr.split(" ");
        String returnVarStr = parts[2];
        if (returnVarStr.matches("-?\\d+")) {
            returnValue = Integer.parseInt(returnVarStr);
        } else {
            returnValue = varNameToValue.get(returnVarStr);
        }
        hasReturned = true;
    }

    private void handleBr2(String instructionStr) {
        // br i1 (%\\w+), label (%\\w+), label (%\\w+)
        if (hasReturned) {
            // 如果已经返回，直接退出
            return;
        }
        String[] parts = instructionStr.split(", ");
        String conditionVarName = parts[0].split(" ")[2];
        String trueBlockName = parts[1].split(" ")[1].substring(1);
        String falseBlockName = parts[2].split(" ")[1].substring(1);

        Integer conditionValue = varNameToValue.get(conditionVarName);
        if (conditionValue != null && conditionValue != 0) {
            LLVMBasicBlockRef trueBlockRef = blockNameToBlockRef.get(trueBlockName);
            if (trueBlockRef != null) {
                runBlock(trueBlockRef);
            }
        } else {
            LLVMBasicBlockRef falseBlockRef = blockNameToBlockRef.get(falseBlockName);
            if (falseBlockRef != null) {
                runBlock(falseBlockRef);
            }
        }
    }

    private void handleBr1(String instructionStr) {
        // br label (%\\w+)
        if (hasReturned) {
            // 如果已经返回，直接退出
            return;
        }
        String[] parts = instructionStr.split(" ");
        String blockName = parts[2].substring(1);
        LLVMBasicBlockRef blockRef = blockNameToBlockRef.get(blockName);
        if (blockRef != null) {
            runBlock(blockRef);
        }
    }

    private void handleLoad(String instructionStr) {
        // %\\w+ = load i32, i32\\* (%\\w+), align \\d+
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        String loadVarName = parts[5].split(",")[0];
        Integer value = varNameToValue.get(loadVarName);
        if (value != null) {
            varNameToValue.put(varName, value);
        }
    }

    private void handleAlloca(String instructionStr) {
        // %\\w+ = alloca i32, align \\d+
        String[] parts = instructionStr.split(" ");
        String varName = parts[0];
        varNameToValue.put(varName, 0);
    }

    private void handleStore(String instructionStr) {
        // store i32 (([@%]\w+)|\d+), i32\* ([@%]\w+), align \d+
        String[] parts = instructionStr.split(" ");
        String storeVarName = parts[2].split(",")[0];
        String targetVarName = parts[4].split(",")[0];
        Integer value = varNameToValue.get(storeVarName);
        // 如果storeVarName是立即数
        if (storeVarName.matches("-?\\d+")) {
            value = Integer.parseInt(storeVarName);
        }
        if (value != null) {
            varNameToValue.put(targetVarName, value);
        } else {
//            System.err.println("Store value is null: " + storeVarName);
        }
    }

    public int run() {
        // 执行模块
        runModule();
        // 打印返回值
        return this.returnValue;
    }
}
