import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useToast } from '../components/Toast.jsx';
import { Icons, StatCard } from '../components/ui.jsx';
import { useSlots } from '../ws/useSlots.js';
import { downloadCsv } from '../utils/download.js';
import { formatMoney, formatTime } from '../utils/format.js';

export default function AdminDashboard() {
  const [tab, setTab] = useState('overview');

  return (
    <div>
      <h1>Admin Dashboard</h1>
      <p className="page-sub">Revenue, occupancy and fleet health at a glance.</p>

      <div className="tabs">
        <button className={`tab ${tab === 'overview' ? 'active' : ''}`} onClick={() => setTab('overview')}>
          Analytics
        </button>
        <button className={`tab ${tab === 'sessions' ? 'active' : ''}`} onClick={() => setTab('sessions')}>
          Sessions
        </button>
        <button className={`tab ${tab === 'users' ? 'active' : ''}`} onClick={() => setTab('users')}>
          Users
        </button>
      </div>

      {tab === 'overview' && <OverviewTab />}
      {tab === 'sessions' && <SessionsTab />}
      {tab === 'users' && <UsersTab />}
    </div>
  );
}

/* ---------------- Analytics ---------------- */

function OverviewTab() {
  const toast = useToast();
  const { board } = useSlots();
  const [report, setReport] = useState(null);
  const [gateStatus, setGateStatus] = useState(null);
  const [slotNumber, setSlotNumber] = useState('');
  const [zone, setZone] = useState('A');
  const [hourlyRate, setHourlyRate] = useState('6');
  const [slotType, setSlotType] = useState('STANDARD');
  const [announcement, setAnnouncement] = useState('');

  const loadReport = useCallback(async () => {
    try {
      const [reportResponse, gateResponse] = await Promise.all([
        api.get('/reports/usage'),
        api.get('/gates/status'),
      ]);
      setReport(reportResponse.data);
      setGateStatus(gateResponse.data);
    } catch {
      /* role guard prevents this page for non-admins */
    }
  }, []);

  useEffect(() => {
    loadReport();
    const id = setInterval(loadReport, 30000);
    return () => clearInterval(id);
  }, [loadReport]);

  const createSlot = async (event) => {
    event.preventDefault();
    try {
      await api.post('/slots', {
        slotNumber,
        zone,
        hourlyRate: hourlyRate ? Number(hourlyRate) : null,
        type: slotType,
      });
      toast('success', `Slot ${slotNumber.toUpperCase()} created in Zone ${zone}.`);
      setSlotNumber('');
    } catch (err) {
      toast('error', err.response?.data?.error || 'Create failed.');
    }
  };

  const deleteSlot = async (slot) => {
    try {
      await api.delete(`/slots/${slot.id}`);
      toast('success', `Slot ${slot.slotNumber} deleted.`);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Delete failed.');
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

  const sendAnnouncement = async (event) => {
    event.preventDefault();
    try {
      await api.post('/admin/announcements', { message: announcement });
      toast('success', 'Announcement broadcast to all connected users.');
      setAnnouncement('');
    } catch (err) {
      toast('error', err.response?.data?.error || 'Announcement failed.');
    }
  };

  const toggleSimulator = async () => {
    try {
      const response = await api.post('/simulator/toggle', { enabled: !gateStatus.simulatorEnabled });
      setGateStatus({ ...gateStatus, simulatorEnabled: response.data.enabled });
      toast('success', `Sensor simulator ${response.data.enabled ? 'enabled' : 'disabled'}.`);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Toggle failed.');
    }
  };

  const tickSimulator = async () => {
    try {
      const response = await api.post('/simulator/tick');
      setGateStatus({ ...gateStatus, simulatorEnabled: response.data.enabled });
      toast('success', `Cycle complete — ${response.data.simulatedInside} simulated vehicles inside.`);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Tick failed.');
    }
  };

  const maxZoneRevenue = report
    ? Math.max(...report.zoneRevenues.map((z) => z.revenue), 1)
    : 1;
  const maxHourly = report
    ? Math.max(...report.hourlyOccupancy.map((h) => h.checkIns), 1)
    : 1;
  const maxDaily = report
    ? Math.max(...report.dailyTrend.map((d) => d.revenue), 1)
    : 1;

  const bestHour = useMemo(() => {
    if (!report) return null;
    // Suggest the quietest daytime hour (7:00-21:59).
    let best = null;
    for (const stat of report.hourlyOccupancy) {
      if (stat.hour >= 7 && stat.hour <= 21) {
        if (best === null || stat.checkIns < best.checkIns) {
          best = stat;
        }
      }
    }
    return best;
  }, [report]);

  return (
    <>
      {report && (
        <>
          <div className="stats-grid">
            <StatCard
              icon={Icons.money}
              label="Total revenue"
              value={report.totalRevenue}
              format={(v) => formatMoney(v)}
              accent="ok"
            />
            <StatCard
              icon={Icons.trending}
              label="Occupancy rate"
              value={report.occupancyRatePercent}
              format={(v) => `${v.toFixed(1)}%`}
              accent="cyan"
            />
            <StatCard icon={Icons.car} label="Sessions today" value={report.sessionsToday} accent="warn" />
            <StatCard
              icon={Icons.clock}
              label="Avg. duration"
              value={report.averageDurationMinutes}
              format={(v) => `${Math.round(v)} min`}
            />
            <StatCard
              icon={Icons.activity}
              label="Live occupancy"
              value={`${report.occupied}/${report.totalSlots}`}
              accent="bad"
            />
          </div>

          <div className="card">
            <h3>Revenue by zone</h3>
            {report.zoneRevenues.length === 0 ? (
              <p className="muted">No completed sessions yet.</p>
            ) : (
              <div className="rev-bars">
                {report.zoneRevenues.map((zoneRevenue) => (
                  <div className="rev-row" key={zoneRevenue.zone}>
                    <span>Zone {zoneRevenue.zone}</span>
                    <div className="rev-track">
                      <div
                        className="rev-fill"
                        style={{ width: `${(zoneRevenue.revenue / maxZoneRevenue) * 100}%` }}
                      />
                    </div>
                    <span className="rev-value">{formatMoney(zoneRevenue.revenue)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <h3>Demand heatmap (check-ins by hour)</h3>
            <div className="heat-grid">
              {report.hourlyOccupancy
                .filter((h) => h.hour % 2 === 0 || h.checkIns > 0)
                .map((stat) => (
                  <div className="heat-col" key={stat.hour}>
                    <div
                      className="heat-bar"
                      style={{ height: `${Math.max(3, (stat.checkIns / maxHourly) * 100)}%` }}
                      title={`${stat.checkIns} check-ins at ${stat.hour}:00`}
                    />
                    <span className="heat-label">{stat.hour}</span>
                  </div>
                ))}
            </div>
            {bestHour && (
              <span className="hint-chip">
                <Icons.pin size={13} />
                Quietest window: around {bestHour.hour}:00 — {bestHour.checkIns} check-ins on record
              </span>
            )}
          </div>

          <div className="card">
            <h3>Revenue trend (last 7 days)</h3>
            <div className="daily-grid">
              {report.dailyTrend.map((day) => (
                <div className="daily-col" key={day.date}>
                  <div
                    className="daily-bar"
                    style={{ height: `${Math.max(3, (day.revenue / maxDaily) * 100)}%` }}
                    title={`${formatMoney(day.revenue)} · ${day.sessions} sessions`}
                  />
                  <span className="heat-label">{day.date.slice(5)}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      <div className="card">
        <h3><Icons.activity size={16} style={{ verticalAlign: '-3px' }} /> Gates &amp; sensor simulator</h3>
        {gateStatus && (
          <>
            <div className="gate-stats">
              <div className="gate-stat">
                <div className="g-value">{gateStatus.entries}</div>
                <div className="g-label">Gate entries</div>
              </div>
              <div className="gate-stat">
                <div className="g-value">{gateStatus.exits}</div>
                <div className="g-label">Gate exits</div>
              </div>
              <div className="gate-stat">
                <div className="g-value">{gateStatus.insideByGates}</div>
                <div className="g-label">Inside (barriers)</div>
              </div>
              <div className="gate-stat">
                <div className="g-value">{gateStatus.occupiedSlots}</div>
                <div className="g-label">Occupied slots</div>
              </div>
              <div className="gate-stat">
                <div className={`g-value ${gateStatus.anomalies > 0 ? 'bad-value' : ''}`}>
                  {gateStatus.anomalies}
                </div>
                <div className="g-label">Anomalies</div>
              </div>
              <div className="gate-stat">
                <div className="g-value">{gateStatus.simulatedVehiclesInside}</div>
                <div className="g-label">Simulated inside</div>
              </div>
            </div>
            <div className="gate-actions">
              <span className={`sim-state ${gateStatus.simulatorEnabled ? 'on' : 'off'}`}>
                Simulator {gateStatus.simulatorEnabled ? 'ON' : 'OFF'}
              </span>
              <button className="btn btn-secondary btn-sm" onClick={toggleSimulator}>
                {gateStatus.simulatorEnabled ? 'Disable' : 'Enable'}
              </button>
              <button className="btn btn-secondary btn-sm" onClick={tickSimulator}>
                Run cycle now
              </button>
              <span className="muted" style={{ fontSize: '0.76rem' }}>
                Target occupancy {(gateStatus.targetOccupancy * 100).toFixed(0)}% ·
                barrier-vs-slot mismatch and tailgating are flagged as anomalies in the activity feed.
              </span>
            </div>
          </>
        )}
      </div>

      <div className="card">
        <h3><Icons.megaphone size={16} style={{ verticalAlign: '-3px' }} /> Broadcast announcement</h3>
        <form className="inline-form" onSubmit={sendAnnouncement}>
          <input
            placeholder="e.g. Zone B closed for maintenance until 2 PM"
            value={announcement}
            onChange={(e) => setAnnouncement(e.target.value)}
            required
            maxLength={200}
            style={{ flex: 1, minWidth: '260px' }}
          />
          <button className="btn btn-primary" type="submit">Broadcast</button>
        </form>
        <p className="muted" style={{ fontSize: '0.78rem', margin: '0.6rem 0 0' }}>
          Shows instantly as a banner + toast for every connected user.
        </p>
      </div>

      <div className="card">
        <h3>Add a slot</h3>
        <form className="inline-form" onSubmit={createSlot}>
          <input
            placeholder="Slot number (e.g. D-01)"
            value={slotNumber}
            onChange={(e) => setSlotNumber(e.target.value.toUpperCase())}
            required
            style={{ minWidth: '190px' }}
          />
          <select value={zone} onChange={(e) => setZone(e.target.value)}>
            {['A', 'B', 'C', 'D', 'E'].map((z) => (
              <option key={z} value={z}>Zone {z}</option>
            ))}
          </select>
          <select value={slotType} onChange={(e) => setSlotType(e.target.value)}>
            <option value="STANDARD">Standard</option>
            <option value="EV_CHARGING">EV charging</option>
            <option value="ACCESSIBLE">Accessible</option>
            <option value="COMPACT">Compact</option>
          </select>
          <input
            type="number"
            step="0.5"
            min="0"
            value={hourlyRate}
            onChange={(e) => setHourlyRate(e.target.value)}
            placeholder="₹/hr"
            style={{ width: '90px' }}
          />
          <button className="btn btn-primary" type="submit">Create slot</button>
        </form>
      </div>

      <div className="card">
        <h3>Manage slots</h3>
        {board && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Slot</th>
                  <th>Floor</th>
                  <th>Zone</th>
                  <th>Type</th>
                  <th>Rate</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {board.slots.map((slot) => (
                  <tr key={slot.id}>
                    <td className="mono">{slot.slotNumber}</td>
                    <td>{slot.floor === 1 ? 'Ground' : `Level ${slot.floor - 1}`}</td>
                    <td>{slot.zone}</td>
                    <td>{slot.type === 'STANDARD' ? '—' : slot.type.replace('_', ' ').toLowerCase()}</td>
                    <td>{formatMoney(slot.hourlyRate)}/hr</td>
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
                            Maintenance
                          </button>
                        )}
                        {slot.status === 'MAINTENANCE' && (
                          <button
                            className="btn btn-primary btn-sm"
                            onClick={() => setSlotStatus(slot, 'AVAILABLE')}
                          >
                            Available
                          </button>
                        )}
                        <button
                          className="btn btn-danger btn-sm"
                          onClick={() => deleteSlot(slot)}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

/* ---------------- Sessions ---------------- */

function SessionsTab() {
  const toast = useToast();
  const [filters, setFilters] = useState({ status: '', zone: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (filters.status) params.set('status', filters.status);
      if (filters.zone) params.set('zone', filters.zone);
      if (filters.from) params.set('from', filters.from);
      if (filters.to) params.set('to', filters.to);
      params.set('page', String(page));
      params.set('size', '15');
      const response = await api.get(`/reports/sessions?${params.toString()}`);
      setData(response.data);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Failed to load sessions.');
    }
  }, [filters, page, toast]);

  useEffect(() => {
    load();
  }, [load]);

  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / 15)) : 1;

  const exportCsv = async () => {
    try {
      await downloadCsv('/reports/export', 'parking-sessions.csv');
      toast('success', 'Sessions exported to CSV.');
    } catch {
      toast('error', 'Export failed.');
    }
  };

  return (
    <div className="card">
      <div className="card-header">
        <h3 style={{ margin: 0 }}>Session log</h3>
        <button className="btn btn-secondary btn-sm" onClick={exportCsv}>
          <Icons.download size={14} /> Export CSV
        </button>
      </div>

      <div className="filter-row">
        <select
          value={filters.status}
          onChange={(e) => { setPage(0); setFilters({ ...filters, status: e.target.value }); }}
        >
          <option value="">Any status</option>
          <option value="ACTIVE">Active</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <select
          value={filters.zone}
          onChange={(e) => { setPage(0); setFilters({ ...filters, zone: e.target.value }); }}
        >
          <option value="">Any zone</option>
          {['A', 'B', 'C', 'D', 'E'].map((z) => (
            <option key={z} value={z}>Zone {z}</option>
          ))}
        </select>
        <input
          type="date"
          value={filters.from}
          onChange={(e) => { setPage(0); setFilters({ ...filters, from: e.target.value }); }}
        />
        <input
          type="date"
          value={filters.to}
          onChange={(e) => { setPage(0); setFilters({ ...filters, to: e.target.value }); }}
        />
      </div>

      {!data ? (
        <p className="muted">Loading sessions…</p>
      ) : data.content.length === 0 ? (
        <div className="empty-state">
          <span className="big"><Icons.search size={26} /></span>
          No sessions match the filters.
        </div>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Vehicle</th>
                <th>Slot</th>
                <th>Status</th>
                <th>Reserved</th>
                <th>Entry</th>
                <th>Exit</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((r) => (
                <tr key={r.id}>
                  <td className="mono">#{r.id}</td>
                  <td className="mono">{r.vehicleNumber}</td>
                  <td>{r.slotNumber}</td>
                  <td>
                    <span className={`status-pill ${r.status.toLowerCase()}`}>{r.status}</span>
                  </td>
                  <td>{formatTime(r.reservedAt)}</td>
                  <td>{formatTime(r.entryTime)}</td>
                  <td>{formatTime(r.exitTime)}</td>
                  <td>{r.amount != null ? formatMoney(r.amount) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="pager">
        <button
          className="btn btn-secondary btn-sm"
          disabled={page === 0}
          onClick={() => setPage(page - 1)}
        >
          ← Prev
        </button>
        <span>Page {page + 1} of {totalPages}</span>
        <button
          className="btn btn-secondary btn-sm"
          disabled={!data || page + 1 >= totalPages}
          onClick={() => setPage(page + 1)}
        >
          Next →
        </button>
      </div>
    </div>
  );
}

/* ---------------- Users ---------------- */

function UsersTab() {
  const toast = useToast();
  const { user: me } = useAuth();
  const [users, setUsers] = useState(null);

  const load = useCallback(async () => {
    try {
      const response = await api.get('/admin/users');
      setUsers(response.data);
    } catch (err) {
      toast('error', err.response?.data?.error || 'Failed to load users.');
    }
  }, [toast]);

  useEffect(() => {
    load();
  }, [load]);

  const changeRole = async (target, role) => {
    try {
      await api.patch(`/admin/users/${target.id}/role`, { role });
      toast('success', `${target.name} is now ${role.toLowerCase()}.`);
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Role update failed.');
    }
  };

  const toggleEnabled = async (target) => {
    try {
      await api.patch(`/admin/users/${target.id}/enabled`, { enabled: !target.enabled });
      toast('success', `${target.name} ${target.enabled ? 'disabled' : 'enabled'}.`);
      await load();
    } catch (err) {
      toast('error', err.response?.data?.error || 'Update failed.');
    }
  };

  if (!users) {
    return <p className="muted">Loading users…</p>;
  }

  return (
    <div className="card">
      <h3>User management</h3>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Joined</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => {
              const isSelf = me && me.id === user.id;
              return (
                <tr key={user.id}>
                  <td>{user.name}{isSelf && <span className="muted"> (you)</span>}</td>
                  <td>{user.email}</td>
                  <td>
                    <select
                      value={user.role}
                      disabled={isSelf}
                      onChange={(e) => changeRole(user, e.target.value)}
                    >
                      <option value="USER">User</option>
                      <option value="ATTENDANT">Attendant</option>
                      <option value="ADMIN">Admin</option>
                    </select>
                  </td>
                  <td>
                    <span className={`status-pill ${user.enabled ? 'completed' : 'cancelled'}`}>
                      {user.enabled ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td>{formatTime(user.createdAt).split(',')[0]}</td>
                  <td>
                    <button
                      className={`btn ${user.enabled ? 'btn-danger' : 'btn-primary'} btn-sm`}
                      disabled={isSelf}
                      onClick={() => toggleEnabled(user)}
                    >
                      {user.enabled ? 'Disable' : 'Enable'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="muted" style={{ fontSize: '0.78rem', margin: '0.6rem 0 0' }}>
        Disabled accounts can't log in and their existing sessions are blocked immediately.
      </p>
    </div>
  );
}
