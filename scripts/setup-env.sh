#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./sync-env.sh
source "$SCRIPT_DIR/sync-env.sh"

ALLOW_NON_TTY="${SETUP_ENV_ALLOW_NON_TTY:-0}"

trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

normalize_public_base_url() {
    local normalized
    normalized="$(trim "$1")"

    if [ -z "$normalized" ]; then
        printf '%s' ""
        return 0
    fi

    if [[ ! "$normalized" =~ ^[a-zA-Z][a-zA-Z0-9+.-]*://.*$ ]]; then
        normalized="https://$normalized"
    fi

    while [[ "$normalized" == */ ]]; do
        normalized="${normalized%/}"
    done

    printf '%s' "$normalized"
}

normalize_callback_path() {
    local normalized
    normalized="$(trim "$1")"

    if [ -z "$normalized" ]; then
        printf '%s' "/ecpay/callback"
        return 0
    fi

    if [[ "$normalized" != /* ]]; then
        normalized="/$normalized"
    fi

    printf '%s' "$normalized"
}

extract_public_domain() {
    local raw_value
    raw_value="$(trim "$1")"

    if [ -z "$raw_value" ]; then
        printf '%s' ""
        return 0
    fi

    if [[ "$raw_value" =~ ^[a-zA-Z][a-zA-Z0-9+.-]*://.*$ ]]; then
        raw_value="${raw_value#*://}"
    fi

    raw_value="${raw_value%%/*}"
    while [[ "$raw_value" == */ ]]; do
        raw_value="${raw_value%/}"
    done

    printf '%s' "$raw_value"
}

prompt_value() {
    local label="$1"
    local default_value="${2:-}"
    local response

    if [ -n "$default_value" ]; then
        printf "%s [%s]: " "$label" "$default_value" >&2
    else
        printf "%s: " "$label" >&2
    fi

    if ! IFS= read -r response; then
        return 1
    fi

    response="$(trim "$response")"
    if [ -z "$response" ]; then
        response="$default_value"
    fi

    printf '%s' "$response"
}

prompt_yes_no() {
    local label="$1"
    local default_answer="$2"
    local suffix
    local response

    if [ "$default_answer" = "yes" ]; then
        suffix="[Y/n]"
    else
        suffix="[y/N]"
    fi

    while true; do
        printf "%s %s: " "$label" "$suffix" >&2
        if ! IFS= read -r response; then
            return 1
        fi

        response="$(trim "$response")"
        response="${response,,}"

        case "$response" in
            "")
                printf '%s' "$default_answer"
                return 0
                ;;
            y|yes)
                printf '%s' "yes"
                return 0
                ;;
            n|no)
                printf '%s' "no"
                return 0
                ;;
            *)
                log_warning "請輸入 y / yes 或 n / no"
                ;;
        esac
    done
}

prompt_required_value() {
    local label="$1"
    local default_value="${2:-}"
    local response

    while true; do
        if ! response="$(prompt_value "$label" "$default_value")"; then
            return 1
        fi

        if [ -n "$response" ]; then
            printf '%s' "$response"
            return 0
        fi

        log_warning "$label 不可留空"
    done
}

require_interactive_terminal() {
    if [ "$ALLOW_NON_TTY" = "1" ]; then
        return 0
    fi

    if [ ! -t 0 ] || [ ! -t 1 ]; then
        log_error "setup-env 需要在互動式 TTY 終端機中執行"
        exit 1
    fi
}

get_sensitive_value_status() {
    local file="$1"
    local key="$2"
    local value

    value="$(trim "$(get_env_value "$file" "$key")")"

    if [ -z "$value" ]; then
        printf '%s' "請手動填寫"
        return 0
    fi

    case "$value" in
        your_discord_bot_token_here|your_ai_service_api_key_here)
            printf '%s' "仍是範例值，請手動更新"
            ;;
        *)
            printf '%s' "已保留現值"
            ;;
    esac
}

print_sensitive_summary() {
    local file="$1"
    local key

    echo ""
    echo "請手動檢查下列敏感欄位："
    for key in \
        DISCORD_BOT_TOKEN \
        AI_SERVICE_API_KEY \
        ECPAY_MERCHANT_ID \
        ECPAY_HASH_KEY \
        ECPAY_HASH_IV \
        PRODUCT_FULFILLMENT_SIGNING_SECRET
    do
        echo "  - $key: $(get_sensitive_value_status "$file" "$key")"
    done
}

main() {
    local temp_file=""
    local cleanup_notice=0

    cleanup() {
        local rc=$?
        if [ -n "${temp_file:-}" ]; then
            rm -f "$temp_file" "${temp_file}.next"
        fi
        if [ $rc -ne 0 ] && [ "${cleanup_notice:-0}" -eq 1 ]; then
            log_warning "流程已取消，未修改 $ENV_FILE"
        fi
    }

    trap cleanup EXIT INT TERM

    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo -e "${COLOR_CYAN}  互動式環境設定助手${COLOR_RESET}"
    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo ""

    require_interactive_terminal
    ensure_example_file

    temp_file="$(mktemp "${ENV_FILE}.setup.XXXXXX")"
    cleanup_notice=1

    if [ -f "$ENV_FILE" ]; then
        build_synced_env_file "$ENV_FILE" "$temp_file"
    else
        log_warning "$ENV_FILE 檔案不存在，將從 $EXAMPLE_FILE 建立新檔"
        create_env_from_example "$temp_file"
    fi

    local existing_base_url
    local existing_domain
    local existing_email
    local existing_return_url
    local callback_path
    local default_public_input

    existing_base_url="$(normalize_public_base_url "$(get_env_value "$temp_file" "APP_PUBLIC_BASE_URL")")"
    existing_domain="$(trim "$(get_env_value "$temp_file" "APP_PUBLIC_DOMAIN")")"
    existing_email="$(trim "$(get_env_value "$temp_file" "CADDY_ACME_EMAIL")")"
    existing_return_url="$(normalize_public_base_url "$(get_env_value "$temp_file" "ECPAY_RETURN_URL")")"
    callback_path="$(normalize_callback_path "$(get_env_value "$temp_file" "ECPAY_CALLBACK_PATH")")"

    if [ -z "$existing_domain" ] && [ -n "$existing_base_url" ]; then
        existing_domain="$(extract_public_domain "$existing_base_url")"
    fi

    if [ -n "$existing_base_url" ]; then
        default_public_input="$existing_base_url"
    else
        default_public_input="$existing_domain"
    fi

    echo "這個流程會："
    echo "  1. 以 .env.example 對齊 .env 欄位"
    echo "  2. 互動式設定公開入口與 callback 策略"
    echo "  3. 保留未觸及的既有 secrets"
    echo ""

    local public_input
    local tls_email
    local auto_callback
    local explicit_return_url=""
    local normalized_base_url
    local normalized_domain
    local effective_return_url=""
    local auto_default="yes"

    if [ -n "$existing_return_url" ]; then
        auto_default="no"
    fi

    if ! public_input="$(
        prompt_value "公開 domain 或 base URL（可填 pay.example.com 或 https://pay.example.com）" "$default_public_input"
    )"; then
        exit 1
    fi
    normalized_base_url="$(normalize_public_base_url "$public_input")"
    normalized_domain="$(extract_public_domain "$public_input")"

    if ! tls_email="$(prompt_value "TLS / ACME email" "$existing_email")"; then
        exit 1
    fi
    tls_email="$(trim "$tls_email")"

    if ! auto_callback="$(
        prompt_yes_no "是否使用 APP_PUBLIC_BASE_URL 自動推導 ECPAY_RETURN_URL" "$auto_default"
    )"; then
        exit 1
    fi

    if [ "$auto_callback" = "no" ]; then
        if ! explicit_return_url="$(
            prompt_required_value "顯式 ECPAY_RETURN_URL（完整 URL）" "$existing_return_url"
        )"; then
            exit 1
        fi
        explicit_return_url="$(normalize_public_base_url "$explicit_return_url")"
        effective_return_url="$explicit_return_url"
    elif [ -n "$normalized_base_url" ]; then
        effective_return_url="${normalized_base_url}${callback_path}"
    fi

    replace_key_value_in_file "$temp_file" "APP_PUBLIC_DOMAIN" "$normalized_domain"
    replace_key_value_in_file "$temp_file" "CADDY_ACME_EMAIL" "$tls_email"
    replace_key_value_in_file "$temp_file" "APP_PUBLIC_BASE_URL" "$normalized_base_url"
    replace_key_value_in_file "$temp_file" "ECPAY_RETURN_URL" "$explicit_return_url"

    if [ -f "$ENV_FILE" ]; then
        backup_env "$ENV_FILE" "$BACKUP_FILE"
    fi

    mv "$temp_file" "$ENV_FILE"
    temp_file=""
    cleanup_notice=0

    echo ""
    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo -e "${COLOR_CYAN}  設定摘要${COLOR_RESET}"
    echo -e "${COLOR_CYAN}========================================${COLOR_RESET}"
    echo "  APP_PUBLIC_DOMAIN=${normalized_domain}"
    echo "  CADDY_ACME_EMAIL=${tls_email}"
    echo "  APP_PUBLIC_BASE_URL=${normalized_base_url}"
    if [ -n "$explicit_return_url" ]; then
        echo "  ECPAY_RETURN_URL=${explicit_return_url}"
    else
        echo "  ECPAY_RETURN_URL=（留空，讓應用程式自動推導）"
    fi
    if [ -n "$effective_return_url" ]; then
        echo "  Effective callback URL=${effective_return_url}"
    fi
    echo ""

    if [ "$auto_callback" = "yes" ]; then
        log_info "ECPAY callback 將由 APP_PUBLIC_BASE_URL 與 ECPAY_CALLBACK_PATH 自動推導"
    else
        log_info "已保留顯式 ECPAY_RETURN_URL override"
    fi

    if [ -f "$BACKUP_FILE" ]; then
        log_info "舊版 $ENV_FILE 已備份至 $BACKUP_FILE"
    else
        log_info "此次為新建 ${ENV_FILE}，沒有舊檔可備份"
    fi

    print_sensitive_summary "$ENV_FILE"

    echo ""
    echo "下一步建議："
    echo "  1. 打開 .env 補齊上面仍需手動填寫的敏感欄位"
    echo "  2. 後續若 .env.example 有新增欄位，請執行 make update-env"
    echo "  3. 準備好後可再執行 make start 或 make start-dev"
}

main "$@"
