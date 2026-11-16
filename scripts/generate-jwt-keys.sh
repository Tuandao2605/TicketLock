#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
key_dir="$project_root/backend/.jwt"
if [[ -e "$key_dir/private.pem" && -e "$key_dir/public.pem" ]]; then
  echo "Khóa JWT đã tồn tại. Giữ nguyên để token đã cấp tiếp tục hoạt động."
  exit 0
fi
if [[ -e "$key_dir/private.pem" || -e "$key_dir/public.pem" ]]; then
  echo "Thiếu một file trong cặp khóa JWT. Cần khôi phục cặp khóa trước khi chạy."
  exit 1
fi
umask 077
mkdir -p "$key_dir"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$key_dir/private.pem"
openssl pkey -in "$key_dir/private.pem" -pubout -out "$key_dir/public.pem"
echo "Đã tạo khóa JWT local trong backend/.jwt (Git bỏ qua)."
