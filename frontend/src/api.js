export async function readJson(response) {
  if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) {
    throw new Error('Server trả HTML thay vì JSON. Hãy kiểm tra backend mới đã chạy và proxy Vite đang hoạt động.');
  }
  try {
    return await response.json();
  } catch {
    throw new Error('Response JSON từ server không hợp lệ.');
  }
}
