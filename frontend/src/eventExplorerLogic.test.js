import test from 'node:test';
import assert from 'node:assert/strict';
import { getJson, messageFor, nextSelection } from './eventExplorerLogic.js';

test('seat selection uses show-seat IDs and rejects unavailable seats', () => {
  const first = { showSeatId: 'show-seat-a', status: 'AVAILABLE' };
  assert.deepEqual(nextSelection([], first).selected, ['show-seat-a']);
  assert.deepEqual(nextSelection(['show-seat-a'], first).selected, []);
  for (const status of ['HELD', 'SOLD']) {
    assert.deepEqual(nextSelection([], { showSeatId: 'occupied', status }).selected, []);
  }
  const selected = ['1', '2', '3', '4', '5', '6'];
  const result = nextSelection(selected, { showSeatId: '7', status: 'AVAILABLE' });
  assert.deepEqual(result.selected, selected);
  assert.match(result.notice, /6 ghế/);
});

test('API requests use the session and handle 401, 403 and service errors', async () => {
  const original = globalThis.fetch;
  try {
    for (const [status, pattern] of [[401, /đăng nhập lại/], [403, /không có quyền/], [503, /thử lại/]]) {
      globalThis.fetch = async (path, options) => {
        assert.equal(path, '/api/v1/events');
        assert.equal(options.credentials, 'same-origin');
        return new Response('', { status });
      };
      await assert.rejects(getJson('/api/v1/events'), pattern);
    }
    globalThis.fetch = async () => new Response(JSON.stringify({ items: [] }), { headers: { 'Content-Type': 'application/json' } });
    assert.deepEqual(await getJson('/api/v1/events'), { items: [] });
    assert.match(messageFor(new TypeError('Failed to fetch')), /kết nối/);
  } finally { globalThis.fetch = original; }
});
