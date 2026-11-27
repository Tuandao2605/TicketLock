const pages = new Set(['/', '/events', '/profile', '/login', '/register']);

export function resolvePage(pathname) {
  const normalized = pathname.replace(/\/+$/, '') || '/';
  return pages.has(normalized) ? normalized : 'not-found';
}
