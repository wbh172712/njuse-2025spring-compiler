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

define i32 @main() {
mainEntry:
  %x = alloca i32, align 4
  %g = call i32 @g(i32 10)
  store i32 %g, i32* %x, align 4
  br label %while.cond

cur:                                              ; preds = %if.else45, %while.cond
  ret i32 1

while.stmt:                                       ; preds = %while.cond
  %load_lval1 = load i32, i32* %x, align 4
  %add = add i32 %load_lval1, 1
  store i32 %add, i32* %x, align 4
  %load_lval2 = load i32, i32* %x, align 4
  %cmp3 = icmp sgt i32 %load_lval2, 30
  %zext_to_i324 = zext i1 %cmp3 to i32
  %to_bool5 = icmp ne i32 %zext_to_i324, 0
  br i1 %to_bool5, label %if.then, label %merge

while.cond:                                       ; preds = %merge40, %mainEntry
  %load_lval = load i32, i32* %x, align 4
  %cmp = icmp slt i32 %load_lval, 100
  %zext_to_i32 = zext i1 %cmp to i32
  %to_bool = icmp ne i32 %zext_to_i32, 0
  br i1 %to_bool, label %while.stmt, label %cur

merge:                                            ; preds = %while.stmt
  %load_lval41 = load i32, i32* %x, align 4
  %cmp42 = icmp sgt i32 %load_lval41, 10
  %zext_to_i3243 = zext i1 %cmp42 to i32
  %to_bool46 = icmp ne i32 %zext_to_i3243, 0
  br i1 %to_bool46, label %if.then44, label %if.else45

if.then:                                          ; preds = %while.stmt
  br label %while.cond8

cur6:                                             ; preds = %if.else33, %while.cond8
  %load_lval37 = load i32, i32* %x, align 4
  %sub38 = sub i32 %load_lval37, 1
  store i32 %sub38, i32* %x, align 4
  %load_lval39 = load i32, i32* %x, align 4
  ret i32 %load_lval39

while.stmt7:                                      ; preds = %while.cond8
  %load_lval14 = load i32, i32* %x, align 4
  %cmp15 = icmp sgt i32 %load_lval14, 20
  %zext_to_i3216 = zext i1 %cmp15 to i32
  %to_bool18 = icmp ne i32 %zext_to_i3216, 0
  br i1 %to_bool18, label %if.then17, label %if.else

while.cond8:                                      ; preds = %merge13, %merge19, %if.then
  %load_lval9 = load i32, i32* %x, align 4
  %cmp10 = icmp sgt i32 %load_lval9, 10
  %zext_to_i3211 = zext i1 %cmp10 to i32
  %to_bool12 = icmp ne i32 %zext_to_i3211, 0
  br i1 %to_bool12, label %while.stmt7, label %cur6

merge13:                                          ; preds = %merge28
  br label %while.cond8

if.then17:                                        ; preds = %while.stmt7
  %load_lval20 = load i32, i32* %x, align 4
  %cmp21 = icmp sgt i32 %load_lval20, 21
  %zext_to_i3222 = zext i1 %cmp21 to i32
  %to_bool24 = icmp ne i32 %zext_to_i3222, 0
  br i1 %to_bool24, label %if.then23, label %merge19

if.else:                                          ; preds = %while.stmt7
  %load_lval29 = load i32, i32* %x, align 4
  %cmp30 = icmp sgt i32 %load_lval29, 25
  %zext_to_i3231 = zext i1 %cmp30 to i32
  %to_bool34 = icmp ne i32 %zext_to_i3231, 0
  br i1 %to_bool34, label %if.then32, label %if.else33

merge19:                                          ; preds = %if.then23, %if.then17
  %load_lval26 = load i32, i32* %x, align 4
  %sub27 = sub i32 %load_lval26, 1
  store i32 %sub27, i32* %x, align 4
  br label %while.cond8

if.then23:                                        ; preds = %if.then17
  %load_lval25 = load i32, i32* %x, align 4
  %sub = sub i32 %load_lval25, 5
  store i32 %sub, i32* %x, align 4
  br label %merge19

merge28:                                          ; preds = %if.then32
  br label %merge13

if.then32:                                        ; preds = %if.else
  %load_lval35 = load i32, i32* %x, align 4
  %add36 = add i32 %load_lval35, 1
  store i32 %add36, i32* %x, align 4
  br label %merge28

if.else33:                                        ; preds = %if.else
  br label %cur6

merge40:                                          ; preds = %if.then44
  br label %while.cond

if.then44:                                        ; preds = %merge
  %load_lval47 = load i32, i32* %x, align 4
  %add48 = add i32 %load_lval47, 5
  store i32 %add48, i32* %x, align 4
  br label %merge40

if.else45:                                        ; preds = %merge
  br label %cur
}
