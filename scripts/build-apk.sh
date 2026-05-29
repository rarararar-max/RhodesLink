#!/bin/bash
# APK 构建脚本
# 用法: ./scripts/build-apk.sh [arm64|amd64|universal|all] [debug|release]

# 调试模式：打印每条命令
set -ex

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$PROJECT_DIR/apk-output"

echo "========================================="
echo "脚本目录: $SCRIPT_DIR"
echo "项目目录: $PROJECT_DIR"
echo "输出目录: $OUTPUT_DIR"
echo "当前工作目录: $(pwd)"
echo "构建架构: ${1:-all}"
echo "构建变体: ${2:-debug}"
echo "========================================="

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# 清理输出目录内容
prepare_output() {
    mkdir -p "$OUTPUT_DIR"
    rm -f "$OUTPUT_DIR"/*.apk 2>/dev/null || true
    log_info "输出目录已准备: $OUTPUT_DIR"
}

# 构建指定 ABI 的 APK
build_apk() {
    local abi=$1
    local output_name=$2
    local build_variant=${3:-debug}

    log_info "开始构建 $output_name (ABI: $abi, 变体: $build_variant)..."

    cd "$PROJECT_DIR"
    log_info "当前目录: $(pwd)"

    if [ "$abi" = "universal" ]; then
        ./gradlew "assemble${build_variant^}" --no-daemon --stacktrace
    else
        ./gradlew "assemble${build_variant^}" -PTARGET_ABI="$abi" --no-daemon --stacktrace
    fi

    log_info "Gradle 构建完成，开始查找 APK..."

    # 列出构建输出目录的内容
    log_info "构建输出目录结构:"
    find "$PROJECT_DIR/app/build/outputs" -type f -name "*.apk" 2>/dev/null || true

    # 查找生成的 APK
    local apk_path=""
    for search_dir in \
        "$PROJECT_DIR/app/build/outputs/apk/$build_variant" \
        "$PROJECT_DIR/app/build/outputs/apk/debug" \
        "$PROJECT_DIR/app/build/outputs/apk/release"; do
        if [ -d "$search_dir" ]; then
            log_info "搜索目录: $search_dir"
            apk_path=$(find "$search_dir" -name "*.apk" -type f | head -1)
            if [ -n "$apk_path" ]; then
                log_info "找到 APK: $apk_path"
                break
            fi
        fi
    done

    if [ -z "$apk_path" ]; then
        log_warn "未找到 $output_name 的 APK 文件"
        log_info "搜索路径: $PROJECT_DIR/app/build/outputs/apk/"
        find "$PROJECT_DIR/app/build/outputs/" -type f 2>/dev/null || true
        return 1
    fi

    # 验证 APK 文件大小（至少 1MB）
    local file_size=$(stat -f%z "$apk_path" 2>/dev/null || stat -c%s "$apk_path" 2>/dev/null || echo 0)
    if [ "$file_size" -lt 1048576 ]; then
        log_warn "APK 文件过小 (${file_size} bytes)，可能构建失败"
        return 1
    fi

    # 复制并重命名
    cp "$apk_path" "$OUTPUT_DIR/${output_name}.apk"
    log_info "$output_name 构建完成: $OUTPUT_DIR/${output_name}.apk ($(ls -lh "$OUTPUT_DIR/${output_name}.apk" | awk '{print $5}'))"
}

# 显示帮助
show_help() {
    echo "用法: $0 [架构] [变体]"
    echo ""
    echo "架构选项:"
    echo "  arm64      构建 arm64-v8a 架构的 APK"
    echo "  amd64      构建 x86_64 架构的 APK"
    echo "  universal  构建包含所有架构的 APK"
    echo "  all        构建所有架构的 APK (默认)"
    echo ""
    echo "变体选项:"
    echo "  debug      调试版本，可直接安装 (默认)"
    echo "  release    发布版本，需配置签名"
    echo ""
    echo "示例:"
    echo "  $0 arm64 debug"
    echo "  $0 all release"
    echo "  $0 universal"
}

# 主函数
main() {
    local target="${1:-all}"
    local variant="${2:-debug}"

    # 验证变体参数
    if [ "$variant" != "debug" ] && [ "$variant" != "release" ]; then
        log_warn "无效的变体: $variant，使用默认值 debug"
        variant="debug"
    fi

    log_info "构建目标: $target, 变体: $variant"

    case "$target" in
        arm64)
            prepare_output
            build_apk "arm64-v8a" "app-arm64-v8a-${variant}" "$variant"
            ;;
        amd64)
            prepare_output
            build_apk "x86_64" "app-x86_64-${variant}" "$variant"
            ;;
        universal)
            prepare_output
            build_apk "universal" "app-universal-${variant}" "$variant"
            ;;
        all)
            prepare_output
            build_apk "arm64-v8a" "app-arm64-v8a-${variant}" "$variant" || true
            build_apk "x86_64" "app-x86_64-${variant}" "$variant" || true
            build_apk "universal" "app-universal-${variant}" "$variant" || true

            log_info "所有架构构建完成！"
            echo ""
            log_info "生成的 APK 文件:"
            ls -lh "$OUTPUT_DIR"/*.apk 2>/dev/null || log_warn "未找到 APK 文件"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_warn "未知选项: $target"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
