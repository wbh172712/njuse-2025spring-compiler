package type;

public abstract class Type {
    public String getTypeName() {
        return null;
    }

    @Override
    public String toString() {
        return getTypeName();
    }

    public abstract boolean typeMatch(Type other);
}

