import java.util.Objects;
import org.bytedeco.llvm.LLVM.*;
import java.util.*;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.annotation.*;

import static org.bytedeco.llvm.global.LLVM.*;


enum ConstStatus {
    UNDEF, CONST, NAC
}

class ConstValue {
    ConstStatus status;
    int constVal;

    public ConstValue(ConstStatus status, int constVal) {
        this.status = status;
        this.constVal = constVal;
    }

    public static final ConstValue UNDEF = new ConstValue(ConstStatus.UNDEF, 0);
    public static final ConstValue NAC = new ConstValue(ConstStatus.NAC, 0);

    public static ConstValue meet(ConstValue a, ConstValue b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.status == ConstStatus.UNDEF) return b;
        if (b.status == ConstStatus.UNDEF) return a;
        if (a.status == ConstStatus.CONST && b.status == ConstStatus.CONST && a.constVal == b.constVal) return b;
        return NAC;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConstValue)) return false;
        ConstValue other = (ConstValue) o;
        return this.status == other.status &&
                (this.status != ConstStatus.CONST || this.constVal == other.constVal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, constVal);
    }
}



