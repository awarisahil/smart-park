import { useEffect, useMemo, useRef, useState } from 'react';
import { useSlots } from '../ws/useSlots.js';
import { useActivity } from '../ws/useActivity.js';
import { useToast } from '../components/Toast.jsx';
import { Icons, Modal, StatCard } from '../components/ui.jsx';
import api from '../api/client.js';
import { formatMoney, formatTime } from '../utils/format.js';

const STATUS_LABELS = {
  AVAILABLE: 'Available',
  RESERVED: 'Reserved',
  OCCUPIED: 'Occupied',
  MAINTENANCE: 'Maintenance',
};

const TYPE_SHORT = {
  EV_CHARGING: 'EV',
  ACCESSIBLE: 'ACC',
  COMPACT: 'CMP',
};

const TYPE_OPTIONS = [
  { value: '', label: 'Any type' },
  { value: 'EV_CHARGING', label: 'EV charging' },
  { value: 'ACCESSIBLE', label: 'Accessible' },
  { value: 'COMPACT', label: 'Compact' },
];

const ZONE_COLORS = {
  A: '#2563eb', B: '#0d9488', C: '#d97706',
  D: '#7c3aed', E: '#db2777', F: '#475569',
};

function floorName(floor) {
  return floor === 1 ? 'Ground' : `Level ${floor - 1}`;
}

export default function SlotBoard() {
  const { board, connected } = useSlots();
  const activity = useActivity();
  const toast = useToast();
  const [vehicleNumber, setVehicleNumber] = useState('');
  const [vehicles, setVehicles] = useState([]);
  const [zone, setZone] = useState('');
  const [slotType, setSlotType] = useState('');
  const [hours, setHours] = useState(2);
  const [selectedSlot, setSelectedSlot] = useState(null);
  const [session, setSession] = useState(null);
  const [busy, setBusy] = useState(false);
  const [myWaitlist, setMyWaitlist] = useState(null);
  const [myActive, setMyActive] = useState(null);
  const [activeFloor, setActiveFloor] = useState(1);
  const [highlighted, setHighlighted] = useState(null);
  const previousStatuses = useRef(new Map());

  useEffect(() => {
    api.get('/vehicles').then((response) => setVehicles(response.data)).catch(() => {});
    api.get('/waitlist/my').then((response) => setMyWaitlist(response.data)).catch(() => {});
    api.get('/reservations/my')
      .then((response) => {
        const active = response.data.find((r) => r.status === 'ACTIVE');
        setMyActive(active || null);
      })
      .catch(() => {});
  }, []);

  // Track slot status changes so we can flash the tile that changed.
  useEffect(() => {
    if (!board) return;
    const current = new Map();
    for (const slot of board.slots) {
      const before = previousStatuses.current.get(slot.id);
      current.set(slot.id, slot.status);
      if (before && before !== slot.status) {
        slot.justChanged = true;
      }
    }
    previousStatuses.current = current;
  }, [board]);

  const floors = useMemo(() => {
    if (!board) return [];
    const map = new Map();
    for (const slot of board.slots) {
      if (!map.has(slot.floor)) map.set(slot.floor, []);
      map.get(slot.floor).push(slot);
    }
    return [...map.entries()].sort((a, b) => a[0] - b[0]);
  }, [board]);

  const floorZones = useMemo(() => {
    const found = floors.find(([floor]) => floor === activeFloor);
    if (!found) return [];
    const map = new Map();
    for (const slot of found[1]) {
      if (!map.has(slot.zone)) map.set(slot.zone, []);
      map.get(slot.zone).push(slot);
    }
    return [...map.entries()];
  }, [floors, activeFloor]);

  const matchingSlots = useMemo(() => {
    if (!board) return [];
    return board.slots.filter(
      (s) => s.status === 'AVAILABLE' && (!zone || s.zone === zone) && (!slotType || s.type === slotType)
    );
  }, [board, zone, slotType]);

  const estimateRate = useMemo(() => {
    if (matchingSlots.length === 0) return null;
    return Math.min(...matchingSlots.map((s) => s.hourlyRate));
  }, [matchingSlots]);

  const zoneOptions = useMemo(() => {
    const options = [];
    for (const [floor, slots] of floors) {
      const seen = new Map();
      for (const slot of slots) {
        if (!seen.has(slot.zone)) seen.set(slot.zone, slot.hourlyRate);
      }
      for (const [zoneName, rate] of seen) {
        options.push({ value: zoneName, label: `Zone ${zoneName} · ${floorName(floor)} · ${formatMoney(rate)}/hr` });
      }
    }
    return options;
  }, [floors]);

  const reserve = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const response = await api.post('/reservations', {
        vehicleNumber,
        zone: zone || null,
        preferredType: slotType || null,
        expectedHours: hours,
      });
      toast(
        'success',
        `Slot ${response.data.slotNumber} reserved — entry code ${response.data.entryCode}`
      );
      setMyActive(response.data);
      setVehicleNumber('');
      setSelectedSlot(null);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Reservation failed. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  const joinWaitlist = async (event) => {
    event.preventDefault();
    setBusy(true);
    try {
      const response = await api.post('/waitlist', {
        vehicleNumber,
        zone: zone || null,
        preferredType: slotType || null,
      });
      setMyWaitlist(response.data);
      toast('success', `You're on the waitlist — position #${response.data.position}`);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Could not join the waitlist.');
    } finally {
      setBusy(false);
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

  const openSlot = (slot) => {
    setSelectedSlot(slot);
    setSession(null);
    api.get(`/slots/${slot.id}/session`)
      .then((response) => setSession(response.data))
      .catch(() => {});
  };

  const locateMySlot = () => {
    if (!myActive || !board) return;
    const slot = board.slots.find((s) => s.slotNumber === myActive.slotNumber);
    if (!slot) return;
    setActiveFloor(slot.floor);
    setHighlighted(slot.id);
    setTimeout(() => {
      document
        .querySelector(`[data-slot-id="${slot.id}"]`)
        ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 80);
    setTimeout(() => setHighlighted(null), 6000);
  };

  if (!board) {
    return <p className="muted">Loading live board…</p>;
  }

  const full = board.available === 0;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Live Slot Board</h1>
          <p className="page-sub">Real-time availability across every floor.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
          {myActive && (
            <button className="locate-chip" onClick={locateMySlot}>
              <Icons.pin size={14} /> My slot · {myActive.slotNumber}
            </button>
          )}
          <span className={connected ? 'live-badge' : 'live-badge offline'}>
            <span className="pulse" />
            {connected ? 'Live' : 'Offline'}
          </span>
        </div>
      </div>

      <div className="stats-grid">
        <StatCard icon={Icons.grid} label="Total slots" value={board.total} />
        <StatCard icon={Icons.check} label="Available" value={board.available} accent="ok" />
        <StatCard icon={Icons.clock} label="Reserved" value={board.reserved} accent="warn" />
        <StatCard icon={Icons.car} label="Occupied" value={board.occupied} accent="bad" />
        <StatCard icon={Icons.wrench} label="Maintenance" value={board.maintenance} accent="gray" />
      </div>

      <div className="capacity-strip" aria-label="Facility occupancy">
        <span className="cap-segment available" style={{ width: `${(board.available / board.total) * 100}%` }} />
        <span className="cap-segment reserved" style={{ width: `${(board.reserved / board.total) * 100}%` }} />
        <span className="cap-segment occupied" style={{ width: `${(board.occupied / board.total) * 100}%` }} />
        <span className="cap-segment maintenance" style={{ width: `${(board.maintenance / board.total) * 100}%` }} />
      </div>
      <div className="capacity-legend">
        <span><i className="dot available" /> {board.available} available</span>
        <span><i className="dot reserved" /> {board.reserved} reserved</span>
        <span><i className="dot occupied" /> {board.occupied} occupied</span>
        <span><i className="dot maintenance" /> {board.maintenance} maintenance</span>
      </div>

      <div className="floor-tabs">
        {floors.map(([floor, slots]) => {
          const free = slots.filter((s) => s.status === 'AVAILABLE').length;
          return (
            <button
              key={floor}
              className={`floor-tab ${floor === activeFloor ? 'active' : ''}`}
              onClick={() => setActiveFloor(floor)}
            >
              {floorName(floor)}
              <span className="floor-free">{free} free</span>
            </button>
          );
        })}
      </div>

      <div className="board-layout">
        <div>
          {floorZones.map(([zoneName, slots]) => {
            const free = slots.filter((s) => s.status === 'AVAILABLE').length;
            const rate = slots[0].hourlyRate;
            const rows = [];
            for (let i = 0; i < slots.length; i += 4) {
              rows.push(slots.slice(i, i + 4));
            }
            return (
              <section className="zone-section" key={zoneName}>
                <div className="zone-header">
                  <span className="zone-letter" style={{ background: ZONE_COLORS[zoneName] || '#2563eb' }}>
                    {zoneName}
                  </span>
                  <div className="zone-meta">
                    <div className="zone-name">
                      Zone {zoneName} <span className="muted">· {formatMoney(rate)}/hr</span>
                    </div>
                    <div className="zone-sub">{free} of {slots.length} slots free</div>
                    <div className="zone-bar">
                      <span style={{ width: `${(free / slots.length) * 100}%` }} />
                    </div>
                  </div>
                </div>
                <div className="lot-map">
                  {rows.map((row, index) => (
                    <RowWithLane
                      key={index}
                      row={row}
                      isLast={index === rows.length - 1}
                      highlighted={highlighted}
                      onSelect={openSlot}
                    />
                  ))}
                </div>
              </section>
            );
          })}
          <div className="legend">
            <span><i className="dot available" /> Available</span>
            <span><i className="dot reserved" /> Reserved</span>
            <span><i className="dot occupied" /> Occupied</span>
            <span><i className="dot maintenance" /> Maintenance</span>
            <span className="muted">EV · EV charging</span>
            <span className="muted">ACC · Accessible</span>
            <span className="muted">CMP · Compact</span>
          </div>
        </div>

        <div className="reserve-form">
          {myWaitlist ? (
            <div className="card queue-card">
              <h3><Icons.clock size={16} style={{ verticalAlign: '-3px' }} /> You're on the waitlist</h3>
              <div className="queue-pos">#{myWaitlist.position}</div>
              <p className="muted" style={{ fontSize: '0.82rem' }}>
                of {myWaitlist.totalQueued} in queue · {myWaitlist.vehicleNumber}
                {myWaitlist.zone ? ` · Zone ${myWaitlist.zone}` : ''}
              </p>
              <p className="muted" style={{ fontSize: '0.8rem' }}>
                A slot will be allocated to you automatically the moment one frees up.
              </p>
              <button className="btn btn-danger btn-sm" onClick={leaveWaitlist}>
                Leave waitlist
              </button>
            </div>
          ) : (
            <form className="card reserve-form" onSubmit={full ? joinWaitlist : reserve}>
              <h3>
                <Icons.zap size={16} style={{ verticalAlign: '-3px' }} />{' '}
                {full ? 'Join the waitlist' : 'Reserve a slot'}
              </h3>
              <p className="muted" style={{ fontSize: '0.82rem' }}>
                {full
                  ? 'The lot is full — queue up and the allocation engine will grab a slot for you as soon as one frees.'
                  : 'The allocation engine picks the best slot instantly — you get a 6-digit entry code with a time-boxed entry window.'}
              </p>
              {vehicles.length > 0 && (
                <label>
                  Saved vehicles
                  <select
                    value=""
                    onChange={(e) => {
                      const chosen = vehicles.find((v) => String(v.id) === e.target.value);
                      if (chosen) setVehicleNumber(chosen.vehicleNumber);
                    }}
                  >
                    <option value="">Pick a saved vehicle…</option>
                    {vehicles.map((vehicle) => (
                      <option key={vehicle.id} value={vehicle.id}>
                        {vehicle.label} · {vehicle.vehicleNumber}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <label>
                Vehicle number
                <input
                  value={vehicleNumber}
                  onChange={(e) => setVehicleNumber(e.target.value.toUpperCase())}
                  placeholder="KA01AB1234"
                  required
                  minLength={4}
                  maxLength={15}
                />
              </label>
              <label>
                Preferred zone
                <select value={zone} onChange={(e) => setZone(e.target.value)}>
                  <option value="">Any zone (ground floor first)</option>
                  {zoneOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label>
                Slot type
                <select value={slotType} onChange={(e) => setSlotType(e.target.value)}>
                  {TYPE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label>
                Expected duration
                <select value={hours} onChange={(e) => setHours(Number(e.target.value))}>
                  {[1, 2, 3, 4, 6, 8].map((h) => (
                    <option key={h} value={h}>{h} hour{h > 1 ? 's' : ''}</option>
                  ))}
                </select>
              </label>
              {!full && (
                <div className="estimate">
                  <span>
                    Estimated parking fee
                    <span style={{ display: 'block', fontSize: '0.68rem' }}>
                      {matchingSlots.length} slots match your selection
                    </span>
                  </span>
                  <strong>{estimateRate != null ? formatMoney(estimateRate * hours) : '—'}</strong>
                </div>
              )}
              <button
                className="btn btn-primary btn-block"
                type="submit"
                disabled={busy || (!full && matchingSlots.length === 0)}
              >
                {busy
                  ? 'Working…'
                  : full
                    ? 'Join waitlist'
                    : matchingSlots.length === 0
                      ? 'No matching slots'
                      : 'Reserve my slot'}
              </button>
            </form>
          )}

          <div className="card activity-card">
            <h3><Icons.activity size={16} style={{ verticalAlign: '-3px' }} /> Live activity</h3>
            {activity.length === 0 ? (
              <p className="muted" style={{ fontSize: '0.8rem' }}>Waiting for the first event…</p>
            ) : (
              activity.map((event, index) => (
                <div className="activity-item" key={`${event.at}-${index}`}>
                  <span className={`activity-dot ${event.type}`} />
                  <div>
                    {event.message}
                    <span className="a-time">
                      {formatTime(event.at)} · {event.slotNumber} · Zone {event.zone} · {floorName(event.floor)}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {selectedSlot && (
        <Modal onClose={() => setSelectedSlot(null)}>
          <h3>Slot {selectedSlot.slotNumber}</h3>
          <p className="modal-sub">
            {floorName(selectedSlot.floor)} · Zone {selectedSlot.zone} · {formatMoney(selectedSlot.hourlyRate)} per hour
            {selectedSlot.type !== 'STANDARD' && (
              <> · {TYPE_OPTIONS.find((t) => t.value === selectedSlot.type)?.label}</>
            )}
          </p>
          <p>
            <span className={`status-pill slot-${selectedSlot.status.toLowerCase()}`}>
              {STATUS_LABELS[selectedSlot.status]}
            </span>
          </p>

          {session && (
            <div className="session-rows">
              {session.vehicleNumber && (
                <div className="session-row">
                  <span className="s-label">Vehicle</span>
                  <span className="s-value mono">{session.vehicleNumber}</span>
                </div>
              )}
              {selectedSlot.status === 'RESERVED' && session.holdExpiresAt && (
                <div className="session-row">
                  <span className="s-label">Hold expires</span>
                  <span className="s-value">{formatTime(session.holdExpiresAt)}</span>
                </div>
              )}
              {selectedSlot.status === 'OCCUPIED' && session.entryTime && (
                <>
                  <div className="session-row">
                    <span className="s-label">Parked since</span>
                    <span className="s-value">{formatTime(session.entryTime)}</span>
                  </div>
                  <div className="session-row">
                    <span className="s-label">Elapsed</span>
                    <span className="s-value">{session.elapsedMinutes} min</span>
                  </div>
                  <div className="session-row">
                    <span className="s-label">Booked duration</span>
                    <span className="s-value">{session.expectedHours + session.extensionHours}h</span>
                  </div>
                  <div className="session-row">
                    <span className="s-label">Running cost</span>
                    <span className="s-value amount-tag">{formatMoney(session.currentCost)}</span>
                  </div>
                </>
              )}
              {selectedSlot.status === 'MAINTENANCE' && session.maintenanceNote && (
                <div className="session-row">
                  <span className="s-label">Reason</span>
                  <span className="s-value">{session.maintenanceNote}</span>
                </div>
              )}
            </div>
          )}

          {selectedSlot.status === 'AVAILABLE' && (
            <button
              className="btn btn-primary btn-block"
              style={{ marginTop: '1rem' }}
              onClick={() => {
                setZone(selectedSlot.zone);
                if (selectedSlot.type !== 'STANDARD') setSlotType(selectedSlot.type);
                setSelectedSlot(null);
                document.querySelector('.reserve-form input')?.focus();
              }}
            >
              Reserve in Zone {selectedSlot.zone}
            </button>
          )}
        </Modal>
      )}
    </div>
  );
}

function RowWithLane({ row, isLast, highlighted, onSelect }) {
  return (
    <>
      {row.map((slot) => (
        <div
          key={`${slot.id}-${slot.status}`}
          data-slot-id={slot.id}
          className={[
            'slot-card',
            slot.status.toLowerCase(),
            slot.type.toLowerCase(),
            slot.justChanged ? 'flash' : '',
            highlighted === slot.id ? 'mine' : '',
          ].join(' ')}
          onClick={() => onSelect(slot)}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && onSelect(slot)}
        >
          {slot.type !== 'STANDARD' && (
            <span className="slot-type">{TYPE_SHORT[slot.type]}</span>
          )}
          <span className="slot-no">{slot.slotNumber}</span>
          <span className="slot-rate">{formatMoney(slot.hourlyRate)}/hr</span>
          <span className="slot-status">{STATUS_LABELS[slot.status]}</span>
        </div>
      ))}
      <div className="drive-lane">
        <span>← Entry</span>
        <span>Drive lane · 6 m</span>
        <span>Exit →</span>
      </div>
    </>
  );
}
