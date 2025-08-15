import type.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

class Scope {
    public Map<String, Type> symbolTable;  // 存储变量/函数名 -> 类型
    public Scope fatherScope;  // 指向上层作用域（如果为 null，则是全局作用域）

    public Scope(Scope fatherScope) {
        this.symbolTable = new LinkedHashMap<>();
        this.fatherScope = fatherScope;
    }

    // 查找符号（从当前作用域开始，逐层向上查找）
    public Type findInAllScope(String SymbolName) {
        if (symbolTable.containsKey(SymbolName)) {
            return symbolTable.get(SymbolName);
        }
        return (fatherScope != null) ? fatherScope.findInAllScope(SymbolName) : null;  // 递归向上查找
    }

    // 仅在当前作用域查找
    public Type findInCurrentScope(String symbolName) {
        return symbolTable.get(symbolName);
    }

    // 向当前作用域添加符号
    public void put(String symbolName, Type symbolType) {
        symbolTable.put(symbolName, symbolType);
    }

    // 获取父作用域
    public Scope getFatherScope() {
        return fatherScope;
    }
}
