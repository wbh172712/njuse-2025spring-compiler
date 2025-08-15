import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.HashMap;
import java.util.Map;

public class IrSymbolTable {
    // 变量表
    private final Map<String, LLVMValueRef> varTable = new HashMap<>();

    // 函数表
    private final Map<String, LLVMValueRef> funcTable = new HashMap<>();

    // 上一级作用域
    private IrSymbolTable parent = null;


    public void addVar(String name, LLVMValueRef value) {
        varTable.put(name, value);
    }

    public void addFunc(String name, LLVMValueRef value) {
        funcTable.put(name, value);
    }

    // 在当前作用域查找变量
    public LLVMValueRef getVar(String name) {
        return varTable.get(name);
    }
    // 在当前作用域查找函数
    public LLVMValueRef getFunc(String name) {
        return funcTable.get(name);
    }

    // 在所有作用域查找变量
    public LLVMValueRef getVarInAll(String name) {
        LLVMValueRef value = varTable.get(name);
        if (value != null) {
            return value;
        }
        if (parent != null) {
            return parent.getVarInAll(name);
        }
        return null;
    }
    // 在所有作用域查找函数
    public LLVMValueRef getFuncInAll(String name) {
        LLVMValueRef value = funcTable.get(name);
        if (value != null) {
            return value;
        }
        if (parent != null) {
            return parent.getFuncInAll(name);
        }
        return null;
    }

    // 设置父作用域
    public void setParent(IrSymbolTable parent) {
        this.parent = parent;
    }
    // 获取父作用域
    public IrSymbolTable getParent() {
        return parent;
    }

}
