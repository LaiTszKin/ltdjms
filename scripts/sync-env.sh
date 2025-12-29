#!/bin/bash

# 環境變數同步腳本
# 功能：將 .env 同步到 .env.example 的狀態
# - 備份現有 .env 到 .env.bak
# - 移除過時的環境變數
# - 添加缺失的新配置
# - 報告所有變更

set -euo pipefail

# 顏色定義
COLOR_RESET='\033[0m'
COLOR_GREEN='\033[0;32m'
COLOR_YELLOW='\033[0;33m'
COLOR_RED='\033[0;31m'
COLOR_BLUE='\033[0;34m'
COLOR_CYAN='\033[0;36m'

# 日誌函數
log_info() {
    echo -e "${COLOR_BLUE}[INFO]${COLOR_RESET} $1"
}

log_success() {
    echo -e "${COLOR_GREEN}[✓]${COLOR_RESET} $1"
}

log_warning() {
    echo -e "${COLOR_YELLOW}[!]${COLOR_RESET} $1"
}

log_error() {
    echo -e "${COLOR_RED}[✗]${COLOR_RESET} $1"
}

# 檢查檔案是否存在
check_files() {
    if [ ! -f .env.example ]; then
        log_error ".env.example 檔案不存在"
        exit 1
    fi

    if [ ! -f .env ]; then
        log_warning ".env 檔案不存在，從 .env.example 創建"
        cp .env.example .env
        log_success "已創建 .env 檔案"
        exit 0
    fi
}

# 解析環境變數（返回鍵名陣列）
parse_env_keys() {
    local file=$1
    grep -E '^[A-Za-z_][A-Za-z0-9_]*=' "$file" | cut -d'=' -f1 | sort
}

# 獲取變數值
get_env_value() {
    local file=$1
    local key=$2
    grep -E "^${key}=" "$file" | cut -d'=' -f2-
}

# 備份 .env 檔案
backup_env() {
    log_info "備份 .env 到 .env.bak"
    cp .env .env.bak
    log_success "備份完成"
}

# 主函數
main() {
    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo -e "${COLOR_CYAN}  環境變數同步腳本${COLOR_RESET}"
    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo ""

    check_files
    backup_env

    # 獲取所有變數名
    local example_keys
    local env_keys

    example_keys=$(parse_env_keys .env.example)
    env_keys=$(parse_env_keys .env)

    # 找出需要刪除的變數（在 .env 但不在 .env.example）
    local to_delete=()
    while IFS= read -r key; do
        if ! echo "$example_keys" | grep -qx "$key"; then
            to_delete+=("$key")
        fi
    done <<< "$env_keys"

    # 找出需要添加的變數（在 .env.example 但不在 .env）
    local to_add=()
    while IFS= read -r key; do
        if ! echo "$env_keys" | grep -qx "$key"; then
            to_add+=("$key")
        fi
    done <<< "$example_keys"

    # 如果沒有變更，提示並退出
    if [ ${#to_delete[@]} -eq 0 ] && [ ${#to_add[@]} -eq 0 ]; then
        log_success ".env 已是最新狀態，無需變更"
        exit 0
    fi

    # 報告計劃變更
    echo ""
    log_info "計劃執行的變更："

    if [ ${#to_delete[@]} -gt 0 ]; then
        echo -e "  ${COLOR_RED}將刪除 ${#to_delete[@]} 個過時變數：${COLOR_RESET}"
        for key in "${to_delete[@]}"; do
            echo -e "    ${COLOR_RED}-${COLOR_RESET} $key"
        done
    fi

    if [ ${#to_add[@]} -gt 0 ]; then
        echo -e "  ${COLOR_GREEN}將添加 ${#to_add[@]} 個新變數：${COLOR_RESET}"
        for key in "${to_add[@]}"; do
            echo -e "    ${COLOR_GREEN}+${COLOR_RESET} $key"
        done
    fi

    echo ""
    read -p "是否繼續？(y/N): " -n 1 -r
    echo ""

    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_warning "已取消操作"
        exit 0
    fi

    # 執行同步
    log_info "正在同步 .env..."

    # 創建臨時檔案
    local temp_file
    temp_file=$(mktemp)

    # 首先複製 .env.example 到臨時檔案
    cp .env.example "$temp_file"

    # 對於 .env 中存在且 .env.example 也存在的變數，保留 .env 中的值
    while IFS= read -r key; do
        if echo "$example_keys" | grep -qx "$key"; then
            local value
            value=$(get_env_value .env "$key")
            # 使用 awk 替換值（更安全且跨平台兼容）
            awk -F= -v k="$key" -v v="$value" '
                $1 == k { print k "=" v; next }
                { print }
            ' "$temp_file" > "${temp_file}.tmp"
            mv "${temp_file}.tmp" "$temp_file"
        fi
    done <<< "$env_keys"

    # 覆蓋 .env
    mv "$temp_file" .env

    # 報告結果
    echo ""
    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo -e "${COLOR_CYAN}  同步完成${COLOR_RESET}"
    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo ""

    if [ ${#to_delete[@]} -gt 0 ]; then
        echo -e "${COLOR_RED}已刪除的變數：${COLOR_RESET}"
        for key in "${to_delete[@]}"; do
            echo -e "  ${COLOR_RED}-${COLOR_RESET} $key"
        done
        echo ""
    fi

    if [ ${#to_add[@]} -gt 0 ]; then
        echo -e "${COLOR_GREEN}已添加的變數：${COLOR_RESET}"
        for key in "${to_add[@]}"; do
            local value
            value=$(get_env_value .env "$key")
            echo -e "  ${COLOR_GREEN}+${COLOR_RESET} $key=${value}"
        done
        echo ""
    fi

    log_success "環境變數同步完成！"
    log_info "舊版 .env 已備份至 .env.bak"
}

main "$@"
