import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { readJson } from './api';
import JwtLogin from './JwtLogin';

async function getCsrf() {
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

function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [user, setUser] = useState(null);

  const username = user?.username;
  useEffect(() => {
    if (busy) return;
    let active = true;
    const controller = new AbortController();
    let pending = false;

    async function checkSession() {
      if (document.visibilityState === 'hidden' || pending) return;
      pending = true;
      try {
        const response = await fetch('/me', {
          credentials: 'same-origin',
          headers: { Accept: 'application/json' },
          cache: 'no-store',
          signal: controller.signal,
        });
        if (!active) return;
        if (response.status === 401) {
          setUser(null);
          if (username) setError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.');
          return;
        }
        if (!response.ok) throw new Error('Không thể kiểm tra trạng thái đăng nhập. Vui lòng thử lại.');
        const profile = await readJson(response);
        if (active) setUser(profile);
      } catch (failure) {
        if (active && failure.name !== 'AbortError') {
          setError('Mất kết nối với máy chủ. Vui lòng kiểm tra lại đường truyền mạng.');
        }
      } finally {
        pending = false;
      }
    }

    checkSession();
    window.addEventListener('focus', checkSession);
    document.addEventListener('visibilitychange', checkSession);
    return () => {
      active = false;
      controller.abort();
      window.removeEventListener('focus', checkSession);
      document.removeEventListener('visibilitychange', checkSession);
    };
  }, [busy, username]);

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const csrf = await getCsrf();
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ email, password }),
      });
      if (response.status === 401) throw new Error('Email hoặc mật khẩu không chính xác.');
      if (!response.ok) throw new Error('Đăng nhập không thành công. Vui lòng kiểm tra lại thông tin.');
      setPassword('');
      const profile = await fetch('/me', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
      if (!profile.ok || profile.redirected) throw new Error('Không thể tải thông tin tài khoản. Vui lòng thử lại.');
      setUser(await readJson(profile));
    } catch (failure) {
      setError(failure instanceof TypeError ? 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại mạng.' : failure.message);
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    setError('');
    try {
      // Lấy token mới vì Spring Security đổi CSRF token sau xác thực.
      const csrf = await getCsrf();
      const response = await fetch('/api/auth/logout', {
        method: 'POST', credentials: 'same-origin', headers: { [csrf.headerName]: csrf.token },
      });
      if (!response.ok) throw new Error('Đăng xuất không thành công. Vui lòng thử lại.');
      setUser(null);
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <p className="brand">TICKETLOCK</p>
      {user ? <>
        <h1>Đăng nhập thành công</h1>
        <p>Xin chào, <strong>{user.username}</strong>.</p>
        <button onClick={logout} disabled={busy}>{busy ? 'Đang xử lý…' : 'Đăng xuất'}</button>
      </> : <>
        <h1>Chào mừng trở lại</h1>
        <p>Đăng nhập để tiếp tục với TicketLock.</p>
        <form onSubmit={submit}>
          <label htmlFor="login-email">Email</label>
          <input id="login-email" type="email" autoComplete="username" required maxLength={254}
            value={email} onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="login-password">Mật khẩu</label>
          <div className="password-field">
            <input id="login-password" type={visible ? 'text' : 'password'} autoComplete="current-password" required
              value={password} onChange={(event) => setPassword(event.target.value)} />
            <button type="button" className="toggle" aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              aria-pressed={visible} onClick={() => setVisible(!visible)}>{visible ? 'Ẩn' : 'Hiện'}</button>
          </div>
          <button disabled={busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
        </form>
        <p>Chưa có tài khoản? <a href="/register">Đăng ký</a></p>
        <p><a href="/jwt-login">Đăng nhập bằng JWT</a></p>
      </>}
      {error && <p role="alert" className="error">{error}</p>}
    </main>
  );
}

function Registration() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [success, setSuccess] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setMessage('');
    setSuccess(false);
    try {
      if (new TextEncoder().encode(password).length > 72) {
        throw new Error('Mật khẩu tối đa 72 ký tự.');
      }
      const csrf = await getCsrf();
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ email, password }),
      });
      const data = await readJson(response);
      if (!response.ok) throw new Error(data.message || 'Đăng ký không thành công. Vui lòng thử lại.');
      setMessage(data.message);
      setSuccess(true);
      setPassword('');
    } catch (error) {
      setMessage(error instanceof TypeError ? 'Không thể kết nối đến máy chủ. Vui lòng thử lại sau.' : error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <p className="brand">TICKETLOCK</p>
      <h1>Tạo tài khoản</h1>
      <p>Đăng ký tài khoản khách hàng để bắt đầu đặt vé.</p>
      <form onSubmit={submit}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="email" required maxLength={254}
          value={email} onChange={(event) => setEmail(event.target.value)} />
        <label htmlFor="password">Mật khẩu</label>
        <input id="password" type="password" autoComplete="new-password" required minLength={8} maxLength={72}
          value={password} onChange={(event) => setPassword(event.target.value)} />
        <small>Độ dài từ 8 đến 72 ký tự.</small>
        <button disabled={busy}>{busy ? 'Đang đăng ký…' : 'Đăng ký'}</button>
      </form>
      {message && <p role={success ? 'status' : 'alert'} className={success ? 'success' : 'error'}>{message}</p>}
      <p>Đã có tài khoản? <a href="/login">Đăng nhập</a></p>
    </main>
  );
}

createRoot(document.getElementById('root')).render(
  window.location.pathname === '/register' ? <Registration /> : window.location.pathname === '/jwt-login' ? <JwtLogin /> : <Login />,
);
