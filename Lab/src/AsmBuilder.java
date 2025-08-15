public class AsmBuilder {

    private StringBuilder buffer = new StringBuilder();

    public void addString(String str) {
        buffer.append(str);
    }

    // 添加文本段声明
    public void createTextSection() {
        buffer.append("  .text\n");
    }

    // 添加数据段声明
    public void createDataSection() {
        buffer.append("  .data\n");
    }

    // 声明全局符号
    public void globalSymbol(String name) {
        buffer.append("  .globl ").append(name).append("\n");
    }

    // 定义标签
    public void label(String name) {
        buffer.append(name).append(":\n");
    }

    public void addi(String dest, String src, int imm) {
        buffer.append("  addi ").append(dest).append(", ")
                .append(src).append(", ").append(imm).append("\n");
    }

    // 生成二元操作指令
    public void op2(String op, String dest, String src1, String src2) {
        buffer.append("  ").append(op).append(" ")
                .append(dest).append(", ")
                .append(src1).append(", ")
                .append(src2).append("\n");
    }

    // 生成一元操作指令
    public void op1(String op, String dest, String src) {
        buffer.append("  ").append(op).append(" ")
                .append(dest).append(", ")
                .append(src).append("\n");
    }

    // 生成无操作数指令
    public void op0(String op, String dest) {
        buffer.append("  ").append(op).append(" ")
                .append(dest).append("\n");
    }

    // 生成加载立即数指令
    public void li(String reg, int imm) {
        buffer.append("  li ").append(reg).append(", ").append(imm).append("\n");
    }

    // 生成存储指令
    public void sw(String src, String base, int offset) {
        buffer.append("  sw ").append(src).append(", ")
                .append(offset).append("(").append(base).append(")\n");
    }

    // 生成加载指令
    public void lw(String dest, String base, int offset) {
        buffer.append("  lw ").append(dest).append(", ")
                .append(offset).append("(").append(base).append(")\n");
    }

    // 生成系统调用
    public void ecall() {
        buffer.append("  ecall\n");
    }

    // 获取生成的汇编代码
    public String getAsmString() {
        return buffer.toString();
    }

}
