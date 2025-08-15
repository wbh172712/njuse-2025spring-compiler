public class LatticeValue {

    public static final int UNDEF = 0;
    public static final int CONST = 1;
    public static final int NAC = 2;

    int type;

    int value;

    public LatticeValue(int type, int value) {
        this.type = type;
        this.value = value;
    }

    public static LatticeValue meet(LatticeValue a, LatticeValue b) {
        if (a.type == CONST && b.type == CONST && a.value == b.value) return a;
        if (a.type == UNDEF) return b;
        if (b.type == UNDEF) return a;
        return new LatticeValue(NAC, 0);
    }

}



