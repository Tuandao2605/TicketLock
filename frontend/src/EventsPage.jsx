import React from 'react';
import EventExplorer from './EventExplorer';
import SiteHeader, { useSession } from './SiteHeader';

export default function EventsPage() {
  const session = useSession();
  return <div className="dashboard-shell events-shell">
    <SiteHeader current="events" session={session} layout="sidebar" />
    <main className="dashboard-main events-main">
      <div className="events-intro"><p className="explorer-kicker">ORBIT TICKETS / SỰ KIỆN</p>
        <h1>Khám phá <em>sự kiện</em></h1>
        <p>Chọn một sự kiện và suất diễn để khám phá sơ đồ ghế.</p>
      </div>
      <EventExplorer />
    </main>
  </div>;
}
