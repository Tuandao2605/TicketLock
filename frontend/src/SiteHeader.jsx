import React, { useEffect, useState } from 'react';
import { readJson } from './api';

export function useSession() {
  const [session, setSession] = useState({ status: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    fetch('/me', { credentials: 'same-origin', headers: { Accept: 'application/json' }, signal: controller.signal })
      .then(async response => {
        if (response.status === 401) return setSession({ status: 'guest' });
        if (!response.ok) throw new Error('unavailable');
        const user = await readJson(response);
        if (!controller.signal.aborted) setSession({ status: 'signed-in', user });
      })
      .catch(error => { if (!controller.signal.aborted && error.name !== 'AbortError') setSession({ status: 'offline' }); });
    return () => controller.abort();
  }, []);
  return session;
}

const links = [
  { path: '/', label: 'Khám phá', key: 'home' },
  { path: '/events', label: 'Sự kiện', key: 'events' },
  { path: '/profile', label: 'Tài khoản', key: 'profile' },
];

export default function SiteHeader({ current, session, layout = 'top' }) {
  const signedIn = session.status === 'signed-in';
  const sidebar = layout === 'sidebar';
  const Container = sidebar ? 'aside' : 'header';
  return <Container className={sidebar ? 'events-sidebar' : 'dashboard-header'}>
    <a href="/" className="dashboard-logo"><span className="logo-mark">✦</span><span>ORBIT <span>TICKETS</span></span></a>
    <nav aria-label="Điều hướng chính" className={sidebar ? 'events-side-nav' : 'dashboard-nav'}>
      {links.map(link => <a key={link.key} href={link.path} className={current === link.key ? 'nav-active' : undefined} aria-current={current === link.key ? 'page' : undefined}>
        {sidebar && <svg className="side-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">{link.key === 'home' ? <><circle cx="12" cy="12" r="8"/><path d="m15 9-2 4-4 2 2-4 4-2Z"/></> : link.key === 'events' ? <><rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M8 3v4M16 3v4"/></> : <><circle cx="12" cy="8" r="3"/><path d="M5 20a7 7 0 0 1 14 0"/></>}</svg>}
        {link.label}
      </a>)}
    </nav>
    <a className={sidebar ? 'events-side-account' : 'header-account'} href={signedIn ? '/profile' : '/login'}>
      <svg className="dash-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" aria-hidden="true"><circle cx="12" cy="8" r="3"/><path d="M5 20a7 7 0 0 1 14 0"/></svg>
      <span className="account-name" title={signedIn ? session.user.username : undefined}>{signedIn ? session.user.username : 'Đăng nhập'}</span>
    </a>
  </Container>;
}
