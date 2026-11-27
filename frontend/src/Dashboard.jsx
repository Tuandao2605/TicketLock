import React from 'react';
import SiteHeader, { useSession } from './SiteHeader';

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
  const session = useSession();
  const signedIn = session.status === 'signed-in';
  const isAdmin = signedIn && session.user.authorities?.includes('ROLE_ADMIN');
  return <div className="dashboard-shell events-shell">
    <SiteHeader current="home" session={session} layout="sidebar" />

    <main className="dashboard-main" id="kham-pha">
      <section className="hero">
        <div className="hero-content"><div className="eyebrow"><span className="status-dot"/> {isAdmin ? 'KHÔNG GIAN QUẢN TRỊ' : 'MỘT VŨ TRỤ TRẢI NGHIỆM'}</div>
          <p className="hero-kicker">{isAdmin ? 'ORBIT TICKETS / ADMIN' : 'VÉ ĐẾN NHỮNG VÌ SAO'}</p>
          <h1>{isAdmin ? <>Không gian <em>quản trị</em><br/>Orbit Tickets</> : <>Sự kiện giữa <em>ngàn sao</em><br/>đang chờ bạn</>}<span className="hero-star"> ✦</span></h1>
          <p className="hero-description">{isAdmin ? 'Chào mừng bạn trở lại. Theo dõi các khu vực quản lý và chuẩn bị cho những sự kiện tiếp theo.' : 'Khám phá những sự kiện lấy cảm hứng từ vũ trụ, thiên hà và bầu trời sao. Hành trình của bạn bắt đầu từ một tấm vé.'}</p>
          <div className="hero-actions"><a className="dash-button primary" href={isAdmin ? "#su-kien" : "/events"}>{isAdmin ? 'Xem khu vực quản lý' : 'Khám phá sự kiện'} <Icon name="arrow"/></a><a className="dash-button secondary" href={signedIn ? '/profile' : '/register'}>{signedIn ? 'Tài khoản của tôi' : 'Tạo tài khoản'}</a></div>
        </div>
        <div className="hero-side" aria-hidden="true"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="orbit-core"><span>✦</span><small>YOUR NEXT<br/>DESTINATION</small></div><span className="orbit-label orbit-label-top">BEYOND THE STARS</span><span className="orbit-label orbit-label-bottom">✳ &nbsp; EXPLORE THE GALAXY</span></div>
        <div className="hero-bottom"><span>SCROLL ĐỂ KHÁM PHÁ</span><span>✳ &nbsp; ORBIT TICKETS / EXPLORE</span></div>
      </section>

      <section className="discover-section" id="su-kien"><div className="section-heading"><div><p className="section-label">{isAdmin ? 'QUẢN TRỊ' : 'DÀNH CHO BẠN'} <span>✦</span></p><h2>{isAdmin ? 'Tổng quan quản lý' : 'Chuyến đi bắt đầu từ đây'}</h2><p>{isAdmin ? 'Các khu vực quản lý dành cho tài khoản ADMIN.' : 'Một điểm hẹn cho những sự kiện và trải nghiệm lấy cảm hứng từ vũ trụ.'}</p></div><span className="section-number">{isAdmin ? '01 / SỰ KIỆN' : '01 / KHÁM PHÁ'}</span></div>
        <div className="feature-grid">
          <article className="feature-card feature-main"><div className="feature-top"><span className="feature-icon"><Icon name="compass"/></span><span className="feature-index">{isAdmin ? '01 / SỰ KIỆN' : '01 / KHÁM PHÁ'}</span></div><div><p className="feature-overline">ĐIỀU GÌ SẮP TỚI?</p><h3>{isAdmin ? 'Quản lý sự kiện' : 'Khám phá sự kiện'}</h3><p>{isAdmin ? 'Khu vực tạo và quản lý sự kiện đang được phát triển.' : 'Xem các sự kiện đang mở và chọn suất diễn phù hợp.'}</p>{isAdmin ? <span className="feature-note">ĐANG CHUẨN BỊ</span> : <a className="feature-link" href="/events">XEM SỰ KIỆN</a>}</div></article>
          <article className="feature-card"><div className="feature-top"><span className="feature-icon warm"><Icon name="ticket"/></span><span className="feature-index">{isAdmin ? '02 / ĐẶT VÉ' : '02 / VÉ CỦA BẠN'}</span></div><div><p className="feature-overline">HÀNH TRÌNH CỦA BẠN</p><h3>{isAdmin ? 'Quản lý đặt vé' : 'Vé của tôi'}</h3><p>{isAdmin ? 'Khu vực theo dõi đơn đặt vé đang được phát triển.' : 'Nơi lưu giữ những tấm vé cho hành trình giữa các vì sao.'}</p><span className="feature-note muted">SẮP RA MẮT <span>↗</span></span></div></article>
          <article className="feature-card" id="tai-khoan"><div className="feature-top"><span className="feature-icon blue"><Icon name="user"/></span><span className="feature-index">03 / TÀI KHOẢN</span></div><div><p className="feature-overline">CHÀO MỪNG BẠN</p><h3>{signedIn ? session.user.username : 'Bắt đầu hành trình'}</h3><p>{signedIn ? 'Chào mừng bạn trở lại Orbit Tickets.' : session.status === 'offline' ? 'Chưa thể kết nối máy chủ. Bạn có thể thử lại sau.' : session.status === 'loading' ? 'Đang kiểm tra tài khoản của bạn…' : 'Đăng nhập để chuẩn bị cho trải nghiệm tiếp theo.'}</p><a className="feature-link" href={signedIn ? '/profile' : '/login'}>{signedIn ? 'XEM TÀI KHOẢN' : 'ĐĂNG NHẬP'} <span>↗</span></a></div></article>
        </div>
      </section>
      <footer className="dashboard-footer"><span>✦ &nbsp; ORBIT TICKETS</span><span>MỖI HÀNH TRÌNH BẮT ĐẦU TỪ MỘT TẤM VÉ.</span><span>BEYOND THE STARS &nbsp; ✳</span></footer>
    </main>
  </div>;
}
