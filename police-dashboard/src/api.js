const DEFAULT_BASE = 'http://127.0.0.1:4000/api/v1';
const BASE = (import.meta.env.VITE_API_BASE_URL || DEFAULT_BASE).replace(/\/$/, '');

export const getApiBase = () => BASE;

export async function apiRequest(path, { token, method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['X-Dashboard-Token'] = token;
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  });
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : {}; } catch { data = { detail: text || 'Empty response' }; }
  if (!response.ok) {
    const detail = typeof data.detail === 'string' ? data.detail : JSON.stringify(data.detail || data);
    throw new Error(`${response.status}: ${detail}`);
  }
  return data;
}

export async function verifyDashboardToken(token) {
  return apiRequest('/dashboard/session', { method: 'POST', body: { token } });
}

export function createLiveSocket(token) {
  const withoutApi = BASE.replace(/\/api\/v1\/?$/, '');
  const url = new URL(withoutApi);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = `${url.pathname.replace(/\/$/, '')}/ws/police`;
  url.searchParams.set('token', token);
  return new WebSocket(url.toString());
}

export const api = {
  overview: (token) => apiRequest('/dashboard/overview', { token }),
  incidents: (token, filters = {}) => {
    const q = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') q.set(key, String(value)); });
    return apiRequest(`/incidents${q.size ? `?${q.toString()}` : ''}`, { token });
  },
  incident: (token, id) => apiRequest(`/incidents/${encodeURIComponent(id)}`, { token }),
  nearbyStations: (token, id, filters = {}) => {
    const q = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') q.set(key, String(value)); });
    return apiRequest(`/incidents/${encodeURIComponent(id)}/nearby-stations${q.size ? `?${q.toString()}` : ''}`, { token });
  },
  updateIncidentStatus: (token, id, body) => apiRequest(`/incidents/${encodeURIComponent(id)}/status`, { token, method: 'PATCH', body }),
  createDispatch: (token, id, body) => apiRequest(`/incidents/${encodeURIComponent(id)}/dispatch`, { token, method: 'POST', body }),
  reports: (token, filters = {}) => {
    const q = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') q.set(key, String(value)); });
    return apiRequest(`/reports${q.size ? `?${q.toString()}` : ''}`, { token });
  },
  createReport: (token, body) => apiRequest('/reports', { token, method: 'POST', body }),
  updateReportStatus: (token, id, body) => apiRequest(`/reports/${encodeURIComponent(id)}/status`, { token, method: 'PATCH', body }),
  dispatches: (token, filters = {}) => {
    const q = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') q.set(key, String(value)); });
    return apiRequest(`/dispatches${q.size ? `?${q.toString()}` : ''}`, { token });
  },
  updateDispatchStatus: (token, id, body) => apiRequest(`/dispatches/${encodeURIComponent(id)}/status`, { token, method: 'PATCH', body }),
  stations: (token) => apiRequest('/stations', { token }),
  policeStationDirectory: (token) => apiRequest('/police-station-directory', { token }),
  helplines: (token) => apiRequest('/public-helplines', { token }),
  ncrbManifest: (token) => apiRequest('/ncrb-manifest', { token }),
  devices: (token) => apiRequest('/devices/status', { token }),
  audit: (token) => apiRequest('/audit-log?limit=100', { token }),
};

export function formatTime(value) {
  if (value === null || value === undefined || value === '') return '—';
  const number = typeof value === 'number' ? value : Number(value);
  const date = Number.isFinite(number) ? new Date(number) : new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function timeAgo(value) {
  const number = typeof value === 'number' ? value : Number(value);
  const date = Number.isFinite(number) ? new Date(number) : new Date(value);
  if (Number.isNaN(date.getTime())) return 'Time unavailable';
  const seconds = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

export function signalTone(value) {
  const text = String(value || '').toLowerCase();
  if (['critical', 'high', 'new', 'escalated', 'false alarm', 'failed'].some((item) => text.includes(item))) return 'red';
  if (['medium', 'acknowledged', 'assigned', 'responding', 'requested', 'pending', 'review'].some((item) => text.includes(item))) return 'amber';
  if (['resolved', 'closed', 'completed', 'on scene reported', 'verified'].some((item) => text.includes(item))) return 'green';
  return 'blue';
}
