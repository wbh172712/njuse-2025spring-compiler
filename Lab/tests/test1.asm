; ModuleID = 'module'
source_filename = "module"

@global_var = global i32 1

define i32 @main() {
mainEntry:
  %num = alloca i32, align 4
  store i32 1, i32* %num, align 4
  %c = alloca i32, align 4
  %num1 = load i32, i32* %num, align 4
  store i32 %num1, i32* %c, align 4
  %i = alloca i32, align 4
  store i32 0, i32* %i, align 4
  %i2 = load i32, i32* %i, align 4
  %c3 = load i32, i32* %c, align 4
  %cmp_tmp = icmp sgt i32 %i2, %c3
  %cmp_ext = zext i1 %cmp_tmp to i32
  %tmp = icmp ne i32 0, %cmp_ext
  br i1 %tmp, label %true, label %false

true:                                             ; preds = %mainEntry
  %num4 = load i32, i32* %num, align 4
  %add_tmp = add i32 %num4, 1
  store i32 %add_tmp, i32* %num, align 4
  %i5 = load i32, i32* %i, align 4
  %add_tmp6 = add i32 %i5, 1
  store i32 %add_tmp6, i32* %i, align 4
  br label %merge

false:                                            ; preds = %mainEntry
  %num7 = load i32, i32* %num, align 4
  %add_tmp8 = add i32 %num7, 1
  store i32 %add_tmp8, i32* %num, align 4
  %i9 = load i32, i32* %i, align 4
  %add_tmp10 = add i32 %i9, 1
  store i32 %add_tmp10, i32* %i, align 4
  br label %merge

merge:                                            ; preds = %false, %true
  %num11 = load i32, i32* %num, align 4
  %num12 = load i32, i32* %num, align 4
  %mul_tmp = mul i32 %num11, %num12
  %c13 = load i32, i32* %c, align 4
  %add_tmp14 = add i32 %mul_tmp, %c13
  ret i32 %add_tmp14
}
