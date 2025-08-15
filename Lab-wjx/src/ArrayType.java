

public class ArrayType extends Type {
    public int elementSize;
    public Type elementtype;
    public int dimension;

    public ArrayType(Type elementtype, int elementSize) {
        super(TypeEnum.ARRAY_TYPE);
        this.elementSize = elementSize;
        this.elementtype = elementtype;
    }

    public ArrayType(Type elementtype, int elementSize, int dimension) {
        super(TypeEnum.ARRAY_TYPE);
        this.elementSize = elementSize;
        this.elementtype = elementtype;
        this.dimension = dimension;
    }


}

