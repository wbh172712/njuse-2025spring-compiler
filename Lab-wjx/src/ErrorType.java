public class ErrorType {
    private int no;
    private String msg;


    static ErrorType NODEF_VAR = new ErrorType(1, "Variable not defined");
    static ErrorType NODEF_FUNC = new ErrorType(2, "Function not defined");
    static ErrorType REDEF_VAR = new ErrorType(3, "Variable redefinition");
    static ErrorType REDEF_FUNC = new ErrorType(4, "Function redefinition");
    // 赋值号两边不匹配
    static ErrorType ASSIGN_TYPE_MISMATCH = new ErrorType(5, "Assignment type mismatch");
    // 运算符需求类型与提供类型不匹配
    static ErrorType OPERAND_TYPE_MISMATCH = new ErrorType(6, "Operand type mismatch");
    // 返回值类型不匹配
    static ErrorType RETURN_TYPE_MISMATCH = new ErrorType(7, "Invalid return type");

    // 函数参数不适用
    static ErrorType FUNC_ARG_MISMATCH = new ErrorType(8, "Function argument mismatch");
    // 对非数组使用下标运算符
    static ErrorType INDEX_ON_NON_ARRAY = new ErrorType(9, "Index on non-array");
    // 对变量使用函数调用
    static ErrorType CALL_NON_FUNC = new ErrorType(10, "Call non-function");
    // 赋值号左侧非变量或数组元素
    static ErrorType ASSIGN_TO_NON_VAR = new ErrorType(11, "Assign to non-variable");

    public ErrorType(int i, String functionRedefinition) {
        this.no = i;
        this.msg = functionRedefinition;
    }
    public int getNo() {
        return no;
    }
    public String getMsg() {
        return msg;
    }
}
