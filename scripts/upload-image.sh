#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-https://v0cs47dfe8.execute-api.us-east-1.amazonaws.com}"
IMAGE_PATH="${IMAGE_PATH:-images/tea1.jpg}"

if [[ ! -f "$IMAGE_PATH" ]]; then
  echo "File not found: $IMAGE_PATH" >&2
  exit 1
fi

case "${IMAGE_PATH##*.}" in
  jpg|jpeg|JPG|JPEG) CONTENT_TYPE="image/jpeg" ;;
  png|PNG) CONTENT_TYPE="image/png" ;;
  webp|WEBP) CONTENT_TYPE="image/webp" ;;
  *)
    echo "Unsupported file extension for $IMAGE_PATH" >&2
    exit 1
    ;;
esac

TMP_B64="$(mktemp)"
TMP_JSON="$(mktemp)"
TMP_RESPONSE="$(mktemp)"

cleanup() {
  rm -f "$TMP_B64" "$TMP_JSON" "$TMP_RESPONSE"
}
trap cleanup EXIT

base64 < "$IMAGE_PATH" | tr -d '\n' > "$TMP_B64"

printf '{"fileName":"%s","contentType":"%s","base64Data":"%s"}' \
  "$(basename "$IMAGE_PATH")" \
  "$CONTENT_TYPE" \
  "$(cat "$TMP_B64")" > "$TMP_JSON"

curl -sS -X POST "$API_URL/images" \
  -H "Content-Type: application/json" \
  --data-binary "@$TMP_JSON" | tee "$TMP_RESPONSE"

IMAGE_ID="$(sed -n 's/.*"imageId":"\([^"]*\)".*/\1/p' "$TMP_RESPONSE")"

if [[ -n "$IMAGE_ID" ]]; then
  echo
  echo "imageId=$IMAGE_ID"
  echo "Check status with:"
  echo "curl \"$API_URL/images/$IMAGE_ID\""
fi
