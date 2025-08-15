; ModuleID = 'my_module'
source_filename = "my_module"

@a = global i32 10

define i32 @main() {
mainEntry:
  %b = alloca i32, align 4
  store i32 2, i32* %b, align 4
  %c = alloca i32, align 4
  store i32 3, i32* %c, align 4
  %load_lval = load i32, i32* %b, align 4
  %add = add i32 %load_lval, 2
  %not = icmp eq i32 %add, 0
  %zext_to_i32 = zext i1 %not to i32
  %cmp = icmp eq i32 %zext_to_i32, 4
  %zext_to_i321 = zext i1 %cmp to i32
  %to_bool = icmp ne i32 %zext_to_i321, 0
  br i1 %to_bool, label %if.then, label %merge

merge:                                            ; preds = %mainEntry
  %load_lval3 = load i32, i32* @a, align 4
  ret i32 %load_lval3

if.then:                                          ; preds = %mainEntry
  %load_lval2 = load i32, i32* @a, align 4
  ret i32 %load_lval2
}
