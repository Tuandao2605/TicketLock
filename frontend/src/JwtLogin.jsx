import React, { useRef, useState } from 'react';

function createClient() {
  let tokens = null;
  let refresh = null;
  async function post(path, body) {
    const response = await fetch(path, { method: 'POST', credentials: 'omit', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    if (!response.ok) throw new Error('Đăng nhập hoặc phiên JWT không hợp lệ. Kiểm tra profile jwt và thử lại.');
    return response;
  }
  return {
    async login(email, password) { tokens = await (await post('/api/token/login', { email, password })).json(); },
    async profile() {
      if (!tokens) throw new Error('Vui lòng đăng nhập lại.');
      const request = (token) => fetch('/api/stateless/me', { credentials: 'omit', cache: 'no-store', headers: { Authorization: `Bearer ${token}` } });
      const used = tokens.accessToken;
      let response = await request(used);
      if (response.status === 401) {
        if (tokens.accessToken === used) {
          if (!refresh) refresh = post('/api/token/refresh', { refreshToken: tokens.refreshToken }).then((r) => r.json()).then((data) => { tokens = data; }).catch((e) => { tokens = null; throw e; }).finally(() => { refresh = null; });
          await refresh;
        }
        response = await request(tokens.accessToken);
      }
      if (!response.ok) throw new Error('Không đọc được thông tin JWT.');
      return response.json();
    },
    async logout() {
      if (refresh) await refresh;
      const old = tokens; tokens = null;
      if (old) await post('/api/token/logout', { refreshToken: old.refreshToken });
    },
  };
}

export default function JwtLogin() {
  const client = useRef(null);
  if (!client.current) client.current = createClient();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [profile, setProfile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function run(action) {
    if (busy) return;
    setBusy(true); setError('');
    try { await action(); } catch (e) { setError(e instanceof TypeError ? 'Không kết nối được backend.' : e.message); }
    finally { setBusy(false); }
  }
  return <main><p className="brand">TICKETLOCK</p><h1>Đăng nhập JWT</h1>
    {profile ? <><p>Mã người dùng: {profile.userId}</p><p>Vai trò: {profile.roles.join(', ')}</p>
      <button disabled={busy} onClick={() => run(async () => setProfile(await client.current.profile()))}>Kiểm tra phiên</button>
      <button disabled={busy} onClick={() => run(async () => { try { await client.current.logout(); } finally { setProfile(null); } })}>Đăng xuất</button></> :
      <form onSubmit={(e) => { e.preventDefault(); run(async () => { await client.current.login(email, password); setPassword(''); setProfile(await client.current.profile()); }); }}>
        <label htmlFor="jwt-email">Email</label><input id="jwt-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <label htmlFor="jwt-password">Mật khẩu</label><input id="jwt-password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        <button disabled={busy}>Đăng nhập</button></form>}
    {error && <p role="alert" className="error">{error}</p>}
    <p>Phiên JWT chỉ tồn tại trong tab này và kết thúc khi tải lại trang.</p><p><a href="/login">Đăng nhập bằng session</a></p>
  </main>;
}
