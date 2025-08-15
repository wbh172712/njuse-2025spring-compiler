public class Type {

    public enum TypeEnum {
        INT_TYPE,
        VOID_TYPE,
        BOOL_TYPE,
        FUNCTION_TYPE,
        ARRAY_TYPE,
    }
    TypeEnum type = null;

    public Type(TypeEnum type) {
        this.type = type;
    }
}

