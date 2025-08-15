; ModuleID = 'my_module'
source_filename = "my_module"

@x = global i32 10
@z = global i32 0

define i32 @fib(i32 %x) {
fibEntry:
  %param0_addr = alloca i32, align 4
  store i32 %x, i32* %param0_addr, align 4
  %load_lval = load i32, i32* %param0_addr, align 4
  %cmp = icmp sle i32 %load_lval, 1
  %zext_to_i32 = zext i1 %cmp to i32
  %to_bool = icmp ne i32 %zext_to_i32, 0
  br i1 %to_bool, label %if.then, label %if.else

if.then:                                          ; preds = %fibEntry
  %load_lval1 = load i32, i32* %param0_addr, align 4
  ret i32 %load_lval1

if.else:                                          ; preds = %fibEntry
  %load_lval2 = load i32, i32* %param0_addr, align 4
  %sub = sub i32 %load_lval2, 1
  %fib = call i32 @fib(i32 %sub)
  %load_lval3 = load i32, i32* %param0_addr, align 4
  %sub4 = sub i32 %load_lval3, 2
  %fib5 = call i32 @fib(i32 %sub4)
  %add = add i32 %fib, %fib5
  ret i32 %add
}

define i32 @g(i32 %t, i32 %y) {
gEntry:
  %param0_addr = alloca i32, align 4
  store i32 %t, i32* %param0_addr, align 4
  %param1_addr = alloca i32, align 4
  store i32 %y, i32* %param1_addr, align 4
  %load_lval = load i32, i32* %param1_addr, align 4
  %add = add i32 %load_lval, 1
  store i32 %add, i32* %param1_addr, align 4
  %load_lval1 = load i32, i32* @z, align 4
  %add2 = add i32 %load_lval1, 1
  store i32 %add2, i32* @z, align 4
  %load_lval3 = load i32, i32* @x, align 4
  %add4 = add i32 %load_lval3, 1
  store i32 %add4, i32* @x, align 4
  %load_lval5 = load i32, i32* %param1_addr, align 4
  %add6 = add i32 %load_lval5, 1
  ret i32 %add6
}

define i32 @main() {
mainEntry:
  %fib = call i32 @fib(i32 7)
  store i32 %fib, i32* @z, align 4
  store i32 4, i32* @z, align 4
  %p = alloca i32, align 4
  store i32 5, i32* %p, align 4
  br label %while.cond

cur:                                              ; preds = %if.then, %while.cond
  %load_lval8 = load i32, i32* @x, align 4
  %add = add i32 %load_lval8, 1
  ret i32 %add

while.stmt:                                       ; preds = %while.cond
  br label %while.cond3

while.cond:                                       ; preds = %merge, %mainEntry
  br i1 true, label %while.stmt, label %cur

cur1:                                             ; preds = %while.cond3
  %load_lval4 = load i32, i32* @x, align 4
  %cmp5 = icmp sgt i32 %load_lval4, 10
  %zext_to_i326 = zext i1 %cmp5 to i32
  %to_bool7 = icmp ne i32 %zext_to_i326, 0
  br i1 %to_bool7, label %if.then, label %merge

while.stmt2:                                      ; preds = %while.cond3
  %g = call i32 @g(i32 10, i32 20)
  br label %while.cond3

while.cond3:                                      ; preds = %while.stmt2, %while.stmt
  %load_lval = load i32, i32* @z, align 4
  %cmp = icmp slt i32 %load_lval, 102
  %zext_to_i32 = zext i1 %cmp to i32
  %to_bool = icmp ne i32 %zext_to_i32, 0
  br i1 %to_bool, label %while.stmt2, label %cur1

merge:                                            ; preds = %cur1
  br label %while.cond

if.then:                                          ; preds = %cur1
  br label %cur
}
