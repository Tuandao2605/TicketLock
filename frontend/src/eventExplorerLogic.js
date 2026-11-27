import { readJson } from './api.js';

export async function getJson(path, signal) {
  const response = await fetch(path, { credentials: 'same-origin', headers: { Accept: 'application/json' }, cache: 'no-store', signal });
  if (response.status === 401) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
  if (response.status === 403) throw new Error('Bạn không có quyền xem nội dung này.');
  if (response.status === 404) throw new Error('Sự kiện hoặc suất diễn không còn khả dụng.');
  if (!response.ok) throw new Error('Không thể tải dữ liệu sự kiện. Vui lòng thử lại.');
  return readJson(response);
}

export function messageFor(error) {
  return error instanceof TypeError ? 'Không thể kết nối máy chủ. Vui lòng thử lại.' : error.message;
}

export function nextSelection(selected, seat) {
  if (seat.status !== 'AVAILABLE') return { selected, notice: '' };
  if (selected.includes(seat.showSeatId)) return { selected: selected.filter(id => id !== seat.showSeatId), notice: '' };
  if (selected.length >= 6) return { selected, notice: 'Bạn có thể chọn tối đa 6 ghế.' };
  return { selected: [...selected, seat.showSeatId], notice: '' };
}
