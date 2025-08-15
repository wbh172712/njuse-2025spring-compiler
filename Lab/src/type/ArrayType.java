package type;

// 表示数组类型
public class ArrayType extends Type {
    public Type elementType;  // 数组元素类型

    public ArrayType(Type elementType) {
        this.elementType = elementType;
    }

    public Type getElementType() {
        return elementType;
    }

    @Override
    public String getTypeName() {
        return "array";
    }

    @Override
    public boolean typeMatch(Type other) {
        if (other instanceof ArrayType) {
            ArrayType otherArray = (ArrayType) other;
            return this.elementType.typeMatch(otherArray.elementType);
        }
        return false;
    }

}