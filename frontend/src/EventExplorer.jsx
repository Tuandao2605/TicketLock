import React, { useEffect, useState } from 'react';
import { getJson, messageFor, nextSelection } from './eventExplorerLogic.js';

const pageSize = 10;

function OrbitLoading({ children }) {
  return <p className="orbit-loading" role="status"><span className="orbit-loader" aria-hidden="true"/><span>{children}</span></p>;
}

function dateTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'Chưa có thời gian' : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'full', timeStyle: 'short' }).format(date);
}

function priceText(price) {
  if (!price || !Number.isSafeInteger(price.amount)) return 'Chưa có giá';
  try {
    const formatter = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: price.currency });
    return formatter.format(price.amount / (10 ** formatter.resolvedOptions().maximumFractionDigits));
  } catch { return `${price.amount} ${price.currency}`; }
}

function Pagination({ page, total, onPage }) {
  if (total <= pageSize && page === 0) return null;
  return <div className="explorer-pagination" aria-label="Chuyển trang">
    <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>Trang trước</button>
    <span>Trang {page + 1}</span>
    <button type="button" disabled={(page + 1) * pageSize >= total} onClick={() => onPage(page + 1)}>Trang sau</button>
  </div>;
}

export default function EventExplorer() {
  const [eventPage, setEventPage] = useState(0);
  const [events, setEvents] = useState({ status: 'loading' });
  const [event, setEvent] = useState(null);
  const [showPage, setShowPage] = useState(0);
  const [shows, setShows] = useState({ status: 'idle' });
  const [showId, setShowId] = useState(null);
  const [detail, setDetail] = useState({ status: 'idle' });
  const [selected, setSelected] = useState([]);
  const [notice, setNotice] = useState('');
  const [reloadEvents, setReloadEvents] = useState(0);
  const [reloadShows, setReloadShows] = useState(0);
  const [reloadDetail, setReloadDetail] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setEvents({ status: 'loading' });
    getJson(`/api/v1/events?page=${eventPage}&size=${pageSize}`, controller.signal)
      .then(data => { if (!controller.signal.aborted) setEvents({ status: 'ready', data }); })
      .catch(error => { if (!controller.signal.aborted && error.name !== 'AbortError') setEvents({ status: 'error', message: messageFor(error) }); });
    return () => controller.abort();
  }, [eventPage, reloadEvents]);

  useEffect(() => {
    if (!event) { setShows({ status: 'idle' }); return undefined; }
    const controller = new AbortController();
    setShows({ status: 'loading' });
    getJson(`/api/v1/events/${encodeURIComponent(event.id)}/shows?page=${showPage}&size=${pageSize}`, controller.signal)
      .then(data => { if (!controller.signal.aborted) setShows({ status: 'ready', data }); })
      .catch(error => { if (!controller.signal.aborted && error.name !== 'AbortError') setShows({ status: 'error', message: messageFor(error) }); });
    return () => controller.abort();
  }, [event, showPage, reloadShows]);

  useEffect(() => {
    if (!showId) { setDetail({ status: 'idle' }); return undefined; }
    const controller = new AbortController();
    setDetail({ status: 'loading' });
    setSelected([]); setNotice('');
    const id = encodeURIComponent(showId);
    Promise.all([getJson(`/api/v1/shows/${id}`, controller.signal), getJson(`/api/v1/shows/${id}/seats`, controller.signal)])
      .then(([show, snapshot]) => { if (!controller.signal.aborted) setDetail({ status: 'ready', show, snapshot }); })
      .catch(error => { if (!controller.signal.aborted && error.name !== 'AbortError') setDetail({ status: 'error', message: messageFor(error) }); });
    return () => controller.abort();
  }, [showId, reloadDetail]);

  function chooseEvent(item) {
    setEvent(item); setShowPage(0); setShowId(null); setDetail({ status: 'idle' }); setSelected([]); setNotice('');
  }
  function toggleSeat(seat) {
    const result = nextSelection(selected, seat);
    setSelected(result.selected);
    setNotice(result.notice);
  }
  const seatLabels = { AVAILABLE: 'Còn trống', HELD: 'Đang giữ', SOLD: 'Đã bán' };
  const seats = detail.status === 'ready' ? detail.snapshot.seats : [];
  const selectedSeats = seats.filter(seat => selected.includes(seat.showSeatId));
  const prices = [...new Set(seats.map(seat => priceText(seat.price)))];
  const currency = selectedSeats[0]?.price?.currency;
  const total = selectedSeats.reduce((sum, seat) => sum + (seat.price?.amount ?? 0), 0);
  const totalAvailable = selectedSeats.length > 0 && selectedSeats.every(seat => Number.isSafeInteger(seat.price?.amount) && seat.price.currency === currency) && Number.isSafeInteger(total);

  return <section className="explorer" aria-labelledby="explorer-title">
    <div className="explorer-heading"><div><h2 id="explorer-title">Chọn sự kiện và suất diễn</h2><p>Chọn lần lượt để xem sơ đồ ghế mới nhất.</p></div></div>
    <div className="explorer-layout">
      <div className="explorer-panel"><div className="explorer-panel-title"><h3>Sự kiện</h3><span>Danh sách công khai</span></div>
        {events.status === 'loading' && <OrbitLoading>Đang tải sự kiện…</OrbitLoading>}
        {events.status === 'error' && <div role="alert"><p>{events.message}</p><button type="button" onClick={() => setReloadEvents(n => n + 1)}>Thử lại</button></div>}
        {events.status === 'ready' && <>{events.data.items.length === 0 ? <p>Chưa có sự kiện đang mở. Hãy quay lại sau nhé.</p> : <div className="explorer-list">{events.data.items.map(item => <button type="button" key={item.id} className={`explorer-item event-card ${event?.id === item.id ? 'is-active' : ''}`} aria-pressed={event?.id === item.id} onClick={() => chooseEvent(item)}><span className="event-card-art" aria-hidden="true"/><span className="event-card-content"><strong>{item.name}</strong><span>{item.description || 'Khám phá các suất diễn'}</span></span></button>)}</div>}
          <Pagination page={eventPage} total={events.data.totalElements} onPage={value => { setEventPage(value); setEvent(null); setShowId(null); setShows({ status: 'idle' }); setDetail({ status: 'idle' }); setSelected([]); }} /></>}
      </div>
      <div className="explorer-panel"><div className="explorer-panel-title"><h3>Suất diễn</h3><span>{event ? event.name : 'Chọn sự kiện'}</span></div>
        {shows.status === 'idle' && <p>Chọn một sự kiện để xem các suất diễn.</p>}
        {shows.status === 'loading' && <OrbitLoading>Đang tải suất diễn…</OrbitLoading>}
        {shows.status === 'error' && <div role="alert"><p>{shows.message}</p><button type="button" onClick={() => setReloadShows(n => n + 1)}>Thử lại</button></div>}
        {shows.status === 'ready' && <>{shows.data.items.length === 0 ? <p>Chưa có suất diễn đang mở.</p> : <div className="explorer-list">{shows.data.items.map(show => <button type="button" key={show.id} className={`explorer-item ${showId === show.id ? 'is-active' : ''}`} aria-pressed={showId === show.id} onClick={() => { if (show.id !== showId) setDetail({ status: 'loading' }); setShowId(show.id); setSelected([]); setNotice(''); }}><strong>{dateTime(show.startsAt)}</strong><span>{show.venue}</span></button>)}</div>}
          <Pagination page={showPage} total={shows.data.totalElements} onPage={value => { setShowPage(value); setShowId(null); setDetail({ status: 'idle' }); setSelected([]); }} /></>}
      </div>
    </div>
    {detail.status !== 'idle' && <div className="explorer-panel seat-panel" aria-live="polite">
      {detail.status === 'loading' && <OrbitLoading>Đang tải sơ đồ ghế…</OrbitLoading>}
      {detail.status === 'error' && <div role="alert"><p>{detail.message}</p><button type="button" onClick={() => { setSelected([]); setDetail({ status: 'loading' }); setReloadDetail(n => n + 1); }}>Thử lại</button></div>}
      {detail.status === 'ready' && <><div className="explorer-panel-title"><div><p className="explorer-kicker">SƠ ĐỒ GHẾ</p><h3>{detail.show.venue}</h3><p>{dateTime(detail.show.startsAt)}</p></div><button type="button" className="refresh-seats" onClick={() => { setSelected([]); setDetail({ status: 'loading' }); setReloadDetail(n => n + 1); }}>Cập nhật ghế</button></div>
        <div className="seat-legend"><span><i className="legend-available"/> Còn trống</span><span><i className="legend-selected"/> Đang chọn</span><span><i className="legend-held"/> Đang giữ</span><span><i className="legend-sold"/> Đã bán</span></div>
        {prices.length > 0 && <p className="seat-prices"><strong>Giá ghế tham khảo:</strong> {prices.join(' · ')}</p>}
        <div className="seat-stage">SÂN KHẤU</div>
        {detail.snapshot.seats.length === 0 ? <p>Chưa có ghế trong sơ đồ.</p> : <div className="seat-grid" aria-label="Danh sách ghế">{detail.snapshot.seats.map(seat => {
          const active = selected.includes(seat.showSeatId);
          return <button type="button" key={seat.showSeatId} className={`seat seat-${seat.status.toLowerCase()} ${active ? 'is-selected' : ''}`} disabled={seat.status !== 'AVAILABLE'} aria-pressed={active} aria-label={`Ghế ${seat.label}, ${seatLabels[seat.status] || 'không khả dụng'}, ${priceText(seat.price)}`} title={`${seat.label} · ${seatLabels[seat.status] || 'Không khả dụng'} · ${priceText(seat.price)}`} onClick={() => toggleSeat(seat)}>{seat.label}</button>;
        })}</div>}
        <div className="seat-summary"><div><strong>{selected.length ? `Đã chọn ${selected.length} ghế` : 'Chọn ghế bạn quan tâm'}</strong>{selected.length ? <ul className="selected-seat-prices">{selectedSeats.map(seat => <li key={seat.showSeatId}>Ghế {seat.label}: {priceText(seat.price)}</li>)}</ul> : <p>Chọn tối đa 6 ghế còn trống.</p>}</div><div className="seat-checkout"><strong>Tổng tham khảo: {selected.length ? totalAvailable ? priceText({ amount: total, currency }) : 'Không thể tính tổng' : '—'}</strong><button type="button" disabled aria-describedby="booking-unavailable">Đặt ghế</button><p id="booking-unavailable">Chức năng đặt ghế đang được phát triển.</p></div></div>
        {notice && <p role="status" className="seat-notice">{notice}</p>}
        <p className="seat-disclaimer">Sơ đồ được cập nhật lúc {dateTime(detail.snapshot.capturedAt)}. Việc chọn ghế trên màn hình chưa giữ chỗ hoặc đặt vé.</p>
      </>}
    </div>}
  </section>;
}
