import { useCallback, useEffect, useState } from 'react';
import api from '../api/client.js';
import { useToast } from '../components/Toast.jsx';
import { Countdown, Icons, StatCard } from '../components/ui.jsx';
import { useSlots } from '../ws/useSlots.js';
import { formatMoney, formatTime } from '../utils/format.js';

export default function AttendantPanel() {
  const toast = useToast();
  const { board } = useSlots();
  const [reservations, setReservations] = useState(null);
  const [search, setSearch] = useState('');
  const [gatePlate, setGatePlate] = useState('');
  const [gateResult, setGateResult] = useState(null);

  const load = useCallback(async () => {
    try {
      const response = await api.get('/reservations');
      setReservations(response.data);
    } catch {
      /* role guard prevents this page for wrong roles */
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (id, action) => {
    try {
      const response = await api.post(`/reservations/${id}/${action}`);
      if (action === 'check-out') {
        toast('success', `Checked out — billed ${formatMoney(response.data.amount)}.`);
      } else {
        toast('success', 'Vehicle checked in. Slot is now occupied.');
      }
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Action failed.');
    }
  };

  const setSlotStatus = async (slot, status) => {
    let note;
    if (status === 'MAINTENANCE') {
      note = window.prompt('Reason for maintenance (optional)') || undefined;
    }
    try {
      await api.patch(`/slots/${slot.id}/status`, { status, note });
      toast('success', `Slot ${slot.slotNumber} is now ${status.toLowerCase()}.`);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Status update failed.');
    }
  };

  const gateAction = async (kind) => {
    if (!gatePlate.trim()) return;
    setGateResult(null);
    try {
      const response = await api.post(`/gates/${kind}`, { vehicleNumber: gatePlate.trim().toUpperCase() });
      if (kind === 'entry') {
        setGateResult({ ok: true, text: `Entry granted — ${response.data.slotNumber} (${response.data.vehicleNumber})` });
      } else {
        setGateResult({ ok: true, text: `Exit settled — ${response.data.vehicleNumber}, ${formatMoney(response.data.amount)}` });
      }
      await load();
    } catch (err) {
      setGateResult({ ok: false, text: err.response?.data?.error || 'Gate command failed.' });
    }
  };

  const filtered = (reservations || []).filter((r) => {
    const query = search.trim().toLowerCase();
    if (!query) return true;
    return (
      r.vehicleNumber.toLowerCase().includes(query) ||
      r.entryCode.includes(query) ||
      r.slotNumber.toLowerCase().includes(query)
    );
  });

  return (
    <div>
      <h1>Attendant Console</h1>
      <p className="page-sub">
        Verify entry codes, check vehicles in and out, keep slots healthy.
      </p>

      {board && (
        <div className="stats-grid">
          <StatCard icon={Icons.check} label="Available" value={board.available} accent="ok" />
          <StatCard icon={Icons.clock} label="Reserved" value={board.reserved} accent="warn" />
          <StatCard icon={Icons.car} label="Occupied" value={board.occupied} accent="bad" />
          <StatCard icon={Icons.wrench} label="Maintenance" value={board.maintenance} accent="gray" />
        </div>
      )}

      <div className="card">
        <h3><Icons.zap size={16} style={{ verticalAlign: '-3px' }} /> Gate console (ANPR)</h3>
        <p className="muted" style={{ fontSize: '0.8rem', marginTop: 0 }}>
          Simulates the barrier camera: a plate with a reservation is checked in, an unknown
          plate becomes a walk-in (if space allows), exit settles the bill.
        </p>
        <div className="inline-form">
          <input
            placeholder="Vehicle plate"
            value={gatePlate}
            onChange={(e) => setGatePlate(e.target.value.toUpperCase())}
            style={{ minWidth: '180px' }}
            onKeyDown={(e) => e.key === 'Enter' && gateAction('entry')}
          />
          <button className="btn btn-primary" onClick={() => gateAction('entry')}>
            Entry
          </button>
          <button className="btn btn-secondary" onClick={() => gateAction('exit')}>
            Exit
          </button>
        </div>
        {gateResult && (
          <div className={`gate-console-result ${gateResult.ok ? 'ok' : 'bad'}`}>
            {gateResult.ok ? <Icons.check size={15} /> : <Icons.alert size={15} />}
            {gateResult.text}
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-header">
          <h3 style={{ margin: 0 }}>Active reservations</h3>
          <input
            className="search-input"
            placeholder="Search code, vehicle or slot…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        {!reservations ? (
          <p className="muted">Loading…</p>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <span className="big"><Icons.check size={26} /></span>
            {search ? 'No reservations match your search.' : 'No active reservations right now.'}
          </div>
        ) : (
          <div className="res-grid">
            {filtered.map((reservation) => (
              <div className="card res-card" key={reservation.id}>
                <div className="res-head">
                  <div>
                    <span className="res-slot">Slot {reservation.slotNumber}</span>
                    <div className="res-vehicle">
                      {reservation.vehicleNumber}
                      {reservation.synthetic && <span className="auto-pill" style={{ marginLeft: '0.4rem' }}>Auto / walk-in</span>}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
                    {reservation.overstay && <span className="overstay-pill">Overstay</span>}
                    {reservation.entryTime ? (
                      <span className="status-pill slot-occupied">Parked</span>
                    ) : (
                      <span className="status-pill slot-reserved">Awaiting entry</span>
                    )}
                  </div>
                </div>

                {!reservation.entryTime && (
                  <>
                    <div className="code-label">Gate entry code</div>
                    <div className="code-block">{reservation.entryCode}</div>
                    <div style={{ textAlign: 'center' }}>
                      <Countdown until={reservation.expiresAt} />
                    </div>
                  </>
                )}

                <div className="res-foot">
                  <span className="muted" style={{ fontSize: '0.78rem' }}>
                    {reservation.userName} · {formatTime(reservation.reservedAt)}
                  </span>
                  {reservation.entryTime ? (
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => act(reservation.id, 'check-out')}
                    >
                      <Icons.money size={14} /> Check-out
                    </button>
                  ) : (
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => act(reservation.id, 'check-in')}
                    >
                      <Icons.check size={14} /> Check-in
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h3>Slot maintenance</h3>
        {board && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Slot</th>
                  <th>Floor</th>
                  <th>Zone</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {board.slots.map((slot) => (
                  <tr key={slot.id}>
                    <td className="mono">{slot.slotNumber}</td>
                    <td>{slot.floor === 1 ? 'Ground' : `Level ${slot.floor - 1}`}</td>
                    <td>{slot.zone}</td>
                    <td>
                      <span className={`status-pill slot-${slot.status.toLowerCase()}`}>
                        {slot.status}
                      </span>
                    </td>
                    <td>
                      <div className="actions">
                        {slot.status === 'AVAILABLE' && (
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => setSlotStatus(slot, 'MAINTENANCE')}
                          >
                            Set maintenance
                          </button>
                        )}
                        {slot.status === 'MAINTENANCE' && (
                          <button
                            className="btn btn-primary btn-sm"
                            onClick={() => setSlotStatus(slot, 'AVAILABLE')}
                          >
                            Make available
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
