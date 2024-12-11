#!/bin/bash

# 检查参数
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input_log_file> <output_json_file>"
    exit 1
fi

INPUT_FILE=$1
OUTPUT_FILE=$2

# 提取堆栈信息时包含行号
grep "Stack trace (JSON): " "$INPUT_FILE" | \
    sed 's/.*Stack trace (JSON): //' | \
    while read -r line; do
        # 从堆栈中提取行号
        # 格式: at com.package.Class.method(Class.java:123)
        line_number=$(echo "$line" | grep -oP '\(.*?:(\d+)\)' | grep -oP '\d+')
        # ... 处理JSON
    done

# 包装成JSON数组
sed -i '1i\[' "$OUTPUT_FILE"
sed -i '$s/,$/\n]/' "$OUTPUT_FILE" 