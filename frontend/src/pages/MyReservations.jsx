import { useCallback, useEffect, useState } from 'react';
import api from '../api/client.js';
import { useToast } from '../components/Toast.jsx';
import { Countdown, Icons, Modal, PseudoQR, StatCard } from '../components/ui.jsx';
import { downloadCsv } from '../utils/download.js';
import { formatMoney, formatRelative, formatTime } from '../utils/format.js';

export default function MyReservations() {
  const toast = useToast();
  const [reservations, setReservations] = useState(null);
  const [stats, setStats] = useState(null);
  const [myWaitlist, setMyWaitlist] = useState(null);
  const [receiptFor, setReceiptFor] = useState(null);

  const load = useCallback(async () => {
    try {
      const [resResponse, statsResponse, waitlistResponse] = await Promise.all([
        api.get('/reservations/my'),
        api.get('/reservations/my/stats'),
        api.get('/waitlist/my'),
      ]);
      setReservations(resResponse.data);
      setStats(statsResponse.data);
      setMyWaitlist(waitlistResponse.data);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Failed to load your reservations.');
    }
  }, [toast]);

  useEffect(() => {
    load();
  }, [load]);

  const cancel = async (id) => {
    try {
      await api.post(`/reservations/${id}/cancel`);
      toast('success', 'Reservation cancelled — the slot is free again.');
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Cancel failed.');
    }
  };

  const extend = async (id, additionalHours) => {
    try {
      await api.post(`/reservations/${id}/extend`, { additionalHours });
      toast('success', `Session extended by ${additionalHours}h.`);
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Extension failed.');
    }
  };

  const leaveWaitlist = async () => {
    try {
      await api.delete(`/waitlist/${myWaitlist.id}`);
      setMyWaitlist(null);
      toast('success', 'You left the waitlist.');
    } catch (err) {
      toast('error', err.response?.data?.error || 'Could not leave the waitlist.');
    }
  };

  const exportCsv = async () => {
    try {
      await downloadCsv('/reservations/my/export', 'my-parking-history.csv');
      toast('success', 'History exported to CSV.');
    } catch {
      toast('error', 'Export failed.');
    }
  };

  if (!reservations) {
    return <p className="muted">Loading your reservations…</p>;
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>My Reservations</h1>
          <p className="page-sub">Your parking history, entry codes and receipts.</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={exportCsv}>
          <Icons.download size={14} /> Export CSV
        </button>
      </div>

      {stats && (
        <div className="stats-grid">
          <StatCard icon={Icons.car} label="Sessions completed" value={stats.totalSessions} accent="cyan" />
          <StatCard
            icon={Icons.money}
            label="Total spent"
            value={stats.totalSpent}
            format={(v) => formatMoney(v)}
            accent="ok"
          />
          <StatCard
            icon={Icons.ticket}
            label="Active reservation"
            value={stats.hasActiveReservation ? 'Yes' : 'None'}
            accent="warn"
          />
        </div>
      )}

      {myWaitlist && (
        <div className="card queue-card" style={{ maxWidth: '420px' }}>
          <h3><Icons.clock size={16} style={{ verticalAlign: '-3px' }} /> Waitlist</h3>
          <div className="queue-pos">#{myWaitlist.position}</div>
          <p className="muted" style={{ fontSize: '0.82rem' }}>
            of {myWaitlist.totalQueued} in queue · {myWaitlist.vehicleNumber}
            {myWaitlist.zone ? ` · Zone ${myWaitlist.zone}` : ''}
          </p>
          <button className="btn btn-danger btn-sm" onClick={leaveWaitlist}>
            Leave waitlist
          </button>
        </div>
      )}

      {reservations.length === 0 && !myWaitlist ? (
        <div className="card empty-state">
          <span className="big"><Icons.pin size={26} /></span>
          No reservations yet — grab a slot from the live board.
        </div>
      ) : (
        <div className="res-grid">
          {reservations.map((reservation) => (
            <ReservationCard
              key={reservation.id}
              reservation={reservation}
              onCancel={() => cancel(reservation.id)}
              onExtend={(h) => extend(reservation.id, h)}
              onReceipt={() => setReceiptFor(reservation)}
            />
          ))}
        </div>
      )}

      <MyPasses />

      <MyBookings />

      <MyVehicles />

      {receiptFor && (
        <Modal onClose={() => setReceiptFor(null)}>
          <h3>Parking receipt</h3>
          <p className="modal-sub">Slot {receiptFor.slotNumber} · Zone {receiptFor.zone}</p>
          <PseudoQR seed={`${receiptFor.entryCode}-${receiptFor.id}`} />
          <div className="code-block" style={{ marginTop: 0 }}>{receiptFor.entryCode}</div>
          <table className="table">
            <tbody>
              <tr><td className="muted">Vehicle</td><td className="mono">{receiptFor.vehicleNumber}</td></tr>
              <tr><td className="muted">Status</td><td>{receiptFor.status}</td></tr>
              <tr><td className="muted">Booked</td><td>{formatTime(receiptFor.reservedAt)}</td></tr>
              <tr><td className="muted">Entry</td><td>{formatTime(receiptFor.entryTime)}</td></tr>
              <tr><td className="muted">Exit</td><td>{formatTime(receiptFor.exitTime)}</td></tr>
              <tr>
                <td className="muted">Duration booked</td>
                <td>{receiptFor.expectedHours + receiptFor.extensionHours}h
                  {receiptFor.extensionHours > 0 && (
                    <span className="muted"> ({receiptFor.extensionHours}h extended)</span>
                  )}
                </td>
              </tr>
              <tr>
                <td className="muted">Amount paid</td>
                <td className="amount-tag">{formatMoney(receiptFor.amount)}</td>
              </tr>
            </tbody>
          </table>
        </Modal>
      )}
    </div>
  );
}

function ReservationCard({ reservation, onCancel, onExtend, onReceipt }) {
  const isActive = reservation.status === 'ACTIVE';
  const entered = reservation.entryTime != null;

  return (
    <div className="card res-card">
      <div className="res-head">
        <div>
          <span className="res-slot">Slot {reservation.slotNumber}</span>
          <span className="muted" style={{ fontSize: '0.8rem' }}> · Zone {reservation.zone}</span>
          <div className="res-vehicle">{reservation.vehicleNumber}</div>
        </div>
        <span className={`status-pill ${reservation.status.toLowerCase()}`}>
          {reservation.status}
        </span>
      </div>

      {isActive && !entered && (
        <div>
          <div className="code-label">Gate entry code</div>
          <div className="code-block">{reservation.entryCode}</div>
          <PseudoQR seed={`${reservation.entryCode}-${reservation.id}`} />
          <div style={{ textAlign: 'center' }}>
            <Countdown until={reservation.expiresAt} />
          </div>
        </div>
      )}

      {isActive && entered && (
        <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
          <span className="status-pill slot-occupied">Parked</span>
          {reservation.overstay && <span className="overstay-pill">Overstay</span>}
          <span className="muted" style={{ fontSize: '0.76rem', alignSelf: 'center' }}>
            booked {reservation.expectedHours + reservation.extensionHours}h
          </span>
        </div>
      )}

      <Timeline reservation={reservation} />

      <div className="res-foot">
        <span className="muted" style={{ fontSize: '0.78rem' }}>
          Booked {formatTime(reservation.reservedAt)}
        </span>
        <div style={{ display: 'flex', gap: '0.4rem' }}>
          {reservation.status === 'COMPLETED' && (
            <>
              {reservation.passCovered && <span className="pass-tag">Covered by pass · ₹0</span>}
              <span className="amount-tag">{formatMoney(reservation.amount)}</span>
              <button className="btn btn-secondary btn-sm" onClick={onReceipt}>
                Receipt
              </button>
            </>
          )}
          {isActive && entered && (
            <>
              <button className="btn btn-secondary btn-sm" onClick={() => onExtend(1)}>
                +1h
              </button>
              <button className="btn btn-secondary btn-sm" onClick={() => onExtend(2)}>
                +2h
              </button>
            </>
          )}
          {isActive && !entered && (
            <button className="btn btn-danger btn-sm" onClick={onCancel}>
              Cancel
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function Timeline({ reservation }) {
  const steps = [
    { label: 'Booked', time: reservation.reservedAt, done: true },
    { label: 'Parked', time: reservation.entryTime, done: reservation.entryTime != null },
    { label: 'Exited', time: reservation.exitTime, done: reservation.exitTime != null },
  ];

  return (
    <div className="timeline">
      {steps.map((step) => (
        <div key={step.label} className={`timeline-step ${step.done ? 'done' : ''}`}>
          <strong>{step.label}</strong>
          {step.time ? formatTime(step.time).split(', ')[1] || formatTime(step.time) : '—'}
        </div>
      ))}
    </div>
  );
}

function MyPasses() {
  const toast = useToast();
  const [passes, setPasses] = useState([]);
  const [zone, setZone] = useState('');
  const [months, setMonths] = useState(1);

  const MONTHLY = { '': 3000, A: 4000, B: 3200, C: 2500, D: 2200, E: 1800, F: 1500 };
  const price = Math.round((MONTHLY[zone] || 3000) * months * (months >= 12 ? 0.8 : months >= 3 ? 0.9 : 1));

  const load = useCallback(async () => {
    try {
      const response = await api.get('/passes/my');
      setPasses(response.data);
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const purchase = async (event) => {
    event.preventDefault();
    try {
      await api.post('/passes', { zone: zone || null, months });
      toast('success', 'Season pass activated — sessions in scope now bill ₹0.');
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Purchase failed.');
    }
  };

  const active = passes.find((p) => p.validNow);

  return (
    <div className="card" style={{ maxWidth: '560px' }}>
      <h3><Icons.ticket size={16} style={{ verticalAlign: '-3px' }} /> Season pass</h3>
      {active ? (
        <div className="session-rows" style={{ marginTop: 0 }}>
          <div className="session-row">
            <span className="s-label">Scope</span>
            <span className="s-value">{active.zone ? `Zone ${active.zone}` : 'All zones'}</span>
          </div>
          <div className="session-row">
            <span className="s-label">Valid until</span>
            <span className="s-value">{formatTime(active.validUntil)}</span>
          </div>
          <div className="session-row">
            <span className="s-label">Paid</span>
            <span className="s-value amount-tag">{formatMoney(active.price)}</span>
          </div>
        </div>
      ) : (
        <>
          <p className="muted" style={{ fontSize: '0.82rem', marginTop: 0 }}>
            Park unlimited sessions in your chosen scope — covered checkouts bill ₹0.
            10% off for 3+ months, 20% off for 12.
          </p>
          <form className="inline-form" onSubmit={purchase}>
            <select value={zone} onChange={(e) => setZone(e.target.value)}>
              <option value="">All zones</option>
              {['A', 'B', 'C', 'D', 'E', 'F'].map((z) => (
                <option key={z} value={z}>Zone {z}</option>
              ))}
            </select>
            <select value={months} onChange={(e) => setMonths(Number(e.target.value))}>
              {[1, 3, 6, 12].map((m) => (
                <option key={m} value={m}>{m} month{m > 1 ? 's' : ''}</option>
              ))}
            </select>
            <span className="amount-tag" style={{ alignSelf: 'center' }}>{formatMoney(price)}</span>
            <button className="btn btn-primary" type="submit">Buy pass</button>
          </form>
        </>
      )}
    </div>
  );
}

function MyBookings() {
  const toast = useToast();
  const [bookings, setBookings] = useState([]);
  const [vehicle, setVehicle] = useState('');
  const [zone, setZone] = useState('');
  const [date, setDate] = useState('');
  const [time, setTime] = useState('09:00');
  const [hours, setHours] = useState(2);

  const load = useCallback(async () => {
    try {
      const response = await api.get('/bookings/my');
      setBookings(response.data.filter((b) => b.status === 'UPCOMING' || b.status === 'ALLOCATED'));
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    load();
    const id = setInterval(load, 30000);
    return () => clearInterval(id);
  }, [load]);

  const today = new Date().toISOString().slice(0, 10);
  const maxDate = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);

  const create = async (event) => {
    event.preventDefault();
    try {
      const startTime = new Date(`${date}T${time}:00`).toISOString();
      await api.post('/bookings', {
        vehicleNumber: vehicle,
        zone: zone || null,
        startTime,
        expectedHours: hours,
      });
      toast('success', 'Booking scheduled — a slot will be allocated before you arrive.');
      setVehicle('');
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Booking failed.');
    }
  };

  const cancel = async (id) => {
    try {
      await api.delete(`/bookings/${id}`);
      toast('success', 'Booking cancelled.');
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Cancel failed.');
    }
  };

  return (
    <div className="card" style={{ maxWidth: '560px' }}>
      <h3><Icons.clock size={16} style={{ verticalAlign: '-3px' }} /> Book ahead</h3>
      <p className="muted" style={{ fontSize: '0.82rem', marginTop: 0 }}>
        Schedule a window up to 7 days out — the engine allocates the best slot shortly
        before you arrive, so nothing sits idle all day.
      </p>
      <form className="inline-form" onSubmit={create}>
        <input
          placeholder="Vehicle plate"
          value={vehicle}
          onChange={(e) => setVehicle(e.target.value.toUpperCase())}
          required
          minLength={4}
          style={{ minWidth: '130px' }}
        />
        <select value={zone} onChange={(e) => setZone(e.target.value)}>
          <option value="">Any zone</option>
          {['A', 'B', 'C', 'D', 'E', 'F'].map((z) => (
            <option key={z} value={z}>Zone {z}</option>
          ))}
        </select>
        <input type="date" value={date} min={today} max={maxDate} required onChange={(e) => setDate(e.target.value)} />
        <select value={time} onChange={(e) => setTime(e.target.value)}>
          {Array.from({ length: 17 }, (_, i) => `${String(i + 6).padStart(2, '0')}:00`).map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
        <select value={hours} onChange={(e) => setHours(Number(e.target.value))}>
          {[1, 2, 3, 4, 6, 8].map((h) => (
            <option key={h} value={h}>{h}h</option>
          ))}
        </select>
        <button className="btn btn-primary" type="submit">Schedule</button>
      </form>

      {bookings.length > 0 && (
        <div style={{ marginTop: '1rem' }}>
          {bookings.map((booking) => (
            <div className="booking-row" key={booking.id}>
              <div className="b-main">
                <span className="b-time">
                  {formatTime(booking.startTime)} <span className="muted">({formatRelative(booking.startTime)})</span>
                </span>
                <span className="b-sub">
                  {booking.vehicleNumber}
                  {booking.zone ? ` · Zone ${booking.zone}` : ' · Any zone'}
                  {' · '}{booking.expectedHours}h
                  {booking.slotNumber && <> · assigned <strong>{booking.slotNumber}</strong></>}
                </span>
              </div>
              <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
                <span className={`status-pill ${booking.status === 'ALLOCATED' ? 'active' : 'slot-reserved'}`}>
                  {booking.status === 'ALLOCATED' ? 'Slot ready' : 'Upcoming'}
                </span>
                {booking.status === 'UPCOMING' && (
                  <button className="btn btn-danger btn-sm" onClick={() => cancel(booking.id)}>
                    Cancel
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function MyVehicles() {
  const toast = useToast();
  const [vehicles, setVehicles] = useState([]);
  const [label, setLabel] = useState('');
  const [vehicleNumber, setVehicleNumber] = useState('');

  const load = useCallback(async () => {
    try {
      const response = await api.get('/vehicles');
      setVehicles(response.data);
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const add = async (event) => {
    event.preventDefault();
    try {
      await api.post('/vehicles', { label, vehicleNumber });
      setLabel('');
      setVehicleNumber('');
      toast('success', 'Vehicle saved.');
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Could not save vehicle.');
    }
  };

  const remove = async (id) => {
    try {
      await api.delete(`/vehicles/${id}`);
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Could not delete vehicle.');
    }
  };

  return (
    <div className="card" style={{ maxWidth: '520px' }}>
      <h3><Icons.car size={16} style={{ verticalAlign: '-3px' }} /> My vehicles</h3>
      {vehicles.length > 0 && (
        <div className="vehicle-list" style={{ marginBottom: '0.9rem' }}>
          {vehicles.map((vehicle) => (
            <div className="vehicle-item" key={vehicle.id}>
              <div>
                <span className="v-label">{vehicle.label}</span>
                <span className="v-number" style={{ marginLeft: '0.5rem' }}>
                  {vehicle.vehicleNumber}
                </span>
              </div>
              <button className="btn btn-danger btn-sm" onClick={() => remove(vehicle.id)}>
                Remove
              </button>
            </div>
          ))}
        </div>
      )}
      <form className="inline-form" onSubmit={add}>
        <input
          placeholder="Label (e.g. My Car)"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          required
          style={{ minWidth: '150px' }}
        />
        <input
          placeholder="Plate number"
          value={vehicleNumber}
          onChange={(e) => setVehicleNumber(e.target.value.toUpperCase())}
          required
          minLength={4}
          style={{ minWidth: '150px' }}
        />
        <button className="btn btn-primary" type="submit">Save</button>
      </form>
    </div>
  );
}
