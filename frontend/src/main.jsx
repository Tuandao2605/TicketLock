import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { readJson } from './api';

async function getCsrf() {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error('Không lấy được CSRF token. Hãy thử lại.');
  return readJson(response);
}

function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [user, setUser] = useState(null);

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
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token },
        body: new URLSearchParams({ username: email, password }),
      });
      if (response.status === 401) throw new Error('Email hoặc mật khẩu không chính xác.');
      if (!response.ok) throw new Error('Không thể đăng nhập. Hãy thử lại.');
      setPassword('');
      const profile = await fetch('/me', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
      if (!profile.ok || profile.redirected) throw new Error('Không đọc được thông tin phiên. Hãy thử lại.');
      setUser(await readJson(profile));
    } catch (failure) {
      setError(failure instanceof TypeError ? 'Không kết nối được backend.' : failure.message);
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
      if (!response.ok) throw new Error('Chưa đăng xuất được. Hãy thử lại.');
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
        throw new Error('Mật khẩu tối đa 72 byte UTF-8.');
      }
      const csrf = await getCsrf();
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ email, password }),
      });
      const data = await readJson(response);
      if (!response.ok) throw new Error(data.message || 'Đăng ký thất bại. Hãy thử lại.');
      setMessage(data.message);
      setSuccess(true);
      setPassword('');
    } catch (error) {
      setMessage(error instanceof TypeError ? 'Không kết nối được server. Kiểm tra backend đã chạy.' : error.message);
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
        <small>Từ 8 ký tự, tối đa 72 byte UTF-8.</small>
        <button disabled={busy}>{busy ? 'Đang đăng ký…' : 'Đăng ký'}</button>
      </form>
      {message && <p role={success ? 'status' : 'alert'} className={success ? 'success' : 'error'}>{message}</p>}
      <p>Đã có tài khoản? <a href="/login">Đăng nhập</a></p>
    </main>
  );
}

createRoot(document.getElementById('root')).render(
  window.location.pathname === '/register' ? <Registration /> : <Login />,
);
