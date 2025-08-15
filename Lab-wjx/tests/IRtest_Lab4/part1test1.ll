; ModuleID = 'my_module'
source_filename = "my_module"

@a = global i32 10

define i32 @main() {
mainEntry:
  %load_lval = load i32, i32* @a, align 4
  %cmp = icmp ne i32 %load_lval, 10
  %zext_to_i32 = zext i1 %cmp to i32
  %lhs_bool = icmp ne i32 %zext_to_i32, 0
  br i1 %lhs_bool, label %and.rhs, label %and.merge

merge:                                            ; preds = %if.then, %and.merge
  %load_lval6 = load i32, i32* @a, align 4
  %cmp7 = icmp eq i32 %load_lval6, 4
  %zext_to_i328 = zext i1 %cmp7 to i32
  %to_bool10 = icmp ne i32 %zext_to_i328, 0
  br i1 %to_bool10, label %if.then9, label %if.else

and.rhs:                                          ; preds = %mainEntry
  %load_lval1 = load i32, i32* @a, align 4
  %cmp2 = icmp ne i32 %load_lval1, 2
  %zext_to_i323 = zext i1 %cmp2 to i32
  %rhs_bool = icmp ne i32 %zext_to_i323, 0
  br label %and.merge

and.merge:                                        ; preds = %and.rhs, %mainEntry
  %and_result = phi i1 [ false, %mainEntry ], [ %rhs_bool, %and.rhs ]
  %zext_to_i324 = zext i1 %and_result to i32
  %to_bool = icmp ne i32 %zext_to_i324, 0
  br i1 %to_bool, label %if.then, label %merge

if.then:                                          ; preds = %and.merge
  store i32 2, i32* @a, align 4
  br label %merge

merge5:                                           ; preds = %merge11, %if.then9
  %load_lval25 = load i32, i32* @a, align 4
  %add = add i32 %load_lval25, 1
  store i32 %add, i32* @a, align 4
  %load_lval26 = load i32, i32* @a, align 4
  ret i32 %load_lval26

if.then9:                                         ; preds = %merge
  store i32 5, i32* @a, align 4
  br label %merge5

if.else:                                          ; preds = %merge
  %load_lval12 = load i32, i32* @a, align 4
  %cmp13 = icmp eq i32 %load_lval12, 3
  %zext_to_i3214 = zext i1 %cmp13 to i32
  %to_bool17 = icmp ne i32 %zext_to_i3214, 0
  br i1 %to_bool17, label %if.then15, label %if.else16

merge11:                                          ; preds = %merge18, %if.then15
  br label %merge5

if.then15:                                        ; preds = %if.else
  store i32 20, i32* @a, align 4
  br label %merge11

if.else16:                                        ; preds = %if.else
  %load_lval19 = load i32, i32* @a, align 4
  %cmp20 = icmp eq i32 %load_lval19, 6
  %zext_to_i3221 = zext i1 %cmp20 to i32
  %to_bool24 = icmp ne i32 %zext_to_i3221, 0
  br i1 %to_bool24, label %if.then22, label %if.else23

merge18:                                          ; preds = %if.else23, %if.then22
  br label %merge11

if.then22:                                        ; preds = %if.else16
  store i32 7, i32* @a, align 4
  br label %merge18

if.else23:                                        ; preds = %if.else16
  store i32 8, i32* @a, align 4
  br label %merge18
}
