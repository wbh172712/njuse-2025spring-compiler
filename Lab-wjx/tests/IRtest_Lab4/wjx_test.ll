; ModuleID = 'my_module'
source_filename = "my_module"

define void @f(i32 %a) {
fEntry:
  %param0_addr = alloca i32, align 4
  store i32 %a, i32* %param0_addr, align 4
  %load_lval = load i32, i32* %param0_addr, align 4
  %add = add i32 %load_lval, 1
  store i32 %add, i32* %param0_addr, align 4
  ret void
}

define i32 @main() {
mainEntry:
  call void @f(i32 1)
  ret i32 0
}
