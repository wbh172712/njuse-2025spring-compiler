package type;

import java.util.List;

// 表示函数类型
public class FunctionType extends Type {
    private final Type returnType;  // 返回类型
    private final List<Type> paramsType; // 形参类型列表

    public FunctionType(Type returnType, List<Type> paramsType) {
        this.returnType = returnType;
        this.paramsType = paramsType;
    }

    public Type getReturnType() {
        return returnType;
    }

    public List<Type> getParamsType() {
        return paramsType;
    }

    @Override
    public String getTypeName() {
        return "function(" + paramsType.toString() + ") -> " + returnType.getTypeName();
    }

    @Override
    public boolean typeMatch(Type other) {
        return false;
    }

}
