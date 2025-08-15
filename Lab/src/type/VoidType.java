package type;

// 表示void类型的单例类
public class VoidType extends Type {
    private static final VoidType instance = new VoidType();

    public VoidType() {}

    public static VoidType getVoidType() {
        return instance;
    }

    @Override
    public String getTypeName() {
        return "void";
    }

    @Override
    public boolean typeMatch(Type other) {
        return other instanceof VoidType;
    }
}
