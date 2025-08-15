package type;

// 表示int类型的单例类
public class IntType extends Type {
    private static final IntType instance = new IntType();

    public IntType() {}  // 私有构造函数，确保单例模式

    public static IntType getI32() {
        return instance;
    }

    @Override
    public String getTypeName() {
        return "int";
    }

    @Override
    public boolean typeMatch(Type other) {
        return other instanceof IntType;
    }
}
