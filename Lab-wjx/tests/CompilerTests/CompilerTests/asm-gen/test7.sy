int main() {
    // 基础算术指令测试
    int a = 15;         // li 指令测试
    int b = 3;          // 立即数加载
    int c = a + b;      // ADD (18)
    int d = a - b;      // SUB (12)
    int e = a * b;      // MUL (45)
    int f = a / b;      // DIV (5)
    int g = a % b;      // REM (0)

    // 条件分支测试
    int m = 0;
    if (a > b) {        // BGT 分支测试
        m = 1;          // 应执行此分支
    } else {
        m = -1;
    }

    // 循环控制测试
    int sum = 0;
    int n = 0;
    while (n < 5) {
      sum = sum + n;
      n = n + 1;
    }

    // 内存压力测试(无数组)
    int mem_test = 0;
    mem_test = c;       // 测试store指令
    int load_test = mem_test; // 测试load指令

    // 复合条件验证
    return (c - 18)    // 验证加法
         + (d - 12)    // 验证减法
         + (e - 45)    // 验证乘法
         + (sum - 10)  // 验证循环
         + (m - 1);    // 验证分支
}