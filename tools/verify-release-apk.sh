#!/usr/bin/env bash
# 仅核验正式发行 APK，不接受调试签名；不访问或生成任何密钥。
set -euo pipefail
export LC_ALL=C
if [[ $# != 4 || ! $3 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ || ! $4 =~ ^[0-9]+$ ]]; then
  echo '用法：bash tools/verify-release-apk.sh APK Build-Tools目录 versionName versionCode' >&2
  exit 2
fi
apk=$1
tools=$2
# 直接调用 SDK 内的 apksigner，Windows 和 Linux 共用同一入口。
certs=$(java -jar "$tools/lib/apksigner.jar" verify --verbose --print-certs "$apk")
if ! awk -v expected=d18d02ab458741b3b9cda763ff8c7771988cd6378681ac7777e8b71d0e1358cb '
  { sub(/\r$/, "") }
  /^Number of signers:/ { counts++; if ($0 != "Number of signers: 1") bad = 1 }
  /^Signer #[0-9]+ certificate SHA-256 digest:/ {
    digests++; if (tolower($0) != "signer #1 certificate sha-256 digest: " expected) bad = 1
  }
  END { exit (bad || counts != 1 || digests != 1) }
' <<< "$certs"; then
  echo '拒绝发布：APK 必须恰好由项目固定的发行证书签名。' >&2
  exit 1
fi

badging=$("$tools/aapt2" dump badging "$apk")
if ! awk -v name="$3" -v code="$4" '
  BEGIN { q = sprintf("%c", 39) }
  { sub(/\r$/, "") }
  /^package: / {
    packages++
    for (i = 2; i <= NF; i++) {
      if ($i ~ /^name=/)        { ids++;   if ($i != "name=" q "com.privateplayer.media" q) bad = 1 }
      if ($i ~ /^versionName=/) { names++; if ($i != "versionName=" q name q) bad = 1 }
      if ($i ~ /^versionCode=/) { codes++; if ($i != "versionCode=" q code q) bad = 1 }
    }
  }
  /^native-code:/ { abis++; if (NF != 2 || $2 != q "arm64-v8a" q) bad = 1 }
  /^application-debuggable([[:space:]:]|$)/ { bad = 1 }
  END { exit (bad || packages != 1 || ids != 1 || names != 1 || codes != 1 || abis != 1) }
' <<< "$badging"; then
  echo '拒绝发布：APK 的应用 ID、版本号、ABI 或可调试属性不符合发行要求。' >&2
  exit 1
fi
printf '正式签名与包身份校验通过：com.privateplayer.media %s (%s)，仅 arm64-v8a，不可调试。\n' "$3" "$4"
