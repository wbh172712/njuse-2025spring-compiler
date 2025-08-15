; ModuleID = 'my_module'
source_filename = "my_module"

@x = global i32 100
@y = global i32 2
@z = global i32 3

define void @g() {
gEntry:
  %load_lval = load i32, i32* @x, align 4
  %add = add i32 %load_lval, 1
  store i32 %add, i32* @x, align 4
  ret void
}

define i32 @f(i32 %x) {
fEntry:
  %param0_addr = alloca i32, align 4
  store i32 %x, i32* %param0_addr, align 4
  %load_lval = load i32, i32* %param0_addr, align 4
  %cmp = icmp eq i32 %load_lval, 0
  %zext_to_i32 = zext i1 %cmp to i32
  %to_bool = icmp ne i32 %zext_to_i32, 0
  br i1 %to_bool, label %if.then, label %merge

merge:                                            ; preds = %fEntry
  %load_lval1 = load i32, i32* %param0_addr, align 4
  %sub = sub i32 %load_lval1, 1
  %f = call i32 @f(i32 %sub)
  %add = add i32 %f, 10
  store i32 %add, i32* %param0_addr, align 4
  %load_lval2 = load i32, i32* %param0_addr, align 4
  ret i32 %load_lval2

if.then:                                          ; preds = %fEntry
  ret i32 0
}

define i32 @main() {
mainEntry:
  br label %while.cond

cur:                                              ; preds = %merge49, %while.cond
  %load_lval109 = load i32, i32* @x, align 4
  ret i32 %load_lval109

while.stmt:                                       ; preds = %while.cond
  %f = call i32 @f(i32 200)
  store i32 %f, i32* @x, align 4
  br label %while.cond3

while.cond:                                       ; preds = %mainEntry
  br i1 true, label %while.stmt, label %cur

cur1:                                             ; preds = %if.then, %while.cond3
  %load_lval7 = load i32, i32* @x, align 4
  %cmp8 = icmp sge i32 %load_lval7, 100
  %zext_to_i329 = zext i1 %cmp8 to i32
  %to_bool11 = icmp ne i32 %zext_to_i329, 0
  br i1 %to_bool11, label %if.then10, label %merge6

while.stmt2:                                      ; preds = %while.cond3
  call void @g()
  %load_lval4 = load i32, i32* @x, align 4
  %cmp = icmp sge i32 %load_lval4, 200
  %zext_to_i32 = zext i1 %cmp to i32
  %to_bool5 = icmp ne i32 %zext_to_i32, 0
  br i1 %to_bool5, label %if.then, label %merge

while.cond3:                                      ; preds = %merge, %while.stmt
  %load_lval = load i32, i32* @x, align 4
  %to_bool = icmp ne i32 %load_lval, 0
  br i1 %to_bool, label %while.stmt2, label %cur1

merge:                                            ; preds = %while.stmt2
  br label %while.cond3

if.then:                                          ; preds = %while.stmt2
  br label %cur1

merge6:                                           ; preds = %cur32, %cur1
  store i32 5000, i32* @x, align 4
  %load_lval50 = load i32, i32* @x, align 4
  %cmp51 = icmp sge i32 %load_lval50, 125
  %zext_to_i3252 = zext i1 %cmp51 to i32
  %to_bool54 = icmp ne i32 %zext_to_i3252, 0
  br i1 %to_bool54, label %if.then53, label %merge49

if.then10:                                        ; preds = %cur1
  %load_lval13 = load i32, i32* @x, align 4
  %cmp14 = icmp slt i32 %load_lval13, 10
  %zext_to_i3215 = zext i1 %cmp14 to i32
  %to_bool17 = icmp ne i32 %zext_to_i3215, 0
  br i1 %to_bool17, label %if.then16, label %if.else

merge12:                                          ; preds = %merge18
  %load_lval31 = load i32, i32* @x, align 4
  %add = add i32 %load_lval31, 1
  store i32 %add, i32* @x, align 4
  br label %while.cond34

if.then16:                                        ; preds = %if.then10
  ret i32 0

if.else:                                          ; preds = %if.then10
  %load_lval19 = load i32, i32* @x, align 4
  %cmp20 = icmp sgt i32 %load_lval19, 200
  %zext_to_i3221 = zext i1 %cmp20 to i32
  %to_bool24 = icmp ne i32 %zext_to_i3221, 0
  br i1 %to_bool24, label %if.then22, label %if.else23

merge18:                                          ; preds = %merge25
  br label %merge12

if.then22:                                        ; preds = %if.else
  ret i32 2

if.else23:                                        ; preds = %if.else
  %load_lval26 = load i32, i32* @x, align 4
  %cmp27 = icmp sgt i32 %load_lval26, 256
  %zext_to_i3228 = zext i1 %cmp27 to i32
  %to_bool30 = icmp ne i32 %zext_to_i3228, 0
  br i1 %to_bool30, label %if.then29, label %merge25

merge25:                                          ; preds = %if.else23
  br label %merge18

if.then29:                                        ; preds = %if.else23
  ret i32 3

cur32:                                            ; preds = %while.cond34
  br label %merge6

while.stmt33:                                     ; preds = %while.cond34
  %load_lval39 = load i32, i32* @x, align 4
  %add40 = add i32 %load_lval39, 1
  store i32 %add40, i32* @x, align 4
  %load_lval42 = load i32, i32* @x, align 4
  %cmp43 = icmp slt i32 %load_lval42, 125
  %zext_to_i3244 = zext i1 %cmp43 to i32
  %to_bool46 = icmp ne i32 %zext_to_i3244, 0
  br i1 %to_bool46, label %if.then45, label %merge41

while.cond34:                                     ; preds = %merge41, %if.then45, %merge12
  %load_lval35 = load i32, i32* @x, align 4
  %cmp36 = icmp slt i32 %load_lval35, 200
  %zext_to_i3237 = zext i1 %cmp36 to i32
  %to_bool38 = icmp ne i32 %zext_to_i3237, 0
  br i1 %to_bool38, label %while.stmt33, label %cur32

merge41:                                          ; preds = %while.stmt33
  call void @g()
  %load_lval47 = load i32, i32* @x, align 4
  %add48 = add i32 %load_lval47, 2
  store i32 %add48, i32* @x, align 4
  br label %while.cond34

if.then45:                                        ; preds = %while.stmt33
  br label %while.cond34

merge49:                                          ; preds = %merge99, %merge6
  %load_lval107 = load i32, i32* @x, align 4
  %sub108 = sub i32 %load_lval107, 100
  br label %cur

if.then53:                                        ; preds = %merge6
  %load_lval56 = load i32, i32* @x, align 4
  %cmp57 = icmp slt i32 %load_lval56, 2030
  %zext_to_i3258 = zext i1 %cmp57 to i32
  %to_bool61 = icmp ne i32 %zext_to_i3258, 0
  br i1 %to_bool61, label %if.then59, label %if.else60

merge55:                                          ; preds = %merge84, %merge77
  %load_lval100 = load i32, i32* @x, align 4
  %cmp101 = icmp sgt i32 %load_lval100, 10
  %zext_to_i32102 = zext i1 %cmp101 to i32
  %to_bool104 = icmp ne i32 %zext_to_i32102, 0
  br i1 %to_bool104, label %if.then103, label %merge99

if.then59:                                        ; preds = %if.then53
  br label %while.cond64

if.else60:                                        ; preds = %if.then53
  %load_lval85 = load i32, i32* @x, align 4
  %cmp86 = icmp slt i32 %load_lval85, 5000
  %zext_to_i3287 = zext i1 %cmp86 to i32
  %to_bool90 = icmp ne i32 %zext_to_i3287, 0
  br i1 %to_bool90, label %if.then88, label %if.else89

cur62:                                            ; preds = %while.cond64
  %load_lval78 = load i32, i32* @x, align 4
  %cmp79 = icmp sgt i32 %load_lval78, 100
  %zext_to_i3280 = zext i1 %cmp79 to i32
  %to_bool82 = icmp ne i32 %zext_to_i3280, 0
  br i1 %to_bool82, label %if.then81, label %merge77

while.stmt63:                                     ; preds = %while.cond64
  %load_lval70 = load i32, i32* @x, align 4
  %to_bool73 = icmp ne i32 %load_lval70, 0
  br i1 %to_bool73, label %if.then71, label %if.else72

while.cond64:                                     ; preds = %merge69, %if.then59
  %load_lval65 = load i32, i32* @x, align 4
  %cmp66 = icmp sgt i32 %load_lval65, 100
  %zext_to_i3267 = zext i1 %cmp66 to i32
  %to_bool68 = icmp ne i32 %zext_to_i3267, 0
  br i1 %to_bool68, label %while.stmt63, label %cur62

merge69:                                          ; preds = %if.else72, %if.then71
  br label %while.cond64

if.then71:                                        ; preds = %while.stmt63
  %load_lval74 = load i32, i32* @x, align 4
  %sub = sub i32 %load_lval74, 1
  store i32 %sub, i32* @x, align 4
  br label %merge69

if.else72:                                        ; preds = %while.stmt63
  %load_lval75 = load i32, i32* @x, align 4
  %sub76 = sub i32 %load_lval75, 2
  store i32 %sub76, i32* @x, align 4
  br label %merge69

merge77:                                          ; preds = %cur62
  br label %merge55

if.then81:                                        ; preds = %cur62
  %load_lval83 = load i32, i32* @x, align 4
  ret i32 %load_lval83

merge84:                                          ; preds = %merge91, %if.then88
  br label %merge55

if.then88:                                        ; preds = %if.else60
  store i32 5016, i32* @x, align 4
  br label %merge84

if.else89:                                        ; preds = %if.else60
  %load_lval92 = load i32, i32* @x, align 4
  %cmp93 = icmp slt i32 %load_lval92, 231
  %zext_to_i3294 = zext i1 %cmp93 to i32
  %to_bool96 = icmp ne i32 %zext_to_i3294, 0
  br i1 %to_bool96, label %if.then95, label %merge91

merge91:                                          ; preds = %if.else89
  br label %merge84

if.then95:                                        ; preds = %if.else89
  %load_lval97 = load i32, i32* @x, align 4
  %add98 = add i32 %load_lval97, 1
  ret i32 %add98

merge99:                                          ; preds = %if.then103, %merge55
  br label %merge49

if.then103:                                       ; preds = %merge55
  %load_lval105 = load i32, i32* @x, align 4
  %sub106 = sub i32 %load_lval105, 10
  store i32 %sub106, i32* @x, align 4
  br label %merge99
}
