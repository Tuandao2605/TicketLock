import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';

const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', { url: 'http://localhost/' });
globalThis.window = dom.window;
globalThis.document = dom.window.document;
Object.defineProperty(globalThis, 'navigator', { value: dom.window.navigator, configurable: true });
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const React = await import('react');
const { createRoot } = await import('react-dom/client');
const vite = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
const { default: SiteHeader } = await vite.ssrLoadModule('/src/SiteHeader.jsx');
const { default: EventExplorer } = await vite.ssrLoadModule('/src/EventExplorer.jsx');
const { default: Profile } = await vite.ssrLoadModule('/src/Profile.jsx');
const { default: Dashboard } = await vite.ssrLoadModule('/src/Dashboard.jsx');
const { default: EventsPage } = await vite.ssrLoadModule('/src/EventsPage.jsx');
const { resolvePage } = await vite.ssrLoadModule('/src/routes.js');
const root = createRoot(document.getElementById('root'));
const originalFetch = globalThis.fetch;
const response = body => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } });
const flush = async () => { await React.act(async () => { await new Promise(resolve => setTimeout(resolve, 0)); }); };
const render = async element => { await React.act(async () => root.render(element)); await flush(); };
const click = async button => { assert.ok(button); await React.act(async () => button.dispatchEvent(new dom.window.MouseEvent('click', { bubbles: true }))); await flush(); };
const buttonWithText = text => [...document.querySelectorAll('button')].find(b => b.textContent.includes(text));

test('three pages have real navigation links and current-page semantics', async () => {
  for (const current of ['home', 'events', 'profile']) {
    await render(React.createElement(SiteHeader, { current, session: { status: 'guest' } }));
    const links = [...document.querySelectorAll('nav a')];
    assert.deepEqual(links.map(a => a.getAttribute('href')), ['/', '/events', '/profile']);
    assert.ok(links.every(a => a.tabIndex === 0));
    assert.equal(links.filter(a => a.getAttribute('aria-current') === 'page').length, 1);
    assert.equal(links.find(a => a.getAttribute('aria-current') === 'page').textContent, { home: 'Khám phá', events: 'Sự kiện', profile: 'Tài khoản' }[current]);
  }
});

test('events sidebar keeps navigation links and marks the current page', async () => {
  await render(React.createElement(SiteHeader, { current: 'events', layout: 'sidebar', session: { status: 'guest' } }));
  const sidebar = document.querySelector('aside.events-sidebar');
  assert.ok(sidebar);
  assert.deepEqual([...sidebar.querySelectorAll('nav a')].map(a => a.getAttribute('href')), ['/', '/events', '/profile']);
  assert.equal(sidebar.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/events');
  assert.equal(sidebar.querySelector('.events-side-account').getAttribute('href'), '/login');
});

test('pages keep distinct content and consistent navigation', async () => {
  globalThis.fetch = async path => path === '/me' || path === '/api/users/me'
    ? new Response('', { status: 401 }) : response({ items: [], totalElements: 0 });
  for (const [Component, route, heading] of [[Dashboard, '/', /Sự kiện giữa/], [EventsPage, '/events', /Khám phá sự kiện/], [Profile, '/profile', /Hồ sơ cá nhân/]]) {
    await render(React.createElement(Component));
    assert.equal(document.querySelector('aside nav a[aria-current="page"]').getAttribute('href'), route);
    assert.match(document.querySelector('main h1').textContent, heading);
    assert.deepEqual([...document.querySelectorAll('aside nav a')].map(a => a.getAttribute('href')), ['/', '/events', '/profile']);
  }
  assert.equal(resolvePage('/events/'), '/events');
  assert.equal(resolvePage('/profile'), '/profile');
  assert.equal(resolvePage('/unknown'), 'not-found');
});

test('event/show selection uses IDs and resets when switching shows', async () => {
  const calls = [];
  globalThis.fetch = async (path, options) => {
    calls.push(path);
    assert.equal(options.credentials, 'same-origin');
    if (path.startsWith('/api/v1/events?')) return response({ items: [{ id: 'event-1', name: 'Galaxy', description: '' }], totalElements: 1 });
    if (path.startsWith('/api/v1/events/event-1/shows')) return response({ items: [{ id: 'show-1', startsAt: '2099-01-01T12:00:00Z', venue: 'Hall One' }, { id: 'show-2', startsAt: '2099-01-02T12:00:00Z', venue: 'Hall Two' }], totalElements: 2 });
    if (path === '/api/v1/shows/show-1') return response({ venue: 'Hall One', startsAt: '2099-01-01T12:00:00Z' });
    if (path === '/api/v1/shows/show-2') return response({ venue: 'Hall Two', startsAt: '2099-01-02T12:00:00Z' });
    if (path.endsWith('/seats')) return response({ capturedAt: '2099-01-01T00:00:00Z', seats: [
      { showSeatId: path.includes('show-1') ? 'seat-first' : 'seat-second', label: 'A1', status: 'AVAILABLE', price: { amount: 150000, currency: 'VND' } },
      { showSeatId: 'seat-held', label: 'A2', status: 'HELD', price: { amount: 150000, currency: 'VND' } },
      { showSeatId: 'seat-sold', label: 'A3', status: 'SOLD', price: { amount: 150000, currency: 'VND' } },
    ] });
    throw new Error(`Unexpected route: ${path}`);
  };
  await render(React.createElement(EventExplorer));
  await click(buttonWithText('Galaxy'));
  const showButtons = [...document.querySelectorAll('button')].filter(b => b.textContent.includes('Hall'));
  assert.equal(showButtons.length, 2);
  await click(showButtons[0]);
  const seats = [...document.querySelectorAll('.seat-grid button')];
  assert.equal(seats.length, 3);
  assert.equal(seats[1].disabled, true);
  assert.equal(seats[2].disabled, true);
  await click(seats[0]);
  assert.equal(seats[0].getAttribute('aria-pressed'), 'true');
  assert.match(document.body.textContent, /Đã chọn 1 ghế/);
  assert.match(document.body.textContent, /Giá ghế tham khảo/);
  assert.match(document.body.textContent, /Ghế A1:.*150\.000/);
  assert.match(document.body.textContent, /Tổng tham khảo:.*150\.000/);
  assert.equal(buttonWithText('Đặt ghế').disabled, true);
  assert.match(document.body.textContent, /Chức năng đặt ghế đang được phát triển/);
  await click(showButtons[1]);
  assert.ok(calls.includes('/api/v1/shows/show-1/seats'));
  assert.ok(calls.includes('/api/v1/shows/show-2/seats'));
  assert.equal(document.querySelector('.seat-grid button').getAttribute('aria-pressed'), 'false');
  assert.match(document.body.textContent, /chưa giữ chỗ hoặc đặt vé/);
});

test('a late response from the previous show cannot replace the current snapshot', async () => {
  await React.act(async () => root.render(React.createElement('div')));
  const delayed = [];
  globalThis.fetch = async path => {
    if (path.startsWith('/api/v1/events?')) return response({ items: [{ id: 'event-1', name: 'Race Event' }], totalElements: 1 });
    if (path.startsWith('/api/v1/events/event-1/shows')) return response({ items: [{ id: 'slow', startsAt: '2099-01-01T12:00:00Z', venue: 'Slow' }, { id: 'fast', startsAt: '2099-01-02T12:00:00Z', venue: 'Fast' }], totalElements: 2 });
    if (path.startsWith('/api/v1/shows/slow')) return new Promise(resolve => delayed.push([path, resolve]));
    if (path === '/api/v1/shows/fast') return response({ venue: 'Fast', startsAt: '2099-01-02T12:00:00Z' });
    if (path === '/api/v1/shows/fast/seats') return response({ capturedAt: '2099-01-01T00:00:00Z', seats: [{ showSeatId: 'fast-seat', label: 'F1', status: 'AVAILABLE', price: { amount: 1, currency: 'VND' } }] });
    throw new Error(`Unexpected route: ${path}`);
  };
  await render(React.createElement(EventExplorer));
  await click(buttonWithText('Race Event'));
  await click(buttonWithText('Slow'));
  await click(buttonWithText('Fast'));
  assert.ok(document.querySelector('.seat-grid button').textContent.includes('F1'));
  assert.equal(delayed.length, 2);
  await React.act(async () => {
    for (const [path, resolve] of delayed) resolve(response(path.endsWith('/seats')
      ? { capturedAt: '2099-01-01T00:00:00Z', seats: [{ showSeatId: 'slow-seat', label: 'S1', status: 'AVAILABLE', price: { amount: 1, currency: 'VND' } }] }
      : { venue: 'Slow', startsAt: '2099-01-01T12:00:00Z' }));
  });
  await flush();
  assert.equal(document.querySelector('.seat-grid button').textContent, 'F1');
});

test('event explorer renders empty and forbidden states', async () => {
  await React.act(async () => root.render(React.createElement('div')));
  globalThis.fetch = async () => response({ items: [], totalElements: 0 });
  await render(React.createElement(EventExplorer));
  assert.match(document.body.textContent, /Chưa có sự kiện đang mở/);
  await React.act(async () => root.render(React.createElement('div')));
  globalThis.fetch = async () => new Response('', { status: 403 });
  await render(React.createElement(EventExplorer));
  assert.match(document.querySelector('[role="alert"]').textContent, /không có quyền/);
});

test('account page handles guest and authenticated session', async () => {
  globalThis.fetch = async () => new Response('', { status: 401 });
  await render(React.createElement(Profile));
  assert.match(document.body.textContent, /Bạn chưa đăng nhập/);
  assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/profile');
  globalThis.fetch = async () => response({ id: 'fixture-id', email: 'fixture@example.test', role: 'CUSTOMER', createdAt: '2025-01-01T00:00:00Z' });
  await React.act(async () => root.render(React.createElement('div')));
  await render(React.createElement(Profile));
  assert.match(document.body.textContent, /fixture@example.test/);
  assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/profile');
  globalThis.fetch = async () => new Response('', { status: 403 });
  await React.act(async () => root.render(React.createElement('div')));
  await render(React.createElement(Profile));
  assert.match(document.querySelector('[role="alert"]').textContent, /không có quyền/);
});

test.after(async () => {
  await React.act(async () => root.unmount());
  globalThis.fetch = originalFetch;
  await vite.close();
  dom.window.close();
});
