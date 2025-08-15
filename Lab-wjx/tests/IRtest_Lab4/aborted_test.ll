; ModuleID = 'my_module'
source_filename = "my_module"

@y = global i32 0

define void @p() {
pEntry:
  %load_lval = load i32, i32* @y, align 4
  %add = add i32 %load_lval, 1
  store i32 %add, i32* @y, align 4
  ret void
}

define i32 @f(i32 %x, i32 %t) {
fEntry:
  %param0_addr = alloca i32, align 4
  store i32 %x, i32* %param0_addr, align 4
  %param1_addr = alloca i32, align 4
  store i32 %t, i32* %param1_addr, align 4
  call void @p()
  %load_lval = load i32, i32* %param0_addr, align 4
  %load_lval1 = load i32, i32* %param1_addr, align 4
  %add = add i32 %load_lval, %load_lval1
  %load_lval2 = load i32, i32* @y, align 4
  %add3 = add i32 %add, %load_lval2
  ret i32 %add3
}

define i32 @g(i32 %x, i32 %y, i32 %z) {
gEntry:
  %param0_addr = alloca i32, align 4
  store i32 %x, i32* %param0_addr, align 4
  %param1_addr = alloca i32, align 4
  store i32 %y, i32* %param1_addr, align 4
  %param2_addr = alloca i32, align 4
  store i32 %z, i32* %param2_addr, align 4
  %load_lval = load i32, i32* %param0_addr, align 4
  %load_lval1 = load i32, i32* %param2_addr, align 4
  %f = call i32 @f(i32 %load_lval, i32 %load_lval1)
  ret i32 %f
}

define i32 @main() {
mainEntry:
  %t = alloca i32, align 4
  store i32 100, i32* %t, align 4
  %x = alloca i32, align 4
  %load_lval = load i32, i32* %t, align 4
  %g = call i32 @g(i32 2, i32 4, i32 %load_lval)
  store i32 %g, i32* %x, align 4
  %load_lval1 = load i32, i32* %x, align 4
  ret i32 %load_lval1
}
