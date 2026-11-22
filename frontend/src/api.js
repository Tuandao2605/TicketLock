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
