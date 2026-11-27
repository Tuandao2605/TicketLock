import React, { useEffect, useState } from 'react';
import { getCsrf, readJson } from './api';

export default function Profile() {
  const [state, setState] = useState({ status: 'loading' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/users/me', { credentials: 'same-origin', headers: { Accept: 'application/json' }, cache: 'no-store', signal: controller.signal })
      .then(async response => {
        if (response.status === 401) { setState({ status: 'guest' }); return; }
        if (!response.ok) throw new Error('Không thể tải hồ sơ. Vui lòng thử lại.');
        setState({ status: 'ready', user: await readJson(response) });
      })
      .catch(failure => { if (failure.name !== 'AbortError') setState({ status: 'error', message: 'Không thể tải hồ sơ. Vui lòng kiểm tra kết nối và thử lại.' }); });
    return () => controller.abort();
  }, []);

  async function logout() {
    if (busy) return;
    setBusy(true); setError('');
    try {
      const csrf = await getCsrf();
      const response = await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token } });
      if (!response.ok) throw new Error('Đăng xuất không thành công. Vui lòng thử lại.');
      window.location.assign('/');
    } catch (failure) {
      setError(failure instanceof TypeError ? 'Không thể kết nối đến máy chủ.' : failure.message);
      setBusy(false);
    }
  }

  const user = state.user;
  const createdAt = user?.createdAt ? new Date(user.createdAt) : null;
  const date = createdAt && !Number.isNaN(createdAt.getTime()) ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'long' }).format(createdAt) : '—';
  return <div className="profile-shell">
    <header className="profile-header"><a href="/" className="dashboard-logo"><span className="logo-mark">✦</span><span>ORBIT <span>TICKETS</span></span></a><a className="profile-back" href="/">← Quay lại trang chủ</a></header>
    <main className="profile-main">
      <p className="profile-eyebrow">TÀI KHOẢN CỦA BẠN <span>✦</span></p>
      <h1>Hồ sơ cá nhân</h1>
      <p className="profile-intro">Thông tin tài khoản của bạn tại Orbit Tickets.</p>
      {state.status === 'loading' && <p role="status">Đang tải hồ sơ…</p>}
      {state.status === 'guest' && <div className="profile-message"><h2>Bạn chưa đăng nhập</h2><p>Đăng nhập để xem thông tin tài khoản của bạn.</p><a className="dash-button primary" href="/login">Đăng nhập →</a></div>}
      {state.status === 'error' && <div className="profile-message"><p className="error" role="alert">{state.message}</p><button type="button" onClick={() => window.location.reload()}>Thử lại</button></div>}
      {state.status === 'ready' && <section className="profile-card" aria-label="Thông tin tài khoản">
        <div className="profile-identity"><div className="profile-avatar" aria-hidden="true">{user.email?.charAt(0).toUpperCase() || '✦'}</div><div><span className="profile-label">TÀI KHOẢN ORBIT TICKETS</span><h2>{user.email}</h2><span className="profile-role">{user.role === 'ADMIN' ? 'Quản trị viên' : user.role === 'CUSTOMER' ? 'Khách hàng' : user.role}</span></div></div>
        <dl className="profile-details"><div><dt>Email</dt><dd>{user.email}</dd></div><div><dt>Vai trò</dt><dd>{user.role === 'ADMIN' ? 'Quản trị viên' : user.role === 'CUSTOMER' ? 'Khách hàng' : user.role}</dd></div><div><dt>Ngày tham gia</dt><dd>{date}</dd></div><div><dt>Mã tài khoản</dt><dd className="profile-id">{user.id}</dd></div></dl>
        <div className="profile-actions"><a className="profile-home" href="/">← Về trang chủ</a><button className="profile-logout" type="button" onClick={logout} disabled={busy}>{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button></div>
        {error && <p className="error" role="alert">{error}</p>}
      </section>}
    </main>
  </div>;
}
