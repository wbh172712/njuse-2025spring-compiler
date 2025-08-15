#!/bin/bash

SYSY_DIR="tests/IRtest_Lab4"
echo "== Batch processing all .sysy files in $SYSY_DIR =="

for sysy_file in "$SYSY_DIR"/*.sysy; do
    filename=$(basename "$sysy_file" .sysy)
    c_file="$SYSY_DIR/$filename.c"
    exe_file="$SYSY_DIR/$filename"
    ll_file="$SYSY_DIR/$filename.ll"

    echo "------------------------------"
    echo "Processing: $filename.sysy"

    # Step 1: 模拟生成 .c 文件（如果你是从 sysy 到 c 的，可以替换这一步）
    cp "$sysy_file" "$c_file"

    # Step 2: 用 gcc 编译为可执行文件
    gcc "$c_file" -o "$exe_file"
    if [ $? -ne 0 ]; then
        echo "Compilation failed for $c_file"
        continue
    fi

    # Step 3: 运行可执行文件并获取返回值
    "$exe_file"
    c_return_value=$?
    echo "C Return value: $c_return_value"

    # Step 4: 用 make run 生成 .ll 文件
    make -s run SRCFILE="$sysy_file" OUTFILE="$ll_file"
    if [ $? -ne 0 ]; then
        echo "IR generation failed for $exe_file"
        continue
    fi

    # Step 5: 用 lli 运行 .ll 文件
    lli "$ll_file"
    ll_return_value=$?
    echo "LLVM IR Return value: $ll_return_value"

    # Step 6: 比较两个返回值
    if [ "$c_return_value" -eq "$ll_return_value" ]; then
        echo "✅ Return values match."
    else
        echo "❌ Return values differ!"
    fi
done
