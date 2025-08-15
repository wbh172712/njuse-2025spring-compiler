import java.util.List;

public class FunctionType extends Type {
    public Type returnType;
    public List<Type> paramTypes;

    public FunctionType(Type returnType, List<Type> paramTypes) {
        super(TypeEnum.FUNCTION_TYPE);
        this.returnType = returnType;
        this.paramTypes = paramTypes;
    }
}
