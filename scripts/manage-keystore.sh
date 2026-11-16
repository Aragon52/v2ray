#!/usr/bin/env bash
# ==============================================================================
# Netcon Android Keystore & 1-Key-Per-1-Account License Management Utility
# Compatible with app/build.gradle.kts signingConfigs.release:
#   KEYSTORE_PATH (defaults to ./my-upload-key.jks)
#   STORE_PASSWORD
#   KEY_PASSWORD
#   keyAlias = "upload"
# ==============================================================================
set -euo pipefail

KEYSTORE_FILE="${KEYSTORE_PATH:-my-upload-key.jks}"
KEY_ALIAS="upload"
HMAC_SECRET="NETCON-VIPER-XRAY-LICENSE-V1"
ALPHABET="23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
DEFAULT_ACCOUNT="arxdevmm@gmail.com"

usage() {
  cat <<EOF
Usage: bash scripts/manage-keystore.sh <command> [args]

Commands:
  generate-keystore [keystore_path]
      Generates a 4096-bit RSA release keystore (default: my-upload-key.jks)
      with keyAlias="upload" matching app/build.gradle.kts.

  export-github-secrets [keystore_path]
      Outputs the base64-encoded keystore string for GitHub Actions secret
      KEYSTORE_BASE64 along with instructions for STORE_PASSWORD and KEY_PASSWORD.

  verify-apk <apk_path>
      Computes SHA-256 checksum and verifies APK signature using apksigner/jarsigner.

  generate-license-key [account_email] [PLAN] [YEAR]
      Generates a 1-Key-Per-1-Account HMAC-SHA256 signed Netcon license key
      (NETCON-AAAA-PPPP-YYYY-SSSS) cryptographically locked to <account_email>.
      A key issued for one account email will be rejected by all other accounts.

  verify-license-key <account_email> <license_key>
      Verifies whether <license_key> is valid and bound to <account_email>.
EOF
}

normalize_account() {
  local raw="${1:-$DEFAULT_ACCOUNT}"
  echo "$raw" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]'
}

sanitize_block() {
  local raw="${1:-FULL}"
  local cleaned
  cleaned="$(echo "$raw" | tr '[:lower:]' '[:upper:]' | tr -cd 'A-Z0-9')"
  cleaned="${cleaned}XXXX"
  echo "${cleaned:0:4}"
}

hmac_to_four_chars() {
  local payload="$1"
  local bytes
  bytes="$(printf '%s' "$payload" | openssl dgst -sha256 -hmac "$HMAC_SECRET" -binary | od -An -tu1 | tr -s ' ')"
  local out=""
  local count=0
  for byte in $bytes; do
    if [ "$count" -ge 4 ]; then
      break
    fi
    local idx=$((byte % 32))
    out="${out}${ALPHABET:$idx:1}"
    count=$((count + 1))
  done
  echo "$out"
}

generate_license_key() {
  local account
  account="$(normalize_account "${1:-$DEFAULT_ACCOUNT}")"
  local b1
  local b2
  local b3
  b1="$(hmac_to_four_chars "ACCT:${account}")"
  b2="$(sanitize_block "${2:-FULL}")"
  b3="$(sanitize_block "${3:-2026}")"
  local sig
  sig="$(hmac_to_four_chars "${account}|${b1}-${b2}-${b3}")"
  echo "NETCON-${b1}-${b2}-${b3}-${sig}"
}

cmd="${1:-help}"
case "$cmd" in
  generate-keystore)
    target="${2:-$KEYSTORE_FILE}"
    if [ -f "$target" ]; then
      echo "[ERROR] Keystore already exists at: $target"
      echo "        Delete or move it first if you intend to rotate keys."
      exit 1
    fi
    STORE_PASS="${STORE_PASSWORD:-}"
    KEY_PASS="${KEY_PASSWORD:-}"
    if [ -z "$STORE_PASS" ]; then
      read -r -s -p "Enter new Keystore Password (min 6 chars): " STORE_PASS
      echo ""
    fi
    if [ -z "$KEY_PASS" ]; then
      KEY_PASS="$STORE_PASS"
    fi
    DN="${KEYSTORE_DN:-CN=Artesys Netcon Release, OU=Security Engineering, O=Artesys, L=Singapore, ST=SG, C=SG}"
    keytool -genkeypair \
      -v \
      -keystore "$target" \
      -storetype PKCS12 \
      -alias "$KEY_ALIAS" \
      -keyalg RSA \
      -keysize 4096 \
      -validity 10000 \
      -storepass "$STORE_PASS" \
      -keypass "$KEY_PASS" \
      -dname "$DN"
    chmod 600 "$target"
    echo "[OK] Generated release keystore: $target (alias: $KEY_ALIAS)"
    ;;

  export-github-secrets)
    target="${2:-$KEYSTORE_FILE}"
    if [ ! -f "$target" ]; then
      echo "[ERROR] Keystore file not found: $target"
      echo "        Run: bash scripts/manage-keystore.sh generate-keystore"
      exit 1
    fi
    b64_out="${target}.base64"
    base64 -w 0 "$target" > "$b64_out" 2>/dev/null || base64 "$target" | tr -d '\n' > "$b64_out"
    chmod 600 "$b64_out"
    echo "======================================================================"
    echo "GitHub Repository Secrets Setup (Settings -> Secrets -> Actions)"
    echo "======================================================================"
    echo "1. KEYSTORE_BASE64 : Saved to $b64_out (copy contents into secret)"
    echo "2. STORE_PASSWORD  : Your keystore password"
    echo "3. KEY_PASSWORD    : Your key password for alias '$KEY_ALIAS'"
    echo "======================================================================"
    ;;

  verify-apk)
    apk="${2:-}"
    if [ -z "$apk" ] || [ ! -f "$apk" ]; then
      echo "[ERROR] Provide a valid APK path: bash scripts/manage-keystore.sh verify-apk <path-to.apk>"
      exit 1
    fi
    sha256sum "$apk" > "${apk}.sha256"
    echo "[SHA-256] $(cat "${apk}.sha256")"
    if command -v apksigner >/dev/null 2>&1; then
      apksigner verify --verbose --print-certs "$apk"
    elif command -v jarsigner >/dev/null 2>&1; then
      jarsigner -verify -verbose -certs "$apk" | tail -n 20
    fi
    ;;

  generate-license-key)
    shift || true
    acct="${1:-$DEFAULT_ACCOUNT}"
    plan="${2:-FULL}"
    year="${3:-2026}"
    generate_license_key "$acct" "$plan" "$year"
    ;;

  verify-license-key)
    shift || true
    acct="$(normalize_account "${1:-}")"
    key="$(echo "${2:-}" | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]')"
    expected="$(generate_license_key "$acct" "FULL" "2026")"
    if [ "$key" = "$expected" ]; then
      echo "[VALID] Key $key is cryptographically bound to account: $acct"
    else
      echo "[REJECTED] Key $key is NOT valid for account: $acct"
      exit 1
    fi
    ;;

  *)
    usage
    ;;
esac
