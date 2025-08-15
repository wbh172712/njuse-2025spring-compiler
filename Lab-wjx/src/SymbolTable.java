import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    public SymbolTable parent;
    public Map<String, Type> symbols;

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
        this.symbols = new HashMap<>();
    }

    public SymbolTable() {
        this.parent = null;
        this.symbols = new HashMap<>();
    }

    public void addSymbol(String name, Type type) {
        symbols.put(name, type);
    }

    public Type getSymbol(String name) {
        Type type = symbols.get(name);
        if (type != null) {
            return type;
        }
        if (parent != null) {
            return parent.getSymbol(name);
        }
        return null;
    }

    public Type getSymbol(String name, Type.TypeEnum typeEnum) {
        for (Map.Entry<String, Type> entry : symbols.entrySet()) {
            if (entry.getKey().equals(name) && entry.getValue().type == typeEnum) {
                return entry.getValue();
            }
        }
        if (parent != null) {
            return parent.getSymbol(name, typeEnum);
        }
        return null;
    }

    public Type getSymbolInCurrentScope(String name) {
        return symbols.get(name);
    }
}
