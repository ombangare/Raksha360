import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity, AlertCircle, AlertOctagon, AlertTriangle, ArrowDownRight, ArrowRight, ArrowUpRight,
  BadgeCheck, BellRing, BookOpenCheck, Check, CheckCircle2, ChevronDown,
  CircleHelp, ClipboardList, Clock3, Download, ExternalLink, FileClock, FileText,
  Filter, Fingerprint, Flame, Gauge, HeartPulse, Layers3, LocateFixed, LockKeyhole,
  LogOut, Map, MapPin, MapPinned, Menu, MessageSquareText, MoreHorizontal, Navigation,
  PhoneCall, Radio, RadioTower, RefreshCw, Search, Send, Settings2, ShieldAlert, ShieldCheck,
  Siren, SlidersHorizontal, Smartphone, UserRound, Users, X, Zap,
} from 'lucide-react';
import { api, createLiveSocket, formatTime, getApiBase, isApiConfigured, signalTone, timeAgo, verifyDashboardToken } from './api.js';
import MapLibreMap from './components/MapLibreMap.jsx';
import NagpurAnimatedLogin from './components/NagpurAnimatedLogin.jsx';

const NAV = [
  { id: 'overview', label: 'Command center', icon: Gauge, group: 'OPERATIONS' },
  { id: 'map', label: 'Live operations map', icon: MapPinned, group: 'OPERATIONS' },
  { id: 'incidents', label: 'SOS & incidents', icon: Siren, group: 'OPERATIONS', badgeKey: 'openIncidents' },
  { id: 'devices', label: 'Device watch', icon: Smartphone, group: 'OPERATIONS' },
  { id: 'reports', label: 'Reports & complaints', icon: ClipboardList, group: 'CASEWORK', badgeKey: 'openReports' },
  { id: 'dispatch', label: 'Dispatch log', icon: RadioTower, group: 'CASEWORK', badgeKey: 'pendingDispatches' },
  { id: 'stations', label: 'Stations & directory', icon: Users, group: 'REFERENCE' },
  { id: 'ncrb', label: 'NCRB crime stats', icon: BookOpenCheck, group: 'REFERENCE' },
  { id: 'audit', label: 'Audit trail', icon: FileClock, group: 'REFERENCE' },
];

const statusOptions = ['New', 'Acknowledged', 'Assigned', 'Responding', 'Resolved', 'False Alarm', 'Review Required'];
const reportStatusOptions = ['New', 'Under Review', 'Assigned', 'Closed', 'Rejected'];
const dispatchStatusOptions = ['REQUESTED', 'ACKNOWLEDGED_IN_CONTROL_ROOM', 'UNIT_EN_ROUTE_REPORTED', 'ON_SCENE_REPORTED', 'COMPLETED', 'CANCELLED'];
const triggerLabels = {
  PANIC_BUTTON: 'Manual SOS', MANUAL_SOS: 'Manual SOS', VOICE_SOS: 'Voice SOS', CHILD_SOS: 'Child SOS',
  LOST_CHILD: 'Lost-child help', JOURNEY_TIMEOUT: 'Overdue journey', GEOFENCE_EXIT: 'Safe-place boundary',
  DEVICE_OFFLINE: 'Phone stopped checking in',
  MISSED_CHECK_IN: 'Missed check-in', SCHOOL_HANDOVER_ESCALATION: 'School handover', DEMO_SIMULATION: 'Demo event',
};

function cx(...parts) { return parts.filter(Boolean).join(' '); }
function labelTrigger(value) { return triggerLabels[value] || String(value || 'Safety event').replaceAll('_', ' ').toLowerCase().replace(/^./, (m) => m.toUpperCase()); }
function isOpenStatus(value) { return !['Resolved', 'False Alarm', 'Closed', 'Rejected', 'COMPLETED', 'CANCELLED'].includes(value); }

function TonePill({ value, demo = false }) {
  const tone = signalTone(value);
  return <span className={cx('tone-pill', `tone-${tone}`)}><i className="tiny-dot" />{demo ? 'DEMO' : value || 'Unknown'}</span>;
}

function Signal({ tone = 'green', label, pulse = false }) {
  return <span className="signal-row"><i className={cx('signal-dot', `signal-${tone}`, pulse && 'signal-pulse')} />{label}</span>;
}

function MetricCard({ icon: Icon, label, value, note, tone = 'blue', trend }) {
  return (
    <article className={`metric-card metric-${tone}`}>
      <div className="metric-head">
        <span className="metric-icon"><Icon size={18} /></span>
        <span className="metric-label">{label}</span>
        {trend && <span className="metric-trend">{trend}</span>}
      </div>
      <div className="metric-value">{value ?? '—'}</div>
      <div className="metric-note">{note}</div>
    </article>
  );
}

function LoadingRows({ rows = 5 }) {
  return <div className="loading-rows">{Array.from({ length: rows }, (_, index) => <div key={index} className="loading-row"><i /><span /><b /></div>)}</div>;
}

function SectionHeading({ eyebrow, title, description, actions }) {
  return (
    <div className="section-heading">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      <div className="heading-actions">{actions}</div>
    </div>
  );
}

function EmptyState({ icon: Icon = CircleHelp, title, detail }) {
  return <div className="empty-state"><span><Icon size={24} /></span><strong>{title}</strong><p>{detail}</p></div>;
}

function AuthGate({ onLogin, busy, error, onHealth, health }) {
  const [token, setToken] = useState('');
  const [showToken, setShowToken] = useState(false);
  return (
    <div className="login-screen">
      <div className="login-grid" />
      <div className="login-left">
        <div className="brand-lockup">
          <div className="brand-mark"><ShieldCheck size={26} /></div>
          <div><b>RAKSHA<span>360</span></b><small>SAFETY OPERATIONS NETWORK</small></div>
        </div>
        <div className="login-message">
          <div className="eyebrow gold"><span className="signal-dot signal-amber" /> CONTROL ROOM ACCESS</div>
          <h1>Every alert.<br /><em>One response.</em></h1>
          <p>Live incidents, geospatial intelligence, and case coordination in a single protected workspace.</p>
          <div className="login-feature">
            <div><RadioTower size={17} /><span>Real-time incident stream</span></div>
            <div><MapPinned size={17} /><span>Location-aware response map</span></div>
            <div><ShieldAlert size={17} /><span>Auditable incident handling</span></div>
          </div>
        </div>
        <div className="login-footer">
          RAKSHA360 CONTROL ROOM <span>•</span> {isApiConfigured() ? 'LOCAL DEVELOPMENT' : 'PUBLIC PREVIEW'}
        </div>
      </div>
      <div className="login-right">
        <form className="login-card glass-panel" onSubmit={(event) => { event.preventDefault(); onLogin(token); }}>
          <div className="login-card-icon"><LockKeyhole size={22} /></div>
          <div className="eyebrow">SECURE OPERATOR SESSION</div>
          <p>
            Enter the operator dashboard access token configured for this Control Room environment.
          </p>
          <label className="field-label" htmlFor="operatorToken">Dashboard access token</label>
          <div className="token-field">
            <input
              id="operatorToken"
              type={showToken ? 'text' : 'password'}
              placeholder="Enter dashboard token"
              value={token}
              onChange={(event) => setToken(event.target.value)}
              autoComplete="current-password"
              required
            />
            <button type="button" className="icon-button" title={showToken ? 'Hide token' : 'Show token'} onClick={() => setShowToken((value) => !value)}>
              <Fingerprint size={16} />
            </button>
          </div>
          {error && <div className="inline-error"><AlertTriangle size={15} />{error}</div>}
          <button className="primary-button full-width" type="submit" disabled={busy}>
            {busy ? 'Verifying session…' : <>Enter Control Room <ArrowRight size={16} /></>}
          </button>
          <button type="button" className="subtle-button full-width" style={{ marginTop: 8 }} onClick={onHealth}>
            <Activity size={15} /> {health ? `API ${health.status || 'reachable'}${health.database ? ` · ${health.database}` : ''}` : 'Check backend health'}
          </button>
          <div className="login-note">
            <i className={cx('signal-dot', isApiConfigured() ? 'signal-amber' : 'signal-blue')} />{' '}
            {isApiConfigured()
              ? 'Local prototype — no official police gateway connected'
              : 'Public preview — remote HTTPS backend pending provisioning'}
          </div>
        </form>
      </div>
    </div>
  );
}

function IncidentRow({ incident, active, onClick }) {
  return (
    <button className={cx('incident-row', active && 'incident-row-active')} onClick={onClick}>
      <span className={cx('incident-rail', `rail-${signalTone(incident.severity)}`)} />
      <span className="incident-row-main">
        <span className="incident-row-title">
          <b>{incident.event_id}</b>
          {incident.isDemo && <span className="demo-tag">DEMO</span>}
        </span>
        <span className="incident-row-sub">
          {labelTrigger(incident.trigger_type)} <span>·</span> {incident.location_description || 'Location unavailable'}
        </span>
        <span className="incident-row-meta">
          <span className="meta-clock"><Clock3 size={12} />{timeAgo(incident.reported_at)}</span>
          <TonePill value={incident.status} />
        </span>
      </span>
      <span className="incident-row-right">
        <TonePill value={incident.severity} />
        <ArrowUpRight size={15} />
      </span>
    </button>
  );
}

function NearbyPoliceStationsSection({ token, incident, onProposeDispatch }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!incident?.event_id || !token) {
      setData(null);
      return;
    }
    const hasValidCoords = (incident.latitude != null && incident.longitude != null) ||
                           (incident.lastLatitude != null && incident.lastLongitude != null) ||
                           (incident.last_latitude != null && incident.last_longitude != null);
    if (!hasValidCoords) {
      setData({ locationAvailable: false, stations: [] });
      setLoading(false);
      return;
    }

    let isMounted = true;
    setLoading(true);
    setError(null);
    setData(null);

    api.nearbyStations(token, incident.event_id, { limit: 5, radius_km: 10 })
      .then((res) => {
        if (isMounted) {
          setData(res);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.message);
          setLoading(false);
        }
      });

    return () => { isMounted = false; };
  }, [token, incident?.event_id, incident?.latitude, incident?.longitude]);

  if (!incident) return null;

  const hasCoords = (incident.latitude != null && incident.longitude != null) ||
                    (incident.lastLatitude != null && incident.lastLongitude != null) ||
                    (incident.last_latitude != null && incident.last_longitude != null);

  return (
    <div className="nearby-stations-section">
      <div className="nearby-stations-header">
        <div className="nearby-title-row">
          <MapPin size={13} style={{ color: 'var(--blue)' }} />
          <h4>Nearby Police Stations</h4>
          {data?.stations?.length > 0 && <span className="counter-pill">{data.stations.length}</span>}
        </div>
        <div className="nearby-disclaimer">
          Nearby public-directory stations — distance estimate; confirm jurisdiction and availability.
        </div>
      </div>

      {!hasCoords ? (
        <div className="nearby-empty-notice">
          <AlertCircle size={14} style={{ color: 'var(--amber)', flexShrink: 0 }} />
          <span>Location unavailable — nearby stations cannot be ranked yet.</span>
        </div>
      ) : loading ? (
        <div className="nearby-loading">
          <div className="loading-orbit-small" />
          <span>Finding closest Nagpur police stations (10 km)…</span>
        </div>
      ) : error ? (
        <div className="nearby-empty-notice">
          <span>Failed to query nearby stations: {error}</span>
        </div>
      ) : data?.stations?.length === 0 ? (
        <div className="nearby-empty-notice">
          <span>No police stations located within {data?.radiusKm || 10} km of this event.</span>
        </div>
      ) : (
        <div className="nearby-stations-list">
          {data.stations.map((st) => (
            <div key={st.stationId} className="nearby-station-card">
              <div className="nearby-card-top">
                <div className="nearby-names">
                  <span className="station-en">{st.name}</span>
                  {st.nameMr && <span className="station-mr">{st.nameMr}</span>}
                </div>
                <span className="distance-badge">{Number(st.distanceKm).toFixed(1)} km</span>
              </div>
              <div className="nearby-address">{st.address}</div>
              <div className="nearby-provenance-row">
                <span className={`coord-status-tag ${st.coordinateStatus === 'HUMAN_VERIFIED_COORDINATE' ? 'tag-verified' : 'tag-candidate'}`}>
                  {st.coordinateStatus === 'HUMAN_VERIFIED_COORDINATE' ? 'VERIFIED' : 'OSM CANDIDATE'}
                </span>
                <span className="provenance-note" title={st.coordinateSource}>{st.coordinateSource}</span>
              </div>
              <div className="nearby-actions">
                {st.phone ? (
                  <a href={st.telUri || `tel:${st.phone}`} className="nearby-action-btn action-call" title={`Call ${st.phone}`}>
                    <PhoneCall size={11} /> Call {st.phone.split('/')[0].trim()}
                  </a>
                ) : (
                  <span className="phone-unavailable">No phone</span>
                )}
                {st.osmDirectionsUrl && (
                  <a href={st.osmDirectionsUrl} target="_blank" rel="noreferrer" className="nearby-action-btn action-dir" title="Open OpenStreetMap directions">
                    <Navigation size={11} /> Directions (OSM)
                  </a>
                )}
                {onProposeDispatch && (
                  <button
                    type="button"
                    className="nearby-action-btn action-propose"
                    onClick={() => onProposeDispatch(st)}
                    title="Propose this station as internal reference"
                  >
                    Propose reference
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

function SpatialInspector({ incident, token, onClose, onOpenFull, onStatus, onOpenDispatch, busy }) {
  if (!incident) return null;
  const isOffline = incident.trigger_type === 'DEVICE_OFFLINE';
  return (
    <div className="spatial-inspector">
      <div className="spatial-inspector-header">
        <div>
          <h3>{incident.event_id}</h3>
          <p>{labelTrigger(incident.trigger_type)}</p>
        </div>
        <button className="icon-button" onClick={onClose} title="Close inspector" style={{ width: 28, height: 28 }}>
          <X size={14} />
        </button>
      </div>
      <div className="spatial-inspector-body">
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <TonePill value={incident.severity} />
          <TonePill value={incident.status} />
          {incident.isDemo && <span className="demo-tag">DEMO</span>}
        </div>
        {isOffline && (
          <div className="warning-banner" style={{ margin: '2px 0', padding: '8px 10px', fontSize: 11 }}>
            <AlertTriangle size={14} />
            <span><b>LAST KNOWN LOCATION · NOT LIVE</b><br />Phone stopped checking in. Stale GPS.</span>
          </div>
        )}
        <div className="spatial-fact">
          <small>Received</small>
          <b>{formatTime(incident.reported_at)} ({timeAgo(incident.reported_at)})</b>
        </div>
        <div className="spatial-fact">
          <small>Location Description</small>
          <b>{incident.location_description || 'Location unavailable'}</b>
        </div>
        {incident.latitude != null && incident.longitude != null && (
          <div className="spatial-fact">
            <small>Coordinates</small>
            <b>{Number(incident.latitude).toFixed(4)}, {Number(incident.longitude).toFixed(4)}</b>
            <a
              href={`https://www.openstreetmap.org/?mlat=${incident.latitude}&mlon=${incident.longitude}#map=17/${incident.latitude}/${incident.longitude}`}
              target="_blank"
              rel="noreferrer"
              className="table-link"
            >
              Open in OpenStreetMap <ExternalLink size={11} />
            </a>
          </div>
        )}

        {/* Nearby Police Stations */}
        <NearbyPoliceStationsSection
          token={token}
          incident={incident}
          onProposeDispatch={onOpenDispatch ? (st) => onOpenDispatch(incident, st) : null}
        />

        <div className="spatial-actions">
          <button className="primary-button full-width" onClick={onOpenFull}>
            <FileText size={14} /> View full case details
          </button>
          {onOpenDispatch && (
            <button
              className="gold-button full-width"
              disabled={busy || incident.status === 'Resolved' || incident.status === 'False Alarm'}
              onClick={() => onOpenDispatch(incident)}
              title="Create internal control room assignment (not sent to police)"
            >
              <RadioTower size={14} /> Create internal dispatch task
            </button>
          )}
          {incident.status === 'New' && (
            <button
              className="subtle-button full-width"
              disabled={busy}
              onClick={() => onStatus(incident, 'Acknowledged', incident.notes || '')}
            >
              <CheckCircle2 size={14} /> Acknowledge alert
            </button>
          )}
          {incident.status === 'Acknowledged' && (
            <button
              className="subtle-button full-width"
              disabled={busy}
              onClick={() => onStatus(incident, 'Responding', incident.notes || '')}
            >
              <Navigation size={14} /> Mark responding
            </button>
          )}
          <a className="outline-button full-width anchor-button" href="tel:112">
            <Navigation size={14} /> Open dialer for 112
          </a>
        </div>
      </div>
    </div>
  );
}

function Modal({ title, description, onClose, children, wide = false }) {
  useEffect(() => {
    const key = (event) => { if (event.key === 'Escape') onClose?.(); };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose?.(); }}>
      <section className={cx('modal-card', wide && 'modal-wide')} role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <div>
            <h2>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button className="icon-button" onClick={onClose} title="Close"><X size={18} /></button>
        </header>
        {children}
      </section>
    </div>
  );
}

function Field({ label, children, hint }) {
  return <label className="form-field"><span className="field-label">{label}</span>{children}{hint && <small>{hint}</small>}</label>;
}

function IncidentDetail({ incident, dispatches, stations = [], publicStations = [], onClose, onStatus, onDispatch, busy, token, toast }) {
  const [status, setStatus] = useState(incident.status || 'New');
  const [notes, setNotes] = useState(incident.notes || '');
  const [dispatchOpen, setDispatchOpen] = useState(false);
  const [proposedStation, setProposedStation] = useState(null);
  useEffect(() => { setStatus(incident.status || 'New'); setNotes(incident.notes || ''); }, [incident]);
  const liveDispatches = dispatches.filter((item) => item.event_id === incident.event_id);
  return (
    <Modal title={incident.event_id} description={`${labelTrigger(incident.trigger_type)} · ${formatTime(incident.reported_at)}`} onClose={onClose} wide>
      <div className="detail-grid">
        <div className="detail-main-col">
          <div className="detail-status-row">
            <TonePill value={incident.severity} />
            <TonePill value={incident.status} />
            <TonePill value={incident.isDemo ? 'Synthetic data' : 'Phone event'} demo={incident.isDemo} />
          </div>
          {incident.isDemo && (
            <div className="warning-banner">
              <AlertTriangle size={17} />
              <span><b>Synthetic training event.</b> This marker is not a real emergency.</span>
            </div>
          )}
          {incident.trigger_type === 'DEVICE_OFFLINE' && (
            <div className="warning-banner">
              <AlertTriangle size={17} />
              <span><b>Last-known location only.</b> The phone stopped checking in. It may be powered off, out of coverage, or the app/service may have stopped. Do not treat these coordinates as live or assume an emergency without verification.</span>
            </div>
          )}
          <div className="detail-facts">
            <div><small>Trigger</small><b>{labelTrigger(incident.trigger_type)}</b></div>
            <div><small>Signal received</small><b>{formatTime(incident.reported_at)}</b></div>
            <div><small>Location source</small><b>{incident.trigger_type === 'DEVICE_OFFLINE' ? 'Last known · not live' : incident.latitude != null && incident.longitude != null ? 'Coordinates available' : 'Unavailable'}</b></div>
            {incident.last_location_at != null && <div><small>Location updated</small><b>{formatTime(incident.last_location_at)}</b></div>}
            <div><small>Accuracy</small><b>{incident.accuracy_meters != null ? `${Number(incident.accuracy_meters).toFixed(1)} m` : '—'}</b></div>
            <div className="detail-fact-wide">
              <small>Location description</small>
              <b>{incident.location_description || 'No location description supplied'}</b>
              {incident.latitude != null && incident.longitude != null && (
                <a target="_blank" rel="noreferrer" href={`https://www.openstreetmap.org/?mlat=${incident.latitude}&mlon=${incident.longitude}#map=17/${incident.latitude}/${incident.longitude}`}>
                  Open coordinate in OpenStreetMap <ExternalLink size={12} />
                </a>
              )}
            </div>
            <div><small>Station assignment</small><b>{incident.assignment_station || 'Not assigned'}</b></div>
            <div><small>Officer assignment</small><b>{incident.assigned_officer || 'Not assigned'}</b></div>
          </div>

          {/* Nearby Police Stations Spatial Ranking */}
          <NearbyPoliceStationsSection
            token={token}
            incident={incident}
            onProposeDispatch={(st) => {
              setProposedStation(st);
              setDispatchOpen(true);
            }}
          />

          <Field label="Response notes">
            <textarea rows={3} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Record only verified response details…" />
          </Field>
          <div className="status-edit-row">
            <Field label="Update case status">
              <select value={status} onChange={(event) => setStatus(event.target.value)}>
                {statusOptions.map((option) => <option key={option}>{option}</option>)}
              </select>
            </Field>
            <button className="primary-button" disabled={busy || (status === incident.status && notes === (incident.notes || ''))} onClick={() => onStatus(incident, status, notes)}>
              Save status <Check size={15} />
            </button>
          </div>
        </div>
        <aside className="detail-side-col">
          <div className="side-panel">
            <div className="eyebrow">CONTROL ROOM ACTIONS</div>
            <h3>Response coordination</h3>
            <p className="muted">These actions update Raksha360’s internal response log only. No external dispatch is configured.</p>
            <button
              className="primary-button full-width"
              disabled={busy || incident.status === 'Resolved' || incident.status === 'False Alarm'}
              onClick={() => onStatus(incident, incident.status === 'New' ? 'Acknowledged' : incident.status === 'Acknowledged' ? 'Responding' : 'Resolved', notes)}
            >
              <CheckCircle2 size={16} />
              {incident.status === 'New' ? 'Acknowledge alert' : incident.status === 'Acknowledged' ? 'Mark responding' : 'Resolve case'}
            </button>
            <button
              className="gold-button full-width"
              disabled={busy || incident.status === 'Resolved' || incident.status === 'False Alarm'}
              onClick={() => { setProposedStation(null); setDispatchOpen(true); }}
            >
              <RadioTower size={16} />Create internal dispatch task
            </button>
            <a className="outline-button full-width anchor-button" href="tel:112">
              <Navigation size={15} />Open dialer for 112
            </a>
            <p className="micro-disclaimer">Use the phone dialer for direct emergency contact. The app does not place an automatic call.</p>
          </div>
          <div className="side-panel">
            <div className="side-panel-heading">
              <h3>Internal dispatch tasks</h3>
              <span>{liveDispatches.length}</span>
            </div>
            {liveDispatches.length ? liveDispatches.map((item) => (
              <div key={item.dispatch_id} className="dispatch-mini">
                <b>{item.station}</b>
                <small>{item.assigned_officer || 'Officer unassigned'}</small>
                <TonePill value={item.status} />
              </div>
            )) : <p className="empty-mini">No dispatch task has been created for this event.</p>}
          </div>
          <div className="side-panel audit-note">
            <ShieldCheck size={17} />
            <div>
              <b>Audit trail enabled</b>
              <small>Status changes and dispatch task actions are timestamped in the local database.</small>
            </div>
          </div>
        </aside>
      </div>
      {dispatchOpen && (
        <DispatchModal
          incident={incident}
          stations={stations}
          publicStations={publicStations}
          proposedStation={proposedStation}
          onClose={() => setDispatchOpen(false)}
          onCreate={(payload) => { setDispatchOpen(false); onDispatch(incident, payload); }}
          busy={busy}
        />
      )}
    </Modal>
  );
}

function DispatchModal({ incident, stations = [], publicStations = [], proposedStation = null, onClose, onCreate, busy }) {
  const initialStation = proposedStation?.name || (stations[0]?.name) || (publicStations[0]?.name) || 'Control Room · Unassigned station';
  const [station, setStation] = useState(initialStation);
  const [assignedOfficer, setAssignedOfficer] = useState('Unassigned');
  const [unitLabel, setUnitLabel] = useState(proposedStation ? `Reference: ${proposedStation.name}` : 'Control-room unit pending confirmation');
  const [channel, setChannel] = useState('INTERNAL_CONTROL_ROOM');
  const [notes, setNotes] = useState(proposedStation ? `Selected nearby public station reference: ${proposedStation.name} (${Number(proposedStation.distanceKm).toFixed(1)} km). Note: Not transmitted to station.` : '');

  // Combine public stations and response units into selectable options
  const allStationOptions = useMemo(() => {
    const list = [];
    if (proposedStation && !stations.some((s) => s.name === proposedStation.name)) {
      list.push({ id: `prop-${proposedStation.stationId}`, name: proposedStation.name, label: `${proposedStation.name} (Proposed Nearest Public Station · ${Number(proposedStation.distanceKm).toFixed(1)} km)` });
    }
    stations.forEach((s) => {
      list.push({ id: s.station_id, name: s.name, label: `${s.name} · ${s.zone || 'Internal Unit'}` });
    });
    publicStations.forEach((s) => {
      if (!list.some((item) => item.name === s.name)) {
        list.push({ id: s.station_id, name: s.name, label: `${s.name} (Nagpur Public Directory)` });
      }
    });
    return list;
  }, [stations, publicStations, proposedStation]);

  return (
    <Modal title="Create internal dispatch task" description={`Incident ${incident.event_id} · External dispatch is not configured`} onClose={onClose}>
      <div className="form-stack">
        <div className="warning-banner" style={{ borderLeft: '4px solid #f59e0b', background: 'rgba(245, 158, 11, 0.12)' }}>
          <AlertTriangle size={18} color="#d97706" />
          <div>
            <b style={{ color: '#b45309', display: 'block', fontSize: 13, letterSpacing: '0.04em' }}>INTERNAL CONTROL ROOM TASK — NOT SENT TO POLICE</b>
            <span style={{ fontSize: 12 }}>This creates an internal task for control-room operators. It does NOT notify, dispatch, or contact official police, 112, or the selected station.</span>
          </div>
        </div>
        <Field label="Station / proposed reference area">
          <select value={station} onChange={(event) => setStation(event.target.value)}>
            {allStationOptions.length ? (
              allStationOptions.map((item) => <option key={item.id} value={item.name}>{item.label}</option>)
            ) : (
              <option>Control Room · Unassigned station</option>
            )}
          </select>
        </Field>
        <Field label="Assigned officer / contact reference">
          <input value={assignedOfficer} onChange={(event) => setAssignedOfficer(event.target.value)} placeholder="Officer identifier or Unassigned" />
        </Field>
        <Field label="Response unit label">
          <input value={unitLabel} onChange={(event) => setUnitLabel(event.target.value)} placeholder="Unit or vehicle identifier (verified only)" />
        </Field>
        <Field label="Action path">
          <select value={channel} onChange={(event) => setChannel(event.target.value)}>
            <option value="INTERNAL_CONTROL_ROOM">Internal control-room task</option>
            <option value="PHONE_112_MANUAL">Manual 112 phone follow-up (operator action)</option>
            <option value="AUTHORIZED_EXTERNAL_GATEWAY">Authorized gateway (not configured)</option>
          </select>
        </Field>
        <Field label="Dispatch notes">
          <textarea rows={3} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Use only verified details. No speculative assignments." />
        </Field>
        <div className="modal-actions">
          <button className="subtle-button" onClick={onClose}>Cancel</button>
          <button className="primary-button" disabled={busy || !station.trim()} onClick={() => onCreate({ station, assignedOfficer: assignedOfficer || 'Unassigned', unitLabel: unitLabel || 'Pending confirmation', channel, notes })}>
            {busy ? 'Saving…' : 'Create internal task'} <ArrowRight size={15} />
          </button>
        </div>
      </div>
    </Modal>
  );
}

function ReportModal({ onClose, onCreate, busy, incident = null }) {
  const [title, setTitle] = useState(incident ? `${labelTrigger(incident.trigger_type)} follow-up` : '');
  const [category, setCategory] = useState(incident?.trigger_type === 'CHILD_SOS' ? 'Child safety' : 'Safety concern');
  const [description, setDescription] = useState(incident ? `Follow-up for ${incident.event_id}. Verify details with the reporting person.` : '');
  const [locationDescription, setLocationDescription] = useState(incident?.location_description || 'Location unavailable');
  const [latitude, setLatitude] = useState(incident?.latitude ?? '');
  const [longitude, setLongitude] = useState(incident?.longitude ?? '');
  const [isDemoMode, setIsDemoMode] = useState(Boolean(incident?.isDemo));
  return (
    <Modal title="Create safety report" description="Records a case in the Raksha360 queue, not an official police filing." onClose={onClose}>
      <form className="form-stack" onSubmit={(event) => { event.preventDefault(); onCreate({ title, category, description, locationDescription, latitude: latitude === '' ? null : Number(latitude), longitude: longitude === '' ? null : Number(longitude), reportedAt: Date.now(), sourceEventId: incident?.event_id || null, isDemoMode }); }}>
        <Field label="Report title">
          <input required minLength={3} maxLength={160} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Short factual title" />
        </Field>
        <Field label="Category">
          <select value={category} onChange={(event) => setCategory(event.target.value)}>
            {['Harassment', 'Unsafe route', 'Child safety', 'Missing child', 'School handover', 'Threat / intimidation', 'Other'].map((item) => <option key={item}>{item}</option>)}
          </select>
        </Field>
        <Field label="Description">
          <textarea required minLength={5} maxLength={5000} rows={4} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Separate observed facts from assumptions." />
        </Field>
        <Field label="Location description">
          <input value={locationDescription} onChange={(event) => setLocationDescription(event.target.value)} placeholder="Address, landmark or unavailable" />
        </Field>
        <div className="form-two">
          <Field label="Latitude"><input type="number" step="any" min={-90} max={90} value={latitude} onChange={(event) => setLatitude(event.target.value)} placeholder="Optional" /></Field>
          <Field label="Longitude"><input type="number" step="any" min={-180} max={180} value={longitude} onChange={(event) => setLongitude(event.target.value)} placeholder="Optional" /></Field>
        </div>
        <label className="checkbox-field">
          <input type="checkbox" checked={isDemoMode} onChange={(event) => setIsDemoMode(event.target.checked)} />
          <span>Mark as synthetic demo data</span>
        </label>
        <div className="modal-actions">
          <button type="button" className="subtle-button" onClick={onClose}>Cancel</button>
          <button type="submit" className="primary-button" disabled={busy}>{busy ? 'Saving…' : 'Save report'} <Send size={15} /></button>
        </div>
      </form>
    </Modal>
  );
}

function ReportDetail({ report, onClose, onUpdate, busy }) {
  const [status, setStatus] = useState(report.status || 'New');
  const [priority, setPriority] = useState(report.priority || 'Medium');
  useEffect(() => { setStatus(report.status || 'New'); setPriority(report.priority || 'Medium'); }, [report]);
  return (
    <Modal title={report.title || report.report_id} description={`${report.category} · ${formatTime(report.reported_at)}`} onClose={onClose}>
      <div className="form-stack">
        <div className="detail-status-row">
          <TonePill value={report.priority} />
          <TonePill value={report.status} />
          {report.isDemo && <span className="demo-tag">DEMO</span>}
        </div>
        <div className="report-description">{report.description}</div>
        <div className="detail-facts">
          <div><small>Report reference</small><b>{report.report_id}</b></div>
          <div><small>Location</small><b>{report.location_description || 'Unavailable'}</b></div>
          {report.source_event_id && <div className="detail-fact-wide"><small>Related incident</small><b>{report.source_event_id}</b></div>}
        </div>
        <Field label="Case status">
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            {reportStatusOptions.map((item) => <option key={item}>{item}</option>)}
          </select>
        </Field>
        <Field label="Priority">
          <select value={priority} onChange={(event) => setPriority(event.target.value)}>
            {['Critical', 'High', 'Medium', 'Low'].map((item) => <option key={item}>{item}</option>)}
          </select>
        </Field>
        <div className="warning-banner">
          <ShieldCheck size={16} />
          <span>Store only necessary case information. This app does not submit this report into an official police or cybercrime portal.</span>
        </div>
        <div className="modal-actions">
          <button className="subtle-button" onClick={onClose}>Close</button>
          <button className="primary-button" disabled={busy || (status === report.status && priority === report.priority)} onClick={() => onUpdate(report, status, priority)}>
            Save updates <Check size={15} />
          </button>
        </div>
      </div>
    </Modal>
  );
}

function TopBar({ connected, lastUpdate, onRefresh, refreshing, page, onNewReport, onLogout, userMenuOpen, setUserMenuOpen, showDemoMode, onToggleDemoMode }) {
  const activeLabel = NAV.find((item) => item.id === page)?.label || 'Command center';
  const [clock, setClock] = useState(() => new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));

  useEffect(() => {
    const timer = setInterval(() => {
      setClock(new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <header className="topbar">
      <div className="topbar-left">
        <div className="mobile-brand">
          <div className="brand-mark small"><ShieldCheck size={18} /></div>
          <b>RAKSHA<span>360</span></b>
        </div>
        <div className="breadcrumb">
          <span>Control Room</span><i>/</i><b>{activeLabel}</b>
        </div>
      </div>
      <div className="topbar-right">
        {onToggleDemoMode && (
          <button
            type="button"
            className={cx('top-qa-toggle', showDemoMode && 'qa-active')}
            onClick={onToggleDemoMode}
            title={showDemoMode ? "Currently showing synthetic QA fixtures. Click to return to genuine operational stream." : "Currently showing genuine operational data only. Click to view demo fixtures for testing."}
          >
            {showDemoMode ? 'QA Mode: DEMO DATA' : 'Operational mode'}
          </button>
        )}
        <span className={cx('top-live-status', !connected && 'status-disconnected')}>
          <i className={cx('signal-dot', connected ? 'signal-green signal-pulse' : 'signal-red')} />
          {connected ? 'LIVE SYNC' : 'RECONNECTING'}
        </span>
        <span className="top-updated">
          <Clock3 size={13} />
          <span>{clock} IST</span>
          <i>•</i>
          <span>{lastUpdate ? `Synced ${timeAgo(lastUpdate)}` : 'Not synced yet'}</span>
        </span>
        <button className="top-icon-button" title="Refresh data" onClick={onRefresh} disabled={refreshing}>
          <RefreshCw className={refreshing ? 'spin' : ''} size={15} />
        </button>
        <button className="top-new-report" onClick={onNewReport}>
          <MessageSquareText size={15} />New report
        </button>
        <div className="operator-wrap">
          <button className="operator-avatar" onClick={() => setUserMenuOpen((value) => !value)} title="Operator menu">
            <UserRound size={17} />
          </button>
          {userMenuOpen && (
            <div className="operator-menu">
              <b>Control room operator</b>
              <small>Local dashboard session</small>
              <button onClick={onLogout}><LogOut size={14} />Sign out</button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

function CommandCenter({
  metrics,
  incidents,
  reports,
  dispatches,
  stations = [],
  publicStations = [],
  devices = [],
  onSelectIncident,
  selectedIncident,
  onOpenFullDetail,
  onStatus,
  onSelectReport,
  onMap,
  loading,
  busy,
  token,
  onOpenDispatch,
}) {
  const [inboxFilter, setInboxFilter] = useState('');
  const open = incidents.filter((item) => isOpenStatus(item.status));
  const urgent = open.filter((item) => ['Critical', 'High'].includes(item.severity));
  const attention = open.filter((item) => ['CHILD_SOS', 'LOST_CHILD', 'JOURNEY_TIMEOUT', 'GEOFENCE_EXIT', 'SCHOOL_HANDOVER_ESCALATION', 'MISSED_CHECK_IN'].includes(item.trigger_type));

  const filteredInbox = incidents.filter((item) => {
    if (!inboxFilter.trim()) return true;
    const term = inboxFilter.toLowerCase();
    return [item.event_id, item.trigger_type, item.location_description, item.severity, item.status].some((f) => String(f || '').toLowerCase().includes(term));
  });

  const mapStations = useMemo(() => [
    ...stations,
    ...publicStations.map((s) => ({
      ...s,
      zone: s.jurisdiction_label,
      isDemo: false,
      isOfficialPublicDirectory: true,
    })),
  ], [stations, publicStations]);

  return (
    <div className="page-content">
      <section className="welcome-strip glass-panel">
        <div className="welcome-copy">
          <div className="eyebrow gold"><span className="signal-dot signal-amber" /> RESPONSE OPERATIONS · NAGPUR TEST AREA</div>
          <h1>Command <em>center</em></h1>
          <p>Real-time emergency monitoring, spatial coordination, and auditable response workflows across Nagpur.</p>
        </div>
        <div className="welcome-side">
          <div className="secure-chip"><ShieldCheck size={14} /> Protected session</div>
          <button className="primary-button" onClick={onMap}><MapPinned size={15} />Open full map <ArrowUpRight size={15} /></button>
        </div>
        <div className="welcome-watermark"><ShieldCheck size={155} /></div>
      </section>

      {/* Operational Summary KPIs */}
      <div className="metrics-grid">
        <MetricCard
          icon={Siren}
          label="Open incident queue"
          value={loading ? '—' : (metrics?.openIncidents ?? open.filter((item) => !item.isDemo).length)}
          note="Unresolved emergency events"
          tone="red"
          trend={urgent.length ? `${urgent.length} urgent` : 'Monitored'}
        />
        <MetricCard
          icon={Flame}
          label="Critical live alerts"
          value={loading ? '—' : (metrics?.criticalIncidents ?? urgent.filter((item) => !item.isDemo).length)}
          note="Requires immediate review"
          tone="amber"
        />
        <MetricCard
          icon={ClipboardList}
          label="Casework reports"
          value={loading ? '—' : (metrics?.openReports ?? reports.filter((item) => isOpenStatus(item.status) && !item.isDemo).length)}
          note="Reports awaiting triage"
          tone="blue"
        />
        <MetricCard
          icon={Smartphone}
          label="Monitored devices"
          value={loading ? '—' : devices.filter((d) => d.monitoringEnabled && d.status !== 'OFFLINE').length}
          note="Active guard mode / journey fixes"
          tone="green"
        />
      </div>

      {/* Central Spatial Workspace (Map + Priority Inbox) */}
      <div className="command-grid">
        {/* Left: Operations Map */}
        <section className="glass-panel map-panel" style={{ position: 'relative' }}>
          <div className="panel-heading">
            <div>
              <div className="eyebrow">SITUATIONAL AWARENESS</div>
              <h2>Live operations map</h2>
              <p>Geospatial telemetry for incoming incidents and stations in Nagpur.</p>
            </div>
            <button className="small-outline-button" onClick={onMap}>
              Full map <ArrowUpRight size={13} />
            </button>
          </div>
          <div className="map-mini">
            <MapLibreMap
              incidents={incidents.slice(0, 50)}
              stations={mapStations}
              devices={devices}
              onSelect={onSelectIncident}
              selectedId={selectedIncident?.event_id}
            />
            {selectedIncident && (
              <SpatialInspector
                incident={selectedIncident}
                token={token}
                onClose={() => onSelectIncident(null)}
                onOpenFull={() => onOpenFullDetail(selectedIncident)}
                onStatus={onStatus}
                onOpenDispatch={onOpenDispatch}
                busy={busy}
              />
            )}
          </div>
          <div className="map-panel-footer">
            <Signal tone="green" label="OpenFreeMap vector tiles online" />
            <span>{incidents.filter((item) => Number.isFinite(Number(item.latitude)) && Number.isFinite(Number(item.longitude))).length} mapped event(s)</span>
          </div>
        </section>

        {/* Right: Priority Incident Inbox */}
        <section className="glass-panel queue-panel">
          <div className="panel-heading">
            <div>
              <div className="eyebrow">PRIORITY INBOX</div>
              <h2>Incoming signals</h2>
              <p>Newest emergency events from the API stream.</p>
            </div>
            <span className="queue-count">{incidents.length}</span>
          </div>
          <div style={{ marginBottom: 10 }}>
            <div className="search-field" style={{ height: 34 }}>
              <Search size={14} />
              <input
                placeholder="Filter inbox by ID, trigger, or location…"
                value={inboxFilter}
                onChange={(e) => setInboxFilter(e.target.value)}
              />
              {inboxFilter && (
                <button className="icon-button" style={{ border: 0, width: 22, height: 22 }} onClick={() => setInboxFilter('')}>
                  <X size={12} />
                </button>
              )}
            </div>
          </div>
          {loading ? (
            <LoadingRows rows={6} />
          ) : filteredInbox.length ? (
            <div className="incident-list">
              {filteredInbox.slice(0, 7).map((item) => (
                <IncidentRow
                  key={item.event_id}
                  incident={item}
                  active={selectedIncident?.event_id === item.event_id}
                  onClick={() => onSelectIncident(item)}
                />
              ))}
            </div>
          ) : (
            <EmptyState
              icon={Radio}
              title={incidents.length === 0 ? "No live incidents received yet." : "No matching incidents"}
              detail={incidents.length === 0 ? "Awaiting genuine emergency triggers from registered Raksha360 devices." : "Trigger SOS from the connected phone or change filter."}
            />
          )}
          <div className="queue-footer">
            <span><span className="signal-dot signal-green" /> Realtime API stream</span>
            <button className="text-button" onClick={() => window.dispatchEvent(new CustomEvent('raksha:goto', { detail: 'incidents' }))}>
              View all incidents <ArrowRight size={13} />
            </button>
          </div>
        </section>
      </div>

      {/* Secondary Operational Views (Casework & Dispatch Log) */}
      <div className="lower-grid">
        <section className="glass-panel lower-panel">
          <div className="panel-heading compact">
            <div>
              <div className="eyebrow">CASEWORK</div>
              <h2>Reports awaiting review</h2>
            </div>
            <button className="text-button" onClick={() => window.dispatchEvent(new CustomEvent('raksha:goto', { detail: 'reports' }))}>
              All reports <ArrowRight size={13} />
            </button>
          </div>
          {reports.filter((item) => isOpenStatus(item.status)).slice(0, 4).map((item) => (
            <button key={item.report_id} className="report-line" onClick={() => onSelectReport(item)}>
              <span className="report-line-icon"><FileText size={16} /></span>
              <span className="report-line-main">
                <b>{item.title}</b>
                <small>{item.category} · {timeAgo(item.reported_at)}</small>
              </span>
              <TonePill value={item.priority} />
            </button>
          ))}
          {!reports.filter((item) => isOpenStatus(item.status)).length && <p className="panel-empty">No open reports currently.</p>}
        </section>

        <section className="glass-panel lower-panel">
          <div className="panel-heading compact">
            <div>
              <div className="eyebrow">INTERNAL COORDINATION</div>
              <h2>Dispatch task activity</h2>
            </div>
            <button className="text-button" onClick={() => window.dispatchEvent(new CustomEvent('raksha:goto', { detail: 'dispatch' }))}>
              Dispatch log <ArrowRight size={13} />
            </button>
          </div>
          {dispatches.slice(0, 4).map((item) => (
            <div className="dispatch-line" key={item.dispatch_id}>
              <span className="dispatch-status-icon"><RadioTower size={15} /></span>
              <span className="dispatch-line-main">
                <b>{item.event_id}</b>
                <small>{item.station} · {timeAgo(item.requested_at)}</small>
              </span>
              <TonePill value={item.status} />
            </div>
          ))}
          {!dispatches.length && <p className="panel-empty">No internal dispatch tasks yet. Select an incident to create one.</p>}
          <div className="inline-warning"><AlertTriangle size={14} />External police dispatch gateway is not configured. Tasks remain internal.</div>
        </section>
      </div>
    </div>
  );
}

function IncidentsPage({ incidents, onSelectIncident, loading, token, refresh, notify }) {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('All');
  const [severity, setSeverity] = useState('All');
  const filtered = incidents.filter((item) => {
    const term = search.toLowerCase();
    return (!term || [item.event_id, item.trigger_type, item.location_description, item.status].some((part) => String(part || '').toLowerCase().includes(term))) && (status === 'All' || item.status === status) && (severity === 'All' || item.severity === severity);
  });
  const exportCsv = () => {
    const rows = [['event_id','trigger_type','severity','status','reported_at','latitude','longitude','location_description','is_demo'], ...filtered.map((item) => [item.event_id,item.trigger_type,item.severity,item.status,item.reported_at,item.latitude,item.longitude,item.location_description,item.isDemo])];
    const csv = rows.map((row) => row.map((cell) => `"${String(cell ?? '').replaceAll('"', '""')}"`).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a'); a.href = url; a.download = 'raksha360-incidents.csv'; a.click(); URL.revokeObjectURL(url); notify('Incident CSV exported.');
  };
  return (
    <div className="page-content">
      <SectionHeading
        eyebrow="INCIDENT MANAGEMENT"
        title="SOS & incident queue"
        description="Review alerts from connected devices, manage operational case status, and track coordinate accuracy."
        actions={
          <>
            <button className="outline-button" onClick={exportCsv}><Download size={15} />Export CSV</button>
            <button className="primary-button" onClick={refresh}><RefreshCw size={15} />Refresh</button>
          </>
        }
      />
      <div className="filters-bar">
        <div className="search-field">
          <Search size={15} />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search event ID, type, or location…" />
        </div>
        <div className="filter-field">
          <Filter size={14} />
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option>All</option>
            {statusOptions.map((item) => <option key={item}>{item}</option>)}
          </select>
        </div>
        <div className="filter-field">
          <SlidersHorizontal size={14} />
          <select value={severity} onChange={(event) => setSeverity(event.target.value)}>
            <option>All</option>
            {['Critical','High','Medium','Low'].map((item) => <option key={item}>{item}</option>)}
          </select>
        </div>
        <span className="filter-total">{filtered.length} results</span>
      </div>
      <div className="data-panel">
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Incident / Trigger</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Location</th>
                <th>Received</th>
                <th>Signal</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan="7"><LoadingRows rows={6} /></td></tr>
              ) : filtered.map((item) => (
                <tr key={item.event_id} onClick={() => onSelectIncident(item)} className="table-row-click">
                  <td>
                    <div className="table-primary">
                      <b>{item.event_id}</b>
                      {item.isDemo && <span className="demo-tag">DEMO</span>}
                    </div>
                    <small>{labelTrigger(item.trigger_type)}</small>
                  </td>
                  <td><TonePill value={item.severity} /></td>
                  <td><TonePill value={item.status} /></td>
                  <td><span className="table-location">{item.location_description || 'Unavailable'}</span></td>
                  <td><span>{formatTime(item.reported_at)}</span><small>{timeAgo(item.reported_at)}</small></td>
                  <td>
                    <Signal
                      tone={item.isDemo ? 'amber' : (Number.isFinite(Number(item.latitude)) && Number.isFinite(Number(item.longitude)) ? 'green' : 'amber')}
                      label={item.isDemo ? 'Synthetic' : (item.latitude != null && item.longitude != null ? 'Coordinates' : 'No GPS')}
                    />
                  </td>
                  <td>
                    <button className="icon-button table-action" title="Open incident" onClick={(event) => { event.stopPropagation(); onSelectIncident(item); }}>
                      <ArrowUpRight size={15} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!loading && filtered.length === 0 && (
          <EmptyState
            icon={Siren}
            title={incidents.length === 0 ? "No live incidents received yet." : "No matching incidents"}
            detail={incidents.length === 0 ? "Trigger SOS from the connected Android app to receive live events here." : "Try a different filter or wait for a device event to arrive."}
          />
        )}
      </div>
      <p className="footnote"><LockKeyhole size={13} />Live and synthetic events are explicitly distinguished. Location markers only use coordinates verified by the backend.</p>
    </div>
  );
}

function ReportsPage({ reports, onSelectReport, loading, onNewReport, refresh }) {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('All');
  const filtered = reports.filter((item) => (!search || [item.report_id, item.title, item.category, item.description].some((part) => String(part || '').toLowerCase().includes(search.toLowerCase()))) && (status === 'All' || item.status === status));
  return (
    <div className="page-content">
      <SectionHeading
        eyebrow="REPORT INTAKE & CASEWORK"
        title="Reports & complaints"
        description="Triage, review and track safety reports recorded in the local Raksha360 database. Not an official FIR portal."
        actions={
          <>
            <button className="outline-button" onClick={refresh}><RefreshCw size={15} />Refresh</button>
            <button className="primary-button" onClick={onNewReport}><MessageSquareText size={15} />Create report</button>
          </>
        }
      />
      <div className="filters-bar">
        <div className="search-field">
          <Search size={15} />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search report reference, title, or category…" />
        </div>
        <div className="filter-field">
          <Filter size={14} />
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option>All</option>
            {reportStatusOptions.map((item) => <option key={item}>{item}</option>)}
          </select>
        </div>
        <span className="filter-total">{filtered.length} report(s)</span>
      </div>
      <div className="report-card-grid">
        {loading ? (
          <div className="glass-panel" style={{ padding: 16 }}><LoadingRows rows={5} /></div>
        ) : filtered.map((item) => (
          <button className="report-card" key={item.report_id} onClick={() => onSelectReport(item)}>
            <div className="report-card-top">
              <span className="report-line-icon"><FileText size={18} /></span>
              <TonePill value={item.priority} />
              {item.isDemo && <span className="demo-tag">DEMO</span>}
            </div>
            <h3>{item.title}</h3>
            <p>{item.description}</p>
            <div className="report-card-meta">
              <span>{item.category}</span>
              <span>{timeAgo(item.reported_at)}</span>
            </div>
            <div className="report-card-bottom">
              <TonePill value={item.status} />
              <span>Open record <ArrowUpRight size={13} /></span>
            </div>
          </button>
        ))}
        {!loading && filtered.length === 0 && (
          <div className="glass-panel" style={{ gridColumn: '1 / -1' }}>
            <EmptyState
              icon={ClipboardList}
              title={reports.length === 0 ? "No reports submitted by the app yet." : "No matching reports"}
              detail={reports.length === 0 ? "No incident reports have been submitted through Raksha360. Create one manually or file from the app." : "Try adjusting the filter or search query."}
            />
          </div>
        )}
      </div>
      <div className="notice-strip">
        <ShieldAlert size={16} />
        <span><b>Official filing status:</b> no connection to police or cybercrime portal is configured. Reports remain in the local Raksha360 database.</span>
      </div>
    </div>
  );
}

function DispatchPage({ dispatches, onUpdateDispatch, loading, onSelectIncident }) {
  return (
    <div className="page-content">
      <SectionHeading
        eyebrow="RESPONSE COORDINATION"
        title="Dispatch task log"
        description="Internal control-room assignments and operator-reported movement. External dispatch must be confirmed separately."
        actions={<span className="gateway-chip"><i className="signal-dot signal-amber" />EXTERNAL GATEWAY OFFLINE</span>}
      />
      <div className="notice-strip">
        <AlertTriangle size={16} />
        <span>Raksha360 records internal tasks. It cannot confirm police radio traffic, patrol assignment, or a 112 call unless an authorized integration exists.</span>
      </div>
      <div className="dispatch-card-list">
        {loading ? (
          <div className="glass-panel" style={{ padding: 16 }}><LoadingRows rows={5} /></div>
        ) : dispatches.map((item) => (
          <article className="dispatch-card" key={item.dispatch_id}>
            <div className="dispatch-card-mark"><RadioTower size={19} /></div>
            <div className="dispatch-card-body">
              <div className="dispatch-card-title">
                <b>{item.event_id}</b>
                <TonePill value={item.status} />
                {item.isDemo && <span className="demo-tag">DEMO</span>}
              </div>
              <p>{item.station} · {item.assigned_officer || 'Officer unassigned'}</p>
              <div className="dispatch-metadata">
                <span><Clock3 size={13} />Requested {formatTime(item.requested_at)}</span>
                <span><Navigation size={13} />{item.unit_label || 'Unit pending confirmation'}</span>
                <span><Activity size={13} />{item.channel.replaceAll('_', ' ')}</span>
              </div>
              {item.notes && <div className="dispatch-note">{item.notes}</div>}
              <button className="text-button" onClick={() => onSelectIncident({ event_id: item.event_id })}>
                Open related incident <ArrowRight size={13} />
              </button>
            </div>
            <div className="dispatch-card-action">
              <label>
                Update internal status
                <select value={item.status} onChange={(event) => onUpdateDispatch(item, event.target.value)}>
                  {dispatchStatusOptions.map((status) => <option key={status} value={status}>{status.replaceAll('_', ' ')}</option>)}
                </select>
              </label>
              <span className="micro-disclaimer">Not an official police acknowledgment</span>
            </div>
          </article>
        ))}
        {!loading && !dispatches.length && (
          <div className="glass-panel">
            <EmptyState icon={RadioTower} title="No dispatch tasks created" detail="Open an incident and choose ‘Create dispatch task’ to start an internal assignment." />
          </div>
        )}
      </div>
    </div>
  );
}

function StationsPage({ stations, publicStations = [], helplinesData = null, loading, refresh }) {
  const [search, setSearch] = useState('');
  const query = search.trim().toLowerCase();
  const filteredPublic = publicStations.filter((item) => !query || [item.name, item.name_mr, item.phone, item.email, item.address].some((value) => String(value || '').toLowerCase().includes(query)));
  const filteredUnits = stations.filter((item) => !query || [item.name, item.zone, item.status].some((value) => String(value || '').toLowerCase().includes(query)));

  const defaultContacts = [
    { id: 'india-emergency-112', name: 'Emergency Response Support System (ERSS)', category: 'emergency', phone: '112', scope: 'All emergencies India-wide; shown on Nagpur City Police portal' },
    { id: 'nagpur-women-1091', name: 'Women Helpline', category: 'women_safety', phone: '1091', scope: 'Dedicated women emergency assistance; shown on Nagpur City Police portal' },
    { id: 'child-1098', name: 'Child Helpline', category: 'child_safety', phone: '1098', scope: 'Child protection & rescue; shown on Nagpur City Police portal' },
    { id: 'cybercrime-1930', name: 'National Cyber Crime Helpline', category: 'cybercrime', phone: '1930', scope: 'Financial cyber fraud & digital complaints helpline' },
    { id: 'ambulance-108', name: 'Emergency Medical Ambulance', category: 'medical', phone: '108', scope: 'Emergency medical response; shown on official portal' },
    { id: 'nagpur-police-office', name: 'Nagpur City Police Office', category: 'police_office', phone: '0712-2560601', scope: 'Police Bhavan, behind Collector Office, Civil Lines, Nagpur' },
    { id: 'nagpur-control-room', name: 'Nagpur Police Control Room', category: 'control_room', phone: '+91-712-2563232', scope: 'Official Nagpur City Police control-room telephone' },
  ];
  const contacts = helplinesData?.contacts || defaultContacts;
  const officialLinks = helplinesData?.official_links || [
    { name: 'Nagpur City Police station directory', url: 'https://nagpurpolice.gov.in/police-stations' },
    { name: 'Nagpur City Police contact page', url: 'https://nagpurpolice.gov.in/contact' },
    { name: 'National Cyber Crime Reporting Portal', url: 'https://cybercrime.gov.in/' },
    { name: 'Government of India Crime in India 2023 catalog', url: 'https://www.data.gov.in/catalog/crime-india-2023' },
  ];

  return (
    <div className="page-content">
      <SectionHeading
        eyebrow="REFERENCE DIRECTORY"
        title="Stations & public directory"
        description="Official Nagpur public directory reference and emergency helplines, strictly separated from synthetic Raksha360 test units."
        actions={<button className="primary-button" onClick={refresh}><RefreshCw size={15} />Refresh directory</button>}
      />
      <div className="notice-strip">
        <AlertTriangle size={16} />
        <span><b>Operational boundary:</b> This page presents publicly listed station and emergency contact numbers. Raksha360 is not connected to the official police command or 112 gateway. Dispatch tasks remain internal.</span>
      </div>

      {/* Official Helplines */}
      <section className="table-panel helplines-section">
        <div className="panel-heading">
          <div>
            <div className="eyebrow gold">OFFICIAL CITIZEN HELPLINES</div>
            <h2>Emergency & public support contacts</h2>
            <p>From official Nagpur City Police public portals. Confirm numbers directly before operational reliance.</p>
          </div>
          <span className="verified-tag">OFFICIAL PUBLIC CONTACTS</span>
        </div>
        <div className="helplines-grid">
          {contacts.map((c) => (
            <article className="helpline-card" key={c.id || c.phone}>
              <div className="helpline-top">
                <span className="helpline-number">{c.phone}</span>
                <span className="helpline-category">{c.category?.replaceAll('_', ' ')}</span>
              </div>
              <div>
                <h3 className="helpline-name">{c.name}</h3>
                <p className="helpline-scope">{c.scope}</p>
              </div>
              <div className="helpline-actions">
                <a className="helpline-call" href={`tel:${c.phone.replace(/[^0-9+]/g, '')}`} title="Open in phone dialer">
                  <Smartphone size={13} /> Open dialer
                </a>
                <span className="helpline-verify-note">Verify before reliance</span>
              </div>
            </article>
          ))}
        </div>
        <div style={{ marginTop: 14, paddingTop: 10, borderTop: '1px solid var(--border-subtle)' }}>
          <small style={{ color: 'var(--text-secondary)', fontSize: 11 }}>Official public reference portals:</small>
          <div className="official-links-bar">
            {officialLinks.map((l) => (
              <a key={l.url} className="official-link-chip" href={l.url} target="_blank" rel="noreferrer">
                {l.name} <ExternalLink size={11} />
              </a>
            ))}
          </div>
        </div>
      </section>

      {/* Section A: 37 Nagpur Police Stations */}
      <div className="filters-bar">
        <div className="search-field">
          <Search size={15} />
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search station name, Marathi script, phone, email, or address…" />
        </div>
        <span className="filter-total">{filteredPublic.length} public listings · {filteredUnits.length} demo units</span>
      </div>

      <section className="table-panel">
        <div className="panel-heading">
          <div>
            <div className="eyebrow">OFFICIAL PUBLIC SOURCE SNAPSHOT (SECTION A)</div>
            <h2>Nagpur City Police station directory</h2>
            <p>Source: <a className="table-link" href="https://nagpurpolice.gov.in/police-stations" target="_blank" rel="noreferrer">nagpurpolice.gov.in/police-stations <ExternalLink size={11} /></a> · 37 official public directory listings snapshot.</p>
          </div>
          <span className="verified-tag">PUBLIC DIRECTORY · NO DISPATCH CONNECTION</span>
        </div>
        {loading ? (
          <LoadingRows rows={6} />
        ) : filteredPublic.length ? (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Station name</th>
                  <th>Public phone</th>
                  <th>Public email</th>
                  <th>Listed location</th>
                  <th>Map coordinate status</th>
                </tr>
              </thead>
              <tbody>
                {filteredPublic.map((item) => (
                  <tr key={item.station_id}>
                    <td>
                      <div className="table-primary"><b>{item.name}</b></div>
                      {item.name_mr && <small style={{ color: 'var(--text-secondary)' }}>{item.name_mr}</small>}
                      <small style={{ color: 'var(--text-tertiary)' }}>{item.station_id}</small>
                    </td>
                    <td>
                      {item.phone ? <a className="table-link" href={`tel:${item.phone.replace(/[^0-9+]/g, '')}`}>{item.phone}</a> : 'Not listed'}
                    </td>
                    <td>
                      {item.email ? <a className="table-link" href={`mailto:${item.email}`}>{item.email}</a> : 'Not listed'}
                    </td>
                    <td>
                      <span className="table-location">{item.address || 'Nagpur, Maharashtra'}</span>
                      <small className="table-subline">Public listing; verify jurisdiction</small>
                    </td>
                    <td>
                      {item.coordinates_verified && item.latitude != null && item.longitude != null ? (
                        <a className="table-link" target="_blank" rel="noreferrer" href={`https://www.openstreetmap.org/?mlat=${item.latitude}&mlon=${item.longitude}#map=17/${item.latitude}/${item.longitude}`}>
                          Verified: {Number(item.latitude).toFixed(4)}, {Number(item.longitude).toFixed(4)} <ExternalLink size={11} />
                        </a>
                      ) : (
                        <span style={{ color: 'var(--text-tertiary)', fontSize: 11 }}>Not geocoded (intentionally unpinned)</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState icon={Users} title="No matching public station" detail="Try another station name, Marathi name, phone, or email." />
        )}
        <p className="footnote"><LockKeyhole size={13} />Public station contacts are reference records only. Coordinates remain intentionally unpinned until independently verified against open map data.</p>
      </section>

      {/* Section B: Demo Units */}
      <section className="table-panel">
        <div className="panel-heading">
          <div>
            <div className="eyebrow">LOCAL TRAINING DATA (SECTION B)</div>
            <h2>Raksha360 demo response units</h2>
            <p>Synthetic local test records for control room dispatch simulation. Not real police stations or patrol units.</p>
          </div>
          <span className="demo-tag">DEMO ONLY</span>
        </div>
        {loading ? (
          <LoadingRows rows={3} />
        ) : filteredUnits.length ? (
          <div className="station-grid">
            {filteredUnits.map((station) => (
              <article className="station-card" key={station.station_id}>
                <div className="station-icon"><Users size={19} /></div>
                <div className="station-content">
                  <h3>{station.name}</h3>
                  <p>{station.zone}</p>
                  <div className="station-coords">
                    {station.latitude != null && station.longitude != null ? `${Number(station.latitude).toFixed(4)}, ${Number(station.longitude).toFixed(4)}` : 'No coordinates'} · {station.status}
                  </div>
                  <div className="station-status">
                    <TonePill value={station.status} demo={station.isDemo} />
                    <span className="demo-tag">DEMO UNIT</span>
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <EmptyState icon={Users} title="No demo units" detail="Synthetic response units are optional." />
        )}
      </section>
    </div>
  );
}

function NcrbPage({ ncrbData, loading, refresh }) {
  const families = ncrbData?.available_table_families || [
    { table: '1B.1', name: 'IPC Crimes (City-wise) - 2021-2023', expected_granularity: 'metropolitan city by year' },
    { table: '1B.2', name: 'SLL Crimes (City-wise) - 2021-2023', expected_granularity: 'metropolitan city by year' },
    { table: '1B.3', name: 'IPC & SLL Crimes (City-wise) - 2021-2023', expected_granularity: 'metropolitan city by year' },
    { table: '1B.4', name: 'IPC Crimes (Crime Head-wise & City-wise) - 2023', expected_granularity: 'city by crime head for 2023' },
    { table: '1B.5', name: 'SLL Crimes (Crime Head-wise & City-wise) - 2023', expected_granularity: 'city by crime head for 2023' },
    { table: 'Crime Against Women', name: 'Crime Against Women (City-wise) - 2021-2023', expected_granularity: 'city by year' },
  ];

  return (
    <div className="page-content">
      <SectionHeading
        eyebrow="OFFICIAL STATISTICAL CATALOG"
        title="NCRB Crime in India 2023 — Source Catalog"
        description="Official National Crime Records Bureau catalog metadata and annual city aggregate tables. Figures remain unimported until verified row-by-row to uphold strict reporting integrity."
        actions={<button className="primary-button" onClick={refresh}><RefreshCw size={15} />Refresh catalog</button>}
      />
      <div className="notice-strip">
        <ShieldCheck size={16} />
        <span><b>Data boundary:</b> Annual crime statistics are publication-year aggregate figures, not real-time emergency events or geographic points. Raksha360 never fabricates crime numbers or displays annual statistics as live emergency pins.</span>
      </div>
      <div className="table-panel">
        <div className="panel-heading">
          <div>
            <div className="eyebrow gold">SOURCE PROVENANCE & VALIDATION STATUS</div>
            <h2>Official source report & tables</h2>
            <p>Catalog: <a className="table-link" href="https://www.data.gov.in/catalog/crime-india-2023" target="_blank" rel="noreferrer">data.gov.in/catalog/crime-india-2023 <ExternalLink size={11} /></a> · Direct report PDF: <a className="table-link" href="https://www.ncrb.gov.in/uploads/files/1CrimeinIndia2023PartI1.pdf" target="_blank" rel="noreferrer">NCRB Crime in India 2023 Part 1 Report <ExternalLink size={11} /></a></p>
          </div>
          <span className="ncrb-status-pill">VALUES UNIMPORTED · PREVENTS MISINFORMATION</span>
        </div>
        <div className="ncrb-table-grid">
          {families.map((fam) => (
            <div key={fam.table} className="ncrb-table-card">
              <span className="ncrb-table-id">Table {fam.table}</span>
              <h3 className="ncrb-table-name">{fam.name}</h3>
              <span className="ncrb-table-granularity">Scope: {fam.expected_granularity}</span>
              <div style={{ marginTop: 'auto', paddingTop: 8, borderTop: '1px solid var(--border-subtle)' }}>
                <span className="micro-disclaimer">Status: Row transcription pending independent check</span>
              </div>
            </div>
          ))}
        </div>
        <p className="footnote">
          <LockKeyhole size={13} />
          To import verified statistics, locate the exact Nagpur metropolitan city row in the official NCRB PDF table, transcribe the exact figure, and validate against <code>ncrb_city_year_statistics_import_template.csv</code>.
        </p>
      </div>
    </div>
  );
}

function DeviceWatchPage({ devices, loading, refresh, smtpConfigured, offlineThresholdSeconds, onMap }) {
  const [showInactive, setShowInactive] = useState(true);
  const filtered = showInactive ? devices : devices.filter((device) => device.monitoringEnabled || device.status === 'OFFLINE');
  return (
    <div className="page-content">
      <SectionHeading
        eyebrow="DEVICE CHECK-IN & LAST-KNOWN LOCATION"
        title="Device watch"
        description="Monitor explicit Guard Mode / active journey heartbeats. When a device misses its timeout, the server creates an offline incident using only the last location previously received."
        actions={
          <>
            <button className="outline-button" onClick={refresh}><RefreshCw size={15} />Refresh devices</button>
            <button className="primary-button" onClick={onMap}><MapPinned size={15} />Open map</button>
          </>
        }
      />
      <div className="notice-strip">
        <ShieldAlert size={16} />
        <span><b>Important:</b> A powered-off phone cannot transmit new GPS. Any offline alert uses last-known coordinates and timestamp. Email is sent only with user consent and configured laptop SMTP settings.</span>
      </div>
      <div className="metrics-grid">
        <MetricCard icon={Smartphone} label="Registered devices" value={devices.length} note="Seen by this backend" tone="blue" />
        <MetricCard icon={Activity} label="Monitoring active" value={devices.filter((d) => d.monitoringEnabled && d.status !== 'OFFLINE').length} note="Guard mode / journey fixes" tone="green" />
        <MetricCard icon={AlertTriangle} label="Offline alerts" value={devices.filter((d) => d.status === 'OFFLINE').length} note="Missed heartbeat timeout" tone="red" />
        <MetricCard icon={Send} label="Email channel" value={smtpConfigured ? 'Configured' : 'Not configured'} note={smtpConfigured ? 'SMTP transport present' : 'Set in root .env'} tone={smtpConfigured ? 'green' : 'amber'} />
      </div>
      <div className="filters-bar">
        <label className="check-field">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          Include disarmed devices
        </label>
        <span className="filter-total">Timeout: {Math.round((offlineThresholdSeconds || 180) / 60 * 10) / 10} minute(s)</span>
      </div>
      <section className="table-panel">
        <div className="panel-heading">
          <div>
            <div className="eyebrow">DEVICE INVENTORY</div>
            <h2>Latest heartbeat & location</h2>
            <p>Coordinates are only shown when supplied as a non-fallback GPS location.</p>
          </div>
        </div>
        {loading ? (
          <LoadingRows rows={4} />
        ) : filtered.length ? (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Status</th>
                  <th>Device reference</th>
                  <th>Last heartbeat</th>
                  <th>Last known location</th>
                  <th>Email consent / delivery</th>
                  <th>Incident</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((device) => {
                  const hasLocation = device.last_latitude != null && device.last_longitude != null;
                  const isOffline = device.status === 'OFFLINE';
                  return (
                    <tr key={device.device_id}>
                      <td><TonePill value={device.status} /></td>
                      <td>
                        <b>{String(device.device_id).slice(0, 14)}…</b>
                        <small className="table-subline">Battery {device.last_battery_pct ?? '—'}%</small>
                      </td>
                      <td>
                        {formatTime(device.last_seen_at)}
                        <small className="table-subline">{timeAgo(device.last_seen_at)}</small>
                      </td>
                      <td>
                        {hasLocation ? (
                          <>
                            <span>{Number(device.last_latitude).toFixed(5)}, {Number(device.last_longitude).toFixed(5)}</span>
                            {isOffline && <span className="tone-pill tone-red" style={{ marginLeft: 6 }}>LAST KNOWN · NOT LIVE</span>}
                            <small className="table-subline">Fix time: {formatTime(device.last_location_at)}</small>
                            <a className="table-link" target="_blank" rel="noreferrer" href={`https://www.openstreetmap.org/?mlat=${device.last_latitude}&mlon=${device.last_longitude}#map=17/${device.last_latitude}/${device.last_longitude}`}>
                              Open in OpenStreetMap <ExternalLink size={11} />
                            </a>
                          </>
                        ) : 'Location unavailable'}
                      </td>
                      <td>
                        <TonePill value={device.offlineLocationEmailConsent ? 'Consent enabled' : 'No consent'} />
                        <small className="table-subline">{device.offline_email_delivery_status || 'Not attempted'}</small>
                        {device.offline_email_sent_at && <small className="table-subline">Accepted: {formatTime(device.offline_email_sent_at)}</small>}
                      </td>
                      <td>
                        {device.offline_incident_id ? (
                          <button className="text-button" onClick={() => window.dispatchEvent(new CustomEvent('raksha:goto', { detail: 'incidents' }))}>
                            {device.offline_incident_id} <ArrowUpRight size={12} />
                          </button>
                        ) : '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            icon={Smartphone}
            title={devices.length === 0 ? "No devices have checked in yet." : "No matching devices"}
            detail={devices.length === 0 ? "Launch Raksha360 on an Android device to start guard mode and telemetry heartbeats." : "Try adjusting the filter or search query."}
          />
        )}
      </section>
      <p className="footnote"><LockKeyhole size={13} />The dashboard reflects status records from this local backend. It indicates the server stopped receiving heartbeats, not proof a phone was powered off.</p>
    </div>
  );
}

function AuditPage({ audit, loading, refresh }) {
  const iconFor = (action) => action?.includes('SOS') || action?.includes('EMERGENCY') ? Siren : action?.includes('REPORT') ? ClipboardList : action?.includes('DISPATCH') ? RadioTower : ShieldCheck;
  return (
    <div className="page-content">
      <SectionHeading
        eyebrow="ACCOUNTABILITY"
        title="Audit trail"
        description="Chronological log of control-room actions and automated alerts recorded in the database."
        actions={<button className="primary-button" onClick={refresh}><RefreshCw size={15} />Refresh</button>}
      />
      <div className="table-panel audit-list">
        {loading ? (
          <LoadingRows rows={6} />
        ) : audit.map((item) => {
          const Icon = iconFor(item.action);
          return (
            <div className="audit-event" key={item.id}>
              <span className="audit-icon"><Icon size={16} /></span>
              <div className="audit-main">
                <b>{item.action.replaceAll('_', ' ')}</b>
                <p>{item.message}</p>
                <div>
                  <span>{item.actor}</span>
                  <span>·</span>
                  <span>{item.target_id || 'System'}</span>
                  {item.isDemo && <span className="demo-tag">DEMO</span>}
                </div>
              </div>
              <time>{formatTime(item.created_at)}</time>
            </div>
          );
        })}
        {!loading && !audit.length && (
          <EmptyState icon={FileClock} title="No audit events" detail="Backend actions will appear here as they occur." />
        )}
      </div>
    </div>
  );
}

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('raksha360_dashboard_token') || '');
  const [authenticated, setAuthenticated] = useState(false);
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState('');
  const [health, setHealth] = useState(null);
  const [connected, setConnected] = useState(false);
  const [lastUpdate, setLastUpdate] = useState(null);
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(true);
  const getInitialPage = () => {
    if (typeof window === 'undefined') return 'overview';
    const path = window.location.pathname.replace(/^\/+/, '').split('/')[0];
    if (NAV.some((item) => item.id === path)) return path;
    const hash = window.location.hash.replace(/^#\/?/, '').split('/')[0];
    if (NAV.some((item) => item.id === hash)) return hash;
    return 'overview';
  };
  const [page, setPage] = useState(getInitialPage);

  const navigateTo = useCallback((nextPage) => {
    setPage(nextPage);
    setMobileNav(false);
    if (typeof window !== 'undefined') {
      const targetPath = nextPage === 'overview' ? '/' : `/${nextPage}`;
      if (window.location.pathname !== targetPath) {
        window.history.pushState(null, '', targetPath);
      }
    }
  }, []);

  useEffect(() => {
    const onPopState = () => {
      setPage(getInitialPage());
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);
  const [mobileNav, setMobileNav] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [metrics, setMetrics] = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [reports, setReports] = useState([]);
  const [dispatches, setDispatches] = useState([]);
  const [stations, setStations] = useState([]);
  const [publicStations, setPublicStations] = useState([]);
  const [helplines, setHelplines] = useState(null);
  const [ncrbManifest, setNcrbManifest] = useState(null);
  const [devices, setDevices] = useState([]);
  const [deviceWatchMeta, setDeviceWatchMeta] = useState({ smtpConfigured: false, offlineThresholdSeconds: 180 });
  const [audit, setAudit] = useState([]);
  const [selectedIncident, setSelectedIncident] = useState(null);
  const [fullModalIncident, setFullModalIncident] = useState(null);
  const [selectedReport, setSelectedReport] = useState(null);
  const [reportModal, setReportModal] = useState(false);
  const [showDemoMode, setShowDemoMode] = useState(false);
  const [dispatchModalData, setDispatchModalData] = useState(null);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState(null);
  const lastEventRef = useRef('');

  const notify = useCallback((message, tone = 'success') => {
    setToast({ message, tone, id: Date.now() });
  }, []);
  useEffect(() => {
    if (!toast) return undefined;
    const id = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(id);
  }, [toast]);

  const refreshData = useCallback(async (quiet = false, demoOverride = undefined) => {
    if (!token) return;
    if (!quiet) setRefreshing(true);
    const includeDemo = demoOverride !== undefined ? demoOverride : showDemoMode;
    try {
      const [overviewResult, incidentsResult, reportsResult, dispatchResult, stationsResult, publicStationsResult, deviceResult, auditResult, helplinesResult, ncrbResult] = await Promise.all([
        api.overview(token),
        api.incidents(token, { limit: 300, include_demo: includeDemo }),
        api.reports(token, { limit: 300, include_demo: includeDemo }),
        api.dispatches(token, { limit: 300, include_demo: includeDemo }),
        api.stations(token),
        api.policeStationDirectory(token),
        api.devices(token),
        api.audit(token),
        api.helplines(token).catch(() => null),
        api.ncrbManifest(token).catch(() => null),
      ]);
      setMetrics(overviewResult.metrics || {});
      setIncidents(incidentsResult.items || []);
      setReports(reportsResult.items || []);
      setDispatches(dispatchResult.items || []);
      setStations(stationsResult.items || []);
      setPublicStations(publicStationsResult.items || []);
      if (helplinesResult) setHelplines(helplinesResult);
      if (ncrbResult) setNcrbManifest(ncrbResult);
      setDevices(deviceResult.items || []);
      setDeviceWatchMeta({ smtpConfigured: Boolean(deviceResult.smtpConfigured), offlineThresholdSeconds: Number(deviceResult.offlineThresholdSeconds || 180) });
      setAudit(auditResult.items || []);
      setLastUpdate(Date.now());
      setHealth((previous) => ({ ...(previous || {}), status: 'ONLINE', database: 'connected' }));
      setLoading(false);
    } catch (error) {
      if (error.message.includes('401')) {
        setAuthenticated(false);
        localStorage.removeItem('raksha360_dashboard_token');
        setAuthError('Session expired. Enter the dashboard token again.');
      } else {
        notify(`Backend sync failed: ${error.message}`, 'error');
      }
      setHealth((previous) => ({ ...(previous || {}), status: 'ERROR' }));
      setLoading(false);
    } finally {
      if (!quiet) setRefreshing(false);
    }
  }, [token, showDemoMode, notify]);

  const handleToggleDemoMode = () => {
    const next = !showDemoMode;
    setShowDemoMode(next);
    refreshData(false, next);
    notify(next ? 'QA Mode enabled: displaying synthetic test fixtures.' : 'Operational mode: genuine live data only.');
  };

  const checkHealth = useCallback(async () => {
    if (!isApiConfigured()) {
      const unconfiguredState = {
        status: 'UNCONFIGURED',
        database: 'no remote endpoint',
        message: 'No public HTTPS backend URL configured. Set VITE_API_BASE_URL.',
      };
      setHealth(unconfiguredState);
      return unconfiguredState;
    }
    try {
      const base = getApiBase().replace(/\/api\/v1\/?$/, '');
      const response = await fetch(`${base}/api/v1/health`);
      const result = await response.json();
      setHealth(result);
      return result;
    } catch (error) {
      setHealth({ status: 'UNREACHABLE', database: 'disconnected', message: error.message });
      return null;
    }
  }, []);

  const login = async (candidate) => {
    setAuthBusy(true); setAuthError('');
    try {
      await verifyDashboardToken(candidate.trim());
      localStorage.setItem('raksha360_dashboard_token', candidate.trim());
      setToken(candidate.trim()); setAuthenticated(true); setLoading(true);
      notify('Control room session established.');
    } catch (error) {
      setAuthError(error.message || 'Could not verify token.');
    } finally { setAuthBusy(false); }
  };
  const logout = () => {
    setAuthenticated(false); setToken(''); localStorage.removeItem('raksha360_dashboard_token'); setConnected(false); setUserMenuOpen(false);
  };

  useEffect(() => {
    if (!token) return undefined;
    let mounted = true;
    verifyDashboardToken(token).then(() => { if (mounted) setAuthenticated(true); }).catch(() => {
      localStorage.removeItem('raksha360_dashboard_token');
      if (mounted) { setAuthenticated(false); setToken(''); }
    });
    return () => { mounted = false; };
  }, []); // Validate saved token on startup once.

  useEffect(() => {
    if (!authenticated || !token) return undefined;
    refreshData();
    const interval = window.setInterval(() => refreshData(true), 15000);
    return () => window.clearInterval(interval);
  }, [authenticated, token, refreshData]);

  useEffect(() => {
    if (!authenticated || !token) return undefined;
    let socket; let reconnectTimer; let stopped = false;
    const connect = () => {
      if (stopped) return;
      try { socket = createLiveSocket(); } catch { setConnected(false); return; }
      socket.onopen = () => {
        // Authenticate immediately via initial JSON frame to protect token from access logs
        socket.send(JSON.stringify({ type: 'AUTH', token }));
      };
      socket.onmessage = (event) => {
        try {
          const packet = JSON.parse(event.data);
          if (packet.type === 'CONNECTED' || packet.type === 'AUTHENTICATED') {
            setConnected(true);
            setLastUpdate(Date.now());
            return;
          }
          if (packet.type === 'HEARTBEAT' || packet.type === 'PONG') {
            setLastUpdate(Date.now());
            return;
          }
          lastEventRef.current = packet.type;
          if (['INCIDENT_CREATED','INCIDENT_UPDATED','EMERGENCY_LOCATION_UPDATED','CONTROL_ROOM_ALERT_QUEUED','REPORT_SUBMITTED','REPORT_UPDATED','DISPATCH_TASK_CREATED','DISPATCH_TASK_UPDATED','CONTACT_ALERT_QUEUED','DEVICE_HEARTBEAT','DEVICE_OFFLINE_DETECTED','DEVICE_ONLINE_AGAIN','DEVICE_OFFLINE_EMAIL_STATUS'].includes(packet.type)) {
            refreshData(true);
            if (packet.type === 'INCIDENT_CREATED') notify(`Incoming safety alert: ${packet.data?.event_id || packet.data?.incident?.event_id || 'New event'}`, 'alert');
            else if (packet.type === 'DEVICE_OFFLINE_DETECTED') notify(`Device missed heartbeat: ${packet.data?.incident?.event_id || 'offline event'} · last-known location only`, 'alert');
            else if (packet.type === 'DEVICE_OFFLINE_EMAIL_STATUS') notify(`Offline location email: ${packet.data?.offline_email_delivery_status || 'updated'}`, 'warning');
            else if (packet.type === 'REPORT_SUBMITTED') notify('A new report reached the Control Room.');
            else if (packet.type === 'DISPATCH_TASK_CREATED') notify('Internal dispatch task created.', 'warning');
          }
        } catch { /* Ignore non-JSON keepalive payloads. */ }
      };
      socket.onclose = () => { setConnected(false); if (!stopped) reconnectTimer = window.setTimeout(connect, 2500); };
      socket.onerror = () => { setConnected(false); };
    };
    connect();
    return () => { stopped = true; window.clearTimeout(reconnectTimer); socket?.close(); setConnected(false); };
  }, [authenticated, token, refreshData, notify]);

  useEffect(() => {
    const handler = (event) => { if (NAV.some((item) => item.id === event.detail)) navigateTo(event.detail); };
    window.addEventListener('raksha:goto', handler);
    return () => window.removeEventListener('raksha:goto', handler);
  }, [navigateTo]);

  const liveIncidents = useMemo(() => [...incidents].sort((a, b) => Number(b.reported_at) - Number(a.reported_at)), [incidents]);
  const activeFullModalIncident = fullModalIncident ? incidents.find((item) => item.event_id === fullModalIncident.event_id) || fullModalIncident : null;
  const selectedReportCurrent = selectedReport ? reports.find((item) => item.report_id === selectedReport.report_id) || selectedReport : null;

  const handleIncidentStatus = async (incident, status, notes) => {
    setBusy(true);
    try {
      await api.updateIncidentStatus(token, incident.event_id, { status, notes });
      notify(`Incident ${incident.event_id} updated to ${status}.`);
      setFullModalIncident(null);
      await refreshData(true);
    } catch (error) { notify(error.message, 'error'); }
    finally { setBusy(false); }
  };
  const handleCreateDispatch = async (incident, payload) => {
    setBusy(true);
    try {
      const result = await api.createDispatch(token, incident.event_id, payload);
      notify(result.message || 'Internal dispatch task created.', 'warning');
      setFullModalIncident(null);
      await refreshData(true);
    } catch (error) { notify(error.message, 'error'); }
    finally { setBusy(false); }
  };
  const handleCreateReport = async (payload) => {
    setBusy(true);
    try {
      await api.createReport(token, payload); setReportModal(false); notify('Report saved to the Raksha360 Control Room queue.'); await refreshData(true); navigateTo('reports');
    } catch (error) { notify(error.message, 'error'); }
    finally { setBusy(false); }
  };
  const handleUpdateReport = async (report, status, priority) => {
    setBusy(true);
    try {
      await api.updateReportStatus(token, report.report_id, { status, priority }); setSelectedReport(null); notify(`Report ${report.report_id} updated.`); await refreshData(true);
    } catch (error) { notify(error.message, 'error'); }
    finally { setBusy(false); }
  };
  const handleUpdateDispatch = async (dispatch, status) => {
    try {
      const result = await api.updateDispatchStatus(token, dispatch.dispatch_id, { status, notes: '' });
      notify(`Internal dispatch status updated to ${status.replaceAll('_',' ')}.`, 'warning'); await refreshData(true);
    } catch (error) { notify(error.message, 'error'); }
  };

  if (!authenticated) return <NagpurAnimatedLogin onLogin={login} busy={authBusy} error={authError} onHealth={checkHealth} health={health} />;

  const currentNav = NAV.find((item) => item.id === page) || NAV[0];
  const NavIcon = currentNav.icon;

  const renderContent = () => {
    if (page === 'overview') {
      return (
        <CommandCenter
          metrics={metrics}
          incidents={liveIncidents}
          reports={reports}
          dispatches={dispatches}
          stations={stations}
          publicStations={publicStations}
          devices={devices}
          selectedIncident={selectedIncident}
          onSelectIncident={setSelectedIncident}
          onOpenFullDetail={setFullModalIncident}
          onStatus={handleIncidentStatus}
          onSelectReport={setSelectedReport}
          onMap={() => navigateTo('map')}
          loading={loading}
          busy={busy}
          token={token}
          onOpenDispatch={(inc, st) => setDispatchModalData({ incident: inc, proposedStation: st })}
        />
      );
    }
    if (page === 'incidents') {
      return (
        <IncidentsPage
          incidents={liveIncidents}
          onSelectIncident={(item) => { setSelectedIncident(item); setFullModalIncident(item); }}
          loading={loading}
          token={token}
          refresh={() => refreshData()}
          notify={notify}
        />
      );
    }
    if (page === 'reports') return <ReportsPage reports={reports} onSelectReport={setSelectedReport} loading={loading} onNewReport={() => setReportModal(true)} refresh={() => refreshData()} />;
    if (page === 'devices') return <DeviceWatchPage devices={devices} loading={loading} refresh={() => refreshData()} smtpConfigured={deviceWatchMeta.smtpConfigured} offlineThresholdSeconds={deviceWatchMeta.offlineThresholdSeconds} onMap={() => navigateTo('map')} />;
    if (page === 'dispatch') return <DispatchPage dispatches={dispatches} onUpdateDispatch={handleUpdateDispatch} loading={loading} onSelectIncident={(partial) => { const found = incidents.find((item) => item.event_id === partial.event_id); if (found) setFullModalIncident(found); else notify('Related incident record not found.', 'error'); }} />;
    if (page === 'stations') return <StationsPage stations={stations} publicStations={publicStations} helplinesData={helplines} loading={loading} refresh={() => refreshData()} />;
    if (page === 'ncrb') return <NcrbPage ncrbData={ncrbManifest} loading={loading} refresh={() => refreshData()} />;
    if (page === 'audit') return <AuditPage audit={audit} loading={loading} refresh={() => refreshData()} />;
    if (page === 'map') {
      return (
        <div className="page-content">
          <SectionHeading
            eyebrow="LIVE GEOSPATIAL VIEW"
            title="Operations map"
            description="Clear, colorful OpenFreeMap street tiles with coordinate-validated Raksha360 incident overlays. Zero Google billing dependencies."
            actions={<button className="primary-button" onClick={() => refreshData()}><RefreshCw size={15} />Refresh events</button>}
          />
          <div className="full-map-panel glass-panel">
            <MapLibreMap
              incidents={liveIncidents}
              stations={[...stations, ...publicStations.map((station) => ({ ...station, zone: station.jurisdiction_label, isDemo: false, isOfficialPublicDirectory: true }))]}
              devices={devices}
              selectedId={selectedIncident?.event_id}
              onSelect={(item) => { setSelectedIncident(item); setFullModalIncident(item); }}
            />
          </div>
          <div className="map-legal-note">
            <Layers3 size={16} />
            <span>MapLibre GL JS + OpenFreeMap Bright/Liberty vector styles. Data attribution: OpenFreeMap © OpenMapTiles · OpenStreetMap. No Google Maps Platform billing is required.</span>
          </div>
        </div>
      );
    }
    return (
      <CommandCenter
        metrics={metrics}
        incidents={liveIncidents}
        reports={reports}
        dispatches={dispatches}
        stations={stations}
        publicStations={publicStations}
        devices={devices}
        selectedIncident={selectedIncident}
        onSelectIncident={setSelectedIncident}
        onOpenFullDetail={setFullModalIncident}
        onStatus={handleIncidentStatus}
        onSelectReport={setSelectedReport}
        onMap={() => navigateTo('map')}
        loading={loading}
        busy={busy}
        token={token}
        onOpenDispatch={(inc, st) => setDispatchModalData({ incident: inc, proposedStation: st })}
      />
    );
  };

  return (
    <div className="app-shell">
      {/* Layer A — Operations Sidebar */}
      <aside className={cx('sidebar', mobileNav && 'sidebar-mobile-open')}>
        <div className="sidebar-brand">
          <div className="brand-mark"><ShieldCheck size={22} /></div>
          <div><b>RAKSHA<span>360</span></b><small>POLICE CONTROL ROOM</small></div>
          <button className="mobile-close" onClick={() => setMobileNav(false)}><X size={17} /></button>
        </div>

        <div className="control-status">
          <span className={cx('signal-dot', connected ? 'signal-green signal-pulse' : 'signal-red')} />
          <div>
            <b>{connected ? 'Realtime connected' : 'Connecting to server'}</b>
            <small>{connected ? 'WebSocket stream active' : 'REST polling fallback active'}</small>
          </div>
          <Radio size={15} />
        </div>

        <nav className="sidebar-nav">
          {['OPERATIONS', 'CASEWORK', 'REFERENCE'].map((group) => (
            <div className="nav-group" key={group}>
              <div className="nav-group-label">{group}</div>
              {NAV.filter((item) => item.group === group).map((item) => {
                const Icon = item.icon;
                const count = item.badgeKey ? Number(metrics?.[item.badgeKey] || 0) : 0;
                return (
                  <button
                    key={item.id}
                    className={cx('nav-item', page === item.id && 'nav-item-active')}
                    onClick={() => navigateTo(item.id)}
                  >
                    <Icon size={17} />
                    <span>{item.label}</span>
                    {item.badgeKey && count > 0 && (
                      <i className={cx('nav-badge', item.badgeKey === 'openIncidents' ? 'badge-red' : '')}>{count}</i>
                    )}
                  </button>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-spacer" />

        <div className="sidebar-callout">
          <div className="callout-icon"><BellRing size={16} /></div>
          <div>
            <b>Emergency protocol</b>
            <p>This console does not replace 112 or an authorized police dispatch system.</p>
            <a href="tel:112">Open system dialer <ArrowUpRight size={12} /></a>
          </div>
        </div>

        <div className="sidebar-bottom">
          <span className="sidebar-avatar">R</span>
          <div className="sidebar-user">
            <b>Local operator</b>
            <small>Control-room session</small>
          </div>
          <button title="Sign out" className="icon-button" onClick={logout}><LogOut size={15} /></button>
        </div>
      </aside>

      {/* Layer B — Main Operations Workspace */}
      <main className="main-shell">
        <TopBar
          connected={connected}
          lastUpdate={lastUpdate}
          onRefresh={() => refreshData()}
          refreshing={refreshing}
          page={page}
          onNewReport={() => setReportModal(true)}
          onLogout={logout}
          userMenuOpen={userMenuOpen}
          setUserMenuOpen={setUserMenuOpen}
          showDemoMode={showDemoMode}
          onToggleDemoMode={handleToggleDemoMode}
        />
        {showDemoMode && (
          <div className="qa-mode-banner">
            <AlertTriangle size={15} />
            <span><b>DEMO / TEST DATA MODE ACTIVE</b> — Displaying synthetic test fixtures alongside operational data.</span>
            <button type="button" onClick={handleToggleDemoMode}>Switch to live operational data</button>
          </div>
        )}
        <div className="page-title-mobile">
          <button className="icon-button" onClick={() => setMobileNav(true)}><Menu size={18} /></button>
          <span><NavIcon size={17} />{currentNav.label}</span>
        </div>
        {renderContent()}
        <footer className="app-footer">
          <span>RAKSHA360 CONTROL ROOM <i>•</i> {isApiConfigured() ? 'LOCAL DEVELOPMENT INSTANCE' : 'PUBLIC PREVIEW'}</span>
          <span><Signal tone={connected ? 'green' : 'red'} label={connected ? 'WebSocket synced' : (isApiConfigured() ? 'Polling fallback' : 'Backend unconfigured')} /><i>•</i> DB: SQLite</span>
        </footer>
      </main>

      {/* Layer C — Contextual Full Detail Modal */}
      {activeFullModalIncident && (
        <IncidentDetail
          incident={activeFullModalIncident}
          dispatches={dispatches}
          stations={[...publicStations.map((station) => ({ ...station, zone: station.jurisdiction_label, isDemo: false, isOfficialPublicDirectory: true })), ...stations]}
          publicStations={publicStations}
          token={token}
          onClose={() => setFullModalIncident(null)}
          onStatus={handleIncidentStatus}
          onDispatch={handleCreateDispatch}
          busy={busy}
          toast={notify}
        />
      )}
      {dispatchModalData && (
        <DispatchModal
          incident={dispatchModalData.incident}
          stations={stations}
          publicStations={publicStations}
          proposedStation={dispatchModalData.proposedStation}
          onClose={() => setDispatchModalData(null)}
          onCreate={(payload) => {
            handleCreateDispatch(dispatchModalData.incident, payload);
            setDispatchModalData(null);
          }}
          busy={busy}
        />
      )}
      {selectedReportCurrent && (
        <ReportDetail
          report={selectedReportCurrent}
          onClose={() => setSelectedReport(null)}
          onUpdate={handleUpdateReport}
          busy={busy}
        />
      )}
      {reportModal && (
        <ReportModal onClose={() => setReportModal(false)} onCreate={handleCreateReport} busy={busy} />
      )}
      {toast && (
        <div className={cx('toast-message', `toast-${toast.tone}`)}>
          <span>{toast.tone === 'error' ? <AlertTriangle size={17} /> : toast.tone === 'warning' ? <AlertOctagon size={17} /> : <CheckCircle2 size={17} />}</span>
          <p>{toast.message}</p>
          <button onClick={() => setToast(null)}><X size={15} /></button>
        </div>
      )}
    </div>
  );
}
