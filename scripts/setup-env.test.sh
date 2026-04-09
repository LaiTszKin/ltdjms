#!/usr/bin/env bash

set -euo pipefail

TEST_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$TEST_ROOT/.." && pwd)"

PASS_COUNT=0
FAIL_COUNT=0

assert_contains() {
  local file="$1"
  local needle="$2"
  if ! grep -Fq -- "$needle" "$file"; then
    echo "[FAIL] Expected to find '$needle' in $file"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    return 1
  fi
}

assert_not_contains() {
  local file="$1"
  local needle="$2"
  if grep -Fq -- "$needle" "$file"; then
    echo "[FAIL] Expected NOT to find '$needle' in $file"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    return 1
  fi
}

assert_file_equals() {
  local left="$1"
  local right="$2"
  if ! cmp -s "$left" "$right"; then
    echo "[FAIL] Expected $left to equal $right"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    return 1
  fi
}

setup_workspace() {
  local workspace
  workspace="$(mktemp -d)"
  mkdir -p "$workspace/scripts"

  cp "$PROJECT_ROOT/scripts/sync-env.sh" "$workspace/scripts/sync-env.sh"
  cp "$PROJECT_ROOT/scripts/setup-env.sh" "$workspace/scripts/setup-env.sh"
  chmod +x "$workspace/scripts/sync-env.sh" "$workspace/scripts/setup-env.sh"

  cat > "$workspace/.env.example" <<'EOF'
DISCORD_BOT_TOKEN=your_discord_bot_token_here
AI_SERVICE_API_KEY=your_ai_service_api_key_here
ECPAY_MERCHANT_ID=
ECPAY_HASH_KEY=
ECPAY_HASH_IV=
PRODUCT_FULFILLMENT_SIGNING_SECRET=
APP_PUBLIC_DOMAIN=
CADDY_ACME_EMAIL=
APP_PUBLIC_BASE_URL=
ECPAY_RETURN_URL=
ECPAY_CALLBACK_PATH=/ecpay/callback
EOF

  printf '%s\n' "$workspace"
}

run_setup_script() {
  local workspace="$1"
  local input_text="$2"
  local stdout_file="$3"
  local stderr_file="$4"
  local allow_non_tty="${5:-1}"

  (
    cd "$workspace"
    printf '%s' "$input_text" \
      | SETUP_ENV_ALLOW_NON_TTY="$allow_non_tty" bash ./scripts/setup-env.sh \
      >"$stdout_file" 2>"$stderr_file"
  )
}

run_update_script() {
  local workspace="$1"
  local stdout_file="$2"
  local stderr_file="$3"

  (
    cd "$workspace"
    bash ./scripts/sync-env.sh >"$stdout_file" 2>"$stderr_file"
  )
}

test_setup_env_creates_and_normalizes_values() {
  local workspace
  workspace="$(setup_workspace)"
  local stdout_file="$workspace/stdout.log"
  local stderr_file="$workspace/stderr.log"

  run_setup_script "$workspace" $'pay.example.com/\nops@example.com\n\n' "$stdout_file" "$stderr_file"

  assert_contains "$workspace/.env" "APP_PUBLIC_DOMAIN=pay.example.com"
  assert_contains "$workspace/.env" "CADDY_ACME_EMAIL=ops@example.com"
  assert_contains "$workspace/.env" "APP_PUBLIC_BASE_URL=https://pay.example.com"
  assert_contains "$workspace/.env" "ECPAY_RETURN_URL="
  assert_not_contains "$workspace/.env" "ECPAY_RETURN_URL=https://"
  assert_contains "$stdout_file" "Effective callback URL=https://pay.example.com/ecpay/callback"
  assert_contains "$stdout_file" "DISCORD_BOT_TOKEN: 仍是範例值，請手動更新"

  if [[ -f "$workspace/.env.bak" ]]; then
    echo "[FAIL] Expected no backup file when .env did not previously exist"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    rm -rf "$workspace"
    return 1
  fi

  PASS_COUNT=$((PASS_COUNT + 1))
  rm -rf "$workspace"
}

test_setup_env_preserves_existing_secrets_and_backup() {
  local workspace
  workspace="$(setup_workspace)"
  cat > "$workspace/.env" <<'EOF'
DISCORD_BOT_TOKEN=secret-token
AI_SERVICE_API_KEY=secret-key
ECPAY_MERCHANT_ID=
ECPAY_HASH_KEY=
ECPAY_HASH_IV=
PRODUCT_FULFILLMENT_SIGNING_SECRET=
APP_PUBLIC_DOMAIN=old.example.com
CADDY_ACME_EMAIL=old@example.com
APP_PUBLIC_BASE_URL=https://old.example.com/base
ECPAY_RETURN_URL=https://override.example.com/old/callback
ECPAY_CALLBACK_PATH=/ecpay/callback
EXTRA_OLD=legacy
EOF

  local original_env="$workspace/original.env"
  cp "$workspace/.env" "$original_env"

  local stdout_file="$workspace/stdout.log"
  local stderr_file="$workspace/stderr.log"

  run_setup_script \
    "$workspace" \
    $'https://new.example.com/store/\nops@example.com\nn\ncallback.example.com/pay/result/\n' \
    "$stdout_file" \
    "$stderr_file"

  assert_contains "$workspace/.env" "DISCORD_BOT_TOKEN=secret-token"
  assert_contains "$workspace/.env" "AI_SERVICE_API_KEY=secret-key"
  assert_contains "$workspace/.env" "APP_PUBLIC_DOMAIN=new.example.com"
  assert_contains "$workspace/.env" "CADDY_ACME_EMAIL=ops@example.com"
  assert_contains "$workspace/.env" "APP_PUBLIC_BASE_URL=https://new.example.com/store"
  assert_contains "$workspace/.env" "ECPAY_RETURN_URL=https://callback.example.com/pay/result"
  assert_not_contains "$workspace/.env" "EXTRA_OLD=legacy"
  assert_contains "$stdout_file" "已保留顯式 ECPAY_RETURN_URL override"
  assert_file_equals "$workspace/.env.bak" "$original_env"

  PASS_COUNT=$((PASS_COUNT + 1))
  rm -rf "$workspace"
}

test_setup_env_cancellation_keeps_existing_file() {
  local workspace
  workspace="$(setup_workspace)"
  cat > "$workspace/.env" <<'EOF'
DISCORD_BOT_TOKEN=secret-token
AI_SERVICE_API_KEY=secret-key
APP_PUBLIC_DOMAIN=old.example.com
CADDY_ACME_EMAIL=old@example.com
APP_PUBLIC_BASE_URL=https://old.example.com
ECPAY_RETURN_URL=
ECPAY_CALLBACK_PATH=/ecpay/callback
EOF

  local original_env="$workspace/original.env"
  cp "$workspace/.env" "$original_env"
  local stdout_file="$workspace/stdout.log"
  local stderr_file="$workspace/stderr.log"

  set +e
  run_setup_script "$workspace" $'new.example.com\n' "$stdout_file" "$stderr_file"
  local rc=$?
  set -e

  if [[ $rc -eq 0 ]]; then
    echo "[FAIL] Expected setup-env cancellation to return non-zero"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    rm -rf "$workspace"
    return 1
  fi

  assert_file_equals "$workspace/.env" "$original_env"
  if [[ -f "$workspace/.env.bak" ]]; then
    echo "[FAIL] Expected no backup file when setup-env is cancelled before write"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    rm -rf "$workspace"
    return 1
  fi
  assert_contains "$stdout_file" "流程已取消，未修改 .env"

  PASS_COUNT=$((PASS_COUNT + 1))
  rm -rf "$workspace"
}

test_setup_env_requires_tty_by_default() {
  local workspace
  workspace="$(setup_workspace)"
  local stdout_file="$workspace/stdout.log"
  local stderr_file="$workspace/stderr.log"

  set +e
  run_setup_script "$workspace" $'pay.example.com\nops@example.com\n\n' "$stdout_file" "$stderr_file" 0
  local rc=$?
  set -e

  if [[ $rc -eq 0 ]]; then
    echo "[FAIL] Expected setup-env without TTY to fail"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    rm -rf "$workspace"
    return 1
  fi

  assert_contains "$stdout_file" "setup-env 需要在互動式 TTY 終端機中執行"

  PASS_COUNT=$((PASS_COUNT + 1))
  rm -rf "$workspace"
}

test_update_env_preserves_sync_semantics() {
  local workspace
  workspace="$(setup_workspace)"
  cat > "$workspace/.env" <<'EOF'
DISCORD_BOT_TOKEN=secret-token
AI_SERVICE_API_KEY=secret-key
ECPAY_MERCHANT_ID=
ECPAY_HASH_KEY=
ECPAY_HASH_IV=
PRODUCT_FULFILLMENT_SIGNING_SECRET=
APP_PUBLIC_BASE_URL=https://old.example.com
ECPAY_RETURN_URL=
ECPAY_CALLBACK_PATH=/ecpay/callback
EXTRA_OLD=legacy
EOF

  local original_env="$workspace/original.env"
  cp "$workspace/.env" "$original_env"
  local stdout_file="$workspace/stdout.log"
  local stderr_file="$workspace/stderr.log"

  run_update_script "$workspace" "$stdout_file" "$stderr_file"

  assert_contains "$workspace/.env" "DISCORD_BOT_TOKEN=secret-token"
  assert_contains "$workspace/.env" "AI_SERVICE_API_KEY=secret-key"
  assert_contains "$workspace/.env" "APP_PUBLIC_DOMAIN="
  assert_contains "$workspace/.env" "CADDY_ACME_EMAIL="
  assert_not_contains "$workspace/.env" "EXTRA_OLD=legacy"
  assert_file_equals "$workspace/.env.bak" "$original_env"
  assert_contains "$stdout_file" "將刪除 1 個過時變數"
  assert_contains "$stdout_file" "將添加 2 個新變數"

  PASS_COUNT=$((PASS_COUNT + 1))
  rm -rf "$workspace"
}

main() {
  test_setup_env_creates_and_normalizes_values
  test_setup_env_preserves_existing_secrets_and_backup
  test_setup_env_cancellation_keeps_existing_file
  test_setup_env_requires_tty_by_default
  test_update_env_preserves_sync_semantics

  if [[ $FAIL_COUNT -gt 0 ]]; then
    echo "[RESULT] PASS=$PASS_COUNT FAIL=$FAIL_COUNT"
    exit 1
  fi

  echo "[RESULT] PASS=$PASS_COUNT FAIL=0"
}

main "$@"
