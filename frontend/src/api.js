export async function readJson(response) {
  if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) {
    throw new Error('Định dạng phản hồi từ máy chủ không hợp lệ. Vui lòng thử lại sau.');
  }
  try {
    return await response.json();
  } catch {
    throw new Error('Dữ liệu từ máy chủ không đúng định dạng.');
  }
}

export async function getCsrf() {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
  if (!response.ok) {
    if (response.status >= 500) {
      throw new Error('Hệ thống đang bảo trì hoặc mất kết nối máy chủ. Vui lòng thử lại sau.');
    }
    throw new Error('Phiên bảo mật không hợp lệ. Vui lòng tải lại trang và thử lại.');
  }
  const metadata = await readJson(response);
  const cookie = document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${metadata.cookieName}=`));
  if (!cookie) throw new Error('Không tìm thấy phiên bảo mật. Vui lòng làm mới trang và thử lại.');
  return { headerName: metadata.headerName, token: decodeURIComponent(cookie.slice(metadata.cookieName.length + 1)) };
}
