; ModuleID = 'my_module'
source_filename = "my_module"

define i32 @g(i32 %x) {
gEntry:
  %param0_addr = alloca i32, align 4
  store i32 %x, i32* %param0_addr, align 4
  %load_lval = load i32, i32* %param0_addr, align 4
  %add = add i32 %load_lval, 1
  ret i32 %add
}

define i32 @f(i32 %x) {
fEntry:
  %param0_addr = alloca i32, align 4
  store i32 %x, i32* %param0_addr, align 4
  %f = alloca i32, align 4
  store i32 10, i32* %f, align 4
  %load_lval = load i32, i32* %f, align 4
  ret i32 %load_lval
}

define void @x() {
xEntry:
  ret void
}

define void @t() {
tEntry:
  ret void
}

define i32 @main() {
mainEntry:
  %f = call i32 @f(i32 10)
  ret i32 %f
}
