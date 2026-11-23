import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { getCsrf, readJson } from './api';
import Profile from './Profile';
import Dashboard from './Dashboard';

function ShootingStarTrail() {
  useEffect(() => {
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const pointer = window.matchMedia('(pointer: fine)');
    let lastSparkle = 0;
    const particles = new Set();
    function onMove(event) {
      if (motion.matches || !pointer.matches || event.pointerType !== 'mouse') return;
      if (event.timeStamp - lastSparkle < 45) return;
      if (event.target instanceof Element && event.target.closest('input, textarea, [contenteditable="true"]')) return;
      lastSparkle = event.timeStamp;
      const sparkle = document.createElement('span');
      sparkle.className = 'shooting-star-sparkle';
      sparkle.setAttribute('aria-hidden', 'true');
      sparkle.style.left = `${event.clientX + (Math.random() - 0.5) * 12}px`;
      sparkle.style.top = `${event.clientY + (Math.random() - 0.5) * 12}px`;
      sparkle.addEventListener('animationend', () => { sparkle.remove(); particles.delete(sparkle); }, { once: true });
      particles.add(sparkle);
      document.body.appendChild(sparkle);
    }
    window.addEventListener('pointermove', onMove);
    return () => {
      window.removeEventListener('pointermove', onMove);
      particles.forEach((particle) => particle.remove());
    };
  }, []);
  return null;
}

function GravityStars() {
  useEffect(() => {
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const pointer = window.matchMedia('(pointer: fine)');
    if (motion.matches || !pointer.matches) return;

    const canvas = document.createElement('canvas');
    canvas.className = 'gravity-stars';
    canvas.setAttribute('aria-hidden', 'true');
    document.body.appendChild(canvas);
    const context = canvas.getContext('2d');
    if (!context) { canvas.remove(); return; }
    let stars = [];
    let frame = 0;
    let mouseX = -1000;
    let mouseY = -1000;

    function draw() {
      frame = 0;
      context.clearRect(0, 0, window.innerWidth, window.innerHeight);
      let moving = false;
      for (const star of stars) {
        const dx = mouseX - star.x;
        const dy = mouseY - star.y;
        const distance = Math.hypot(dx, dy);
        const pull = distance < 70 ? (1 - distance / 70) * 0.9 : 0;
        const targetX = star.x + dx * pull;
        const targetY = star.y + dy * pull;
        star.offsetX += (targetX - star.x - star.offsetX) * 0.18;
        star.offsetY += (targetY - star.y - star.offsetY) * 0.18;
        if (Math.abs(targetX - star.x - star.offsetX) > 0.08 || Math.abs(targetY - star.y - star.offsetY) > 0.08) moving = true;
        context.beginPath();
        context.fillStyle = `rgba(${star.color}, ${star.opacity})`;
        context.shadowColor = `rgb(${star.color})`;
        context.shadowBlur = distance < 70 ? 9 : 3;
        context.arc(star.x + star.offsetX, star.y + star.offsetY, star.size, 0, Math.PI * 2);
        context.fill();
      }
      if (moving) frame = requestAnimationFrame(draw);
    }
    function schedule() { if (!frame) frame = requestAnimationFrame(draw); }
    function resize() {
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(window.innerWidth * ratio);
      canvas.height = Math.round(window.innerHeight * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      const count = Math.min(750, Math.max(200, Math.floor(window.innerWidth * window.innerHeight / 2000)));
      const colors = ['255, 239, 255', '207, 174, 255', '160, 210, 255', '255, 170, 208', '255, 218, 160'];
      stars = Array.from({ length: count }, () => ({ x: Math.random() * window.innerWidth, y: Math.random() * window.innerHeight, offsetX: 0, offsetY: 0, size: 0.9 + Math.random() * 1.5, opacity: 0.65 + Math.random() * 0.35, color: colors[Math.floor(Math.random() * colors.length)] }));
      schedule();
    }
    function move(event) {
      if (event.pointerType !== 'mouse' || motion.matches) return;
      mouseX = event.clientX; mouseY = event.clientY; schedule();
    }
    function leave() { mouseX = -1000; mouseY = -1000; schedule(); }
    function motionChanged() { if (motion.matches) { canvas.style.display = 'none'; leave(); } else { canvas.style.display = ''; schedule(); } }
    resize();
    window.addEventListener('resize', resize);
    window.addEventListener('pointermove', move);
    document.documentElement.addEventListener('pointerleave', leave);
    motion.addEventListener('change', motionChanged);
    return () => {
      window.removeEventListener('resize', resize);
      window.removeEventListener('pointermove', move);
      document.documentElement.removeEventListener('pointerleave', leave);
      motion.removeEventListener('change', motionChanged);
      cancelAnimationFrame(frame);
      canvas.remove();
    };
  }, []);
  return null;
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
      await readJson(profile);
      window.location.assign('/');
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
  <><GravityStars /><ShootingStarTrail />{window.location.pathname === '/register' ? <Registration /> : window.location.pathname === '/login' ? <Login /> : window.location.pathname === '/profile' ? <Profile /> : <Dashboard />}</>,
);
