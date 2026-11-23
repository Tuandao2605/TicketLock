import React, { useEffect, useState } from 'react';
import { readJson } from './api';

const Icon = ({ name }) => {
  const paths = {
    compass: <><circle cx="12" cy="12" r="9"/><path d="m15.8 8.2-2.1 5.5-5.5 2.1 2.1-5.5z"/></>,
    ticket: <><path d="M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4z"/><path d="M12 7v2m0 3v1m0 3v1"/></>,
    calendar: <><rect x="4" y="6" width="16" height="14" rx="2"/><path d="M8 4v4m8-4v4M4 10h16"/></>,
    user: <><circle cx="12" cy="8" r="3"/><path d="M5 20a7 7 0 0 1 14 0"/></>,
    arrow: <><path d="M5 12h14m-5-5 5 5-5 5"/></>,
    spark: <><path d="M12 3 14 9l6 3-6 2-2 7-2-7-6-2 6-3z"/></>,
  };
  return <svg className="dash-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
};

export default function Dashboard() {
  const [session, setSession] = useState({ status: 'loading' });
  useEffect(() => {
    const controller = new AbortController();
    fetch('/me', { credentials: 'same-origin', headers: { Accept: 'application/json' }, signal: controller.signal })
      .then(async response => {
        if (response.status === 401) return setSession({ status: 'guest' });
        if (!response.ok) throw new Error('unavailable');
        setSession({ status: 'signed-in', user: await readJson(response) });
      })
      .catch(error => { if (error.name !== 'AbortError') setSession({ status: 'offline' }); });
    return () => controller.abort();
  }, []);

  const signedIn = session.status === 'signed-in';
  const isAdmin = signedIn && session.user.authorities?.includes('ROLE_ADMIN');
  return <div className="dashboard-shell">
    <header className="dashboard-header">
      <a href="/" className="dashboard-logo"><span className="logo-mark">✦</span><span>TICKET<span>LOCK</span></span></a>
      <nav aria-label="Điều hướng chính" className="dashboard-nav"><a className="nav-active" href="#kham-pha">{isAdmin ? 'Tổng quan' : 'Khám phá'}</a><a href="#su-kien">{isAdmin ? 'Quản lý' : 'Sự kiện'}</a><a href="#tai-khoan">Tài khoản</a></nav>
      <a className="header-account" href={signedIn ? '/profile' : '/login'}><Icon name="user"/><span className="account-name" title={signedIn ? session.user.username : undefined}>{signedIn ? session.user.username : 'Đăng nhập'}</span><span className="account-arrow">↗</span></a>
    </header>

    <main className="dashboard-main" id="kham-pha">
      <section className="hero">
        <div className="hero-content"><div className="eyebrow"><span className="status-dot"/> {isAdmin ? 'KHÔNG GIAN QUẢN TRỊ' : 'MỘT VŨ TRỤ TRẢI NGHIỆM'}</div>
          <p className="hero-kicker">{isAdmin ? 'TICKETLOCK / ADMIN' : 'ĐIỀU TUYỆT VỜI ĐANG CHỜ BẠN'}</p>
          <h1>{isAdmin ? <>Không gian <em>quản trị</em><br/>TicketLock</> : <>Chạm tới <em>khoảnh khắc</em><br/>thuộc về bạn</>}<span className="hero-star"> ✦</span></h1>
          <p className="hero-description">{isAdmin ? 'Chào mừng bạn trở lại. Theo dõi các khu vực quản lý và chuẩn bị cho những sự kiện tiếp theo.' : 'Từ những đêm nhạc rực rỡ đến những trải nghiệm khó quên. Hành trình tiếp theo của bạn bắt đầu tại đây.'}</p>
          <div className="hero-actions"><a className="dash-button primary" href="#su-kien">{isAdmin ? 'Xem khu vực quản lý' : 'Khám phá sự kiện'} <Icon name="arrow"/></a><a className="dash-button secondary" href={signedIn ? '/profile' : '/register'}>{signedIn ? 'Tài khoản của tôi' : 'Tạo tài khoản'}</a></div>
        </div>
        <div className="hero-side" aria-hidden="true"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="orbit-core"><span>✦</span><small>YOUR NEXT<br/>EXPERIENCE</small></div><span className="orbit-label orbit-label-top">A LITTLE CLOSER TO MAGIC</span><span className="orbit-label orbit-label-bottom">✳ &nbsp; FIND YOUR MOMENT</span></div>
        <div className="hero-bottom"><span>SCROLL ĐỂ KHÁM PHÁ</span><span>✳ &nbsp; TICKETLOCK / EXPLORE</span></div>
      </section>

      <section className="discover-section" id="su-kien"><div className="section-heading"><div><p className="section-label">{isAdmin ? 'QUẢN TRỊ' : 'DÀNH CHO BẠN'} <span>✦</span></p><h2>{isAdmin ? 'Tổng quan quản lý' : 'Chuyến đi bắt đầu từ đây'}</h2><p>{isAdmin ? 'Các khu vực quản lý dành cho tài khoản ADMIN.' : 'Một nơi để khám phá và lưu giữ những trải nghiệm của riêng bạn.'}</p></div><span className="section-number">{isAdmin ? '01 / SỰ KIỆN' : '01 / KHÁM PHÁ'}</span></div>
        <div className="feature-grid">
          <article className="feature-card feature-main"><div className="feature-top"><span className="feature-icon"><Icon name="compass"/></span><span className="feature-index">{isAdmin ? '01 / SỰ KIỆN' : '01 / KHÁM PHÁ'}</span></div><div><p className="feature-overline">ĐIỀU GÌ SẮP TỚI?</p><h3>{isAdmin ? 'Quản lý sự kiện' : 'Khám phá sự kiện'}</h3><p>{isAdmin ? 'Khu vực tạo và quản lý sự kiện đang được phát triển.' : 'Những trải nghiệm mới đang được chuẩn bị. Hãy quay lại để khám phá khi sự kiện được mở bán.'}</p><span className="feature-note"><span className="status-dot"/> ĐANG CHUẨN BỊ</span></div></article>
          <article className="feature-card"><div className="feature-top"><span className="feature-icon warm"><Icon name="ticket"/></span><span className="feature-index">{isAdmin ? '02 / ĐẶT VÉ' : '02 / VÉ CỦA BẠN'}</span></div><div><p className="feature-overline">HÀNH TRÌNH CỦA BẠN</p><h3>{isAdmin ? 'Quản lý đặt vé' : 'Vé của tôi'}</h3><p>{isAdmin ? 'Khu vực theo dõi đơn đặt vé đang được phát triển.' : 'Không gian dành cho những sự kiện bạn sẽ tham gia.'}</p><span className="feature-note muted">SẮP RA MẮT <span>↗</span></span></div></article>
          <article className="feature-card" id="tai-khoan"><div className="feature-top"><span className="feature-icon blue"><Icon name="user"/></span><span className="feature-index">03 / TÀI KHOẢN</span></div><div><p className="feature-overline">CHÀO MỪNG BẠN</p><h3>{signedIn ? session.user.username : 'Bắt đầu hành trình'}</h3><p>{signedIn ? 'Chào mừng bạn trở lại TicketLock.' : session.status === 'offline' ? 'Chưa thể kết nối máy chủ. Bạn có thể thử lại sau.' : session.status === 'loading' ? 'Đang kiểm tra tài khoản của bạn…' : 'Đăng nhập để chuẩn bị cho trải nghiệm tiếp theo.'}</p><a className="feature-link" href={signedIn ? '/profile' : '/login'}>{signedIn ? 'XEM TÀI KHOẢN' : 'ĐĂNG NHẬP'} <span>↗</span></a></div></article>
        </div>
      </section>
      <footer className="dashboard-footer"><span>✦ &nbsp; TICKETLOCK</span><span>MỖI KHOẢNH KHẮC ĐỀU ĐÁNG NHỚ.</span><span>MADE FOR THE MOMENT &nbsp; ✳</span></footer>
    </main>
  </div>;
}
