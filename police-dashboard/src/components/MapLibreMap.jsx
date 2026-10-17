import { useEffect, useMemo, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import { LocateFixed, Rotate3D, Sun, Moon, Sparkles, Maximize2 } from 'lucide-react';

const NAGPUR = [79.0882, 21.1458];
const BASEMAP_STYLES = {
  bright: 'https://tiles.openfreemap.org/styles/bright',
  liberty: 'https://tiles.openfreemap.org/styles/liberty',
  dark: 'https://tiles.openfreemap.org/styles/dark',
};

function hasCoordinates(item) {
  const lat = Number(item?.latitude);
  const lng = Number(item?.longitude);
  return item?.latitude !== null && item?.latitude !== undefined && item?.latitude !== '' &&
    item?.longitude !== null && item?.longitude !== undefined && item?.longitude !== '' &&
    Number.isFinite(lat) && Number.isFinite(lng) &&
    Math.abs(lat) <= 90 && Math.abs(lng) <= 180 &&
    !(lat === 0 && lng === 0);
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[character]));
}

function severityColor(item) {
  if (item.trigger_type === 'DEVICE_OFFLINE') return '#DC2626';
  if (item.isDemo) return '#B45309';
  if (item.severity === 'Critical') return '#DC2626';
  if (item.severity === 'High') return '#D97706';
  if (item.status === 'Resolved') return '#16805D';
  return '#2563EB';
}

export default function MapLibreMap({ incidents = [], stations = [], devices = [], selectedId, onSelect }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const markersRef = useRef([]);
  const stationMarkersRef = useRef([]);
  const deviceMarkersRef = useRef([]);
  const [loaded, setLoaded] = useState(false);
  const [mapError, setMapError] = useState('');
  const [tilted, setTilted] = useState(false);
  const [basemapStyle, setBasemapStyle] = useState('bright');

  const validIncidents = useMemo(() => incidents.filter(hasCoordinates), [incidents]);

  // Only pin public directory stations if coordinates have been human verified
  const validStations = useMemo(() => stations.filter((station) =>
    hasCoordinates(station) && (station.isOfficialPublicDirectory ? Boolean(station.coordinates_verified) : true)
  ), [stations]);

  const validDevices = useMemo(() => devices.filter((device) =>
    (device.monitoringEnabled || device.status === 'OFFLINE') &&
    device.last_latitude !== null && device.last_latitude !== undefined &&
    device.last_longitude !== null && device.last_longitude !== undefined &&
    Number.isFinite(Number(device.last_latitude)) && Number.isFinite(Number(device.last_longitude))
  ), [devices]);

  // Try to safely add 3D buildings layer if building geometry is present in vector source
  const addBuildingLayer = (map) => {
    try {
      if (map.getSource('openmaptiles') && !map.getLayer('3d-buildings')) {
        const layers = map.getStyle().layers;
        const labelLayerId = layers.find((l) => l.type === 'symbol' && l.layout?.['text-field'])?.id;
        map.addLayer({
          id: '3d-buildings',
          source: 'openmaptiles',
          'source-layer': 'building',
          type: 'fill-extrusion',
          minzoom: 14,
          paint: {
            'fill-extrusion-color': '#CBD5E1',
            'fill-extrusion-height': ['interpolate', ['linear'], ['zoom'], 14, 0, 16, ['coalesce', ['get', 'render_height'], 12]],
            'fill-extrusion-base': ['interpolate', ['linear'], ['zoom'], 14, 0, 16, ['coalesce', ['get', 'render_min_height'], 0]],
            'fill-extrusion-opacity': 0.55,
          },
        }, labelLayerId);
      }
    } catch {
      // Graceful fallback if source has no building layer
    }
  };

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return undefined;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: BASEMAP_STYLES.bright,
      center: NAGPUR,
      zoom: 12,
      pitch: 0,
      bearing: 0,
      attributionControl: false,
      cooperativeGestures: true,
    });
    mapRef.current = map;

    // Controls: Navigation, metric scale, and OpenFreeMap attribution
    map.addControl(new maplibregl.NavigationControl({ visualizePitch: true, showCompass: true }), 'top-right');
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-left');
    map.addControl(new maplibregl.AttributionControl({
      compact: false,
      customAttribution: 'OpenFreeMap © OpenMapTiles · Data from OpenStreetMap',
    }), 'bottom-right');

    map.on('load', () => {
      setLoaded(true);
      addBuildingLayer(map);
    });

    map.on('style.load', () => {
      addBuildingLayer(map);
    });

    map.on('error', (event) => {
      if (event?.error?.message) setMapError(event.error.message);
    });

    return () => {
      markersRef.current.forEach((marker) => marker.remove());
      markersRef.current = [];
      stationMarkersRef.current.forEach((marker) => marker.remove());
      stationMarkersRef.current = [];
      deviceMarkersRef.current.forEach((marker) => marker.remove());
      deviceMarkersRef.current = [];
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Spatial Focus: when selectedId changes, smoothly fly map to coordinates
  useEffect(() => {
    if (!selectedId || !mapRef.current || !loaded) return;
    const target = incidents.find((i) => i.event_id === selectedId);
    if (target && hasCoordinates(target)) {
      mapRef.current.flyTo({
        center: [Number(target.longitude), Number(target.latitude)],
        zoom: Math.max(mapRef.current.getZoom(), 14),
        pitch: tilted ? 45 : 20,
        duration: 700,
        essential: true,
      });
    }
  }, [selectedId, incidents, loaded, tilted]);

  // Update incident markers
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !loaded) return;
    markersRef.current.forEach((marker) => marker.remove());
    markersRef.current = validIncidents.map((incident) => {
      const isSelected = incident.event_id === selectedId;
      const el = document.createElement('button');
      el.type = 'button';
      el.className = `map-pin ${incident.isDemo ? 'map-pin-demo' : ''} ${incident.trigger_type === 'DEVICE_OFFLINE' ? 'map-pin-offline' : ''} ${isSelected ? 'map-pin-selected' : ''}`;
      el.style.setProperty('--pin-color', severityColor(incident));
      el.setAttribute('aria-label', `${incident.trigger_type} ${incident.event_id}`);
      el.title = `${incident.event_id} · ${incident.trigger_type} · ${incident.isDemo ? 'Demo' : incident.severity}`;
      el.addEventListener('click', () => onSelect?.(incident));

      const dot = document.createElement('span');
      dot.className = 'map-pin-core';
      el.appendChild(dot);

      const popupHtml = incident.trigger_type === 'DEVICE_OFFLINE'
        ? `<div class="map-popup">
            <b>${escapeHtml(incident.event_id)}</b>
            ${incident.isDemo ? '<span class="demo-tag">DEMO FIXTURE</span>' : ''}
            <span>DEVICE MISSED HEARTBEAT</span>
            <small style="color: #DC2626; font-weight: 700;">LAST KNOWN LOCATION · NOT LIVE</small>
            <small>Last GPS fix: ${escapeHtml(incident.last_location_at ? new Date(Number(incident.last_location_at)).toLocaleString() : 'unavailable')}</small>
            <small style="color: #637187;">Server heartbeat timeout · not a verified emergency</small>
          </div>`
        : `<div class="map-popup">
            <b>${escapeHtml(incident.event_id)}</b>
            ${incident.isDemo ? '<span class="demo-tag">DEMO FIXTURE</span>' : ''}
            <span>${escapeHtml(incident.trigger_type)}</span>
            <small>${incident.isDemo ? 'DEMO · synthetic training event' : `Status: ${escapeHtml(incident.status)} · Priority: ${escapeHtml(incident.severity)}`}</small>
            <small style="color: #637187;">${escapeHtml(incident.location_description || '')}</small>
          </div>`;

      const marker = new maplibregl.Marker({ element: el, anchor: 'center' })
        .setLngLat([Number(incident.longitude), Number(incident.latitude)])
        .setPopup(new maplibregl.Popup({ offset: 16, closeButton: false }).setHTML(popupHtml))
        .addTo(map);
      return marker;
    });
  }, [validIncidents, selectedId, loaded, onSelect]);

  // Update station markers (verified public directory stations and demo units)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !loaded) return;
    stationMarkersRef.current.forEach((marker) => marker.remove());
    stationMarkersRef.current = validStations.map((station) => {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = `map-station-pin ${station.isDemo ? 'map-station-demo' : ''} ${station.isOfficialPublicDirectory ? 'map-station-public' : ''}`;
      el.setAttribute('aria-label', `${station.isOfficialPublicDirectory ? 'Public police directory reference' : 'Response unit'} ${station.name}`);
      el.title = `${station.name} · ${station.isOfficialPublicDirectory ? 'public directory reference; availability not known' : (station.isDemo ? 'synthetic directory record' : station.status)}`;

      const core = document.createElement('span');
      core.className = 'map-station-core';
      el.appendChild(core);

      const popupHtml = `<div class="map-popup">
        <b>${escapeHtml(station.name)}</b>
        <span>${escapeHtml(station.zone || station.jurisdiction_label || 'Nagpur public directory')}</span>
        <small style="color: ${station.isOfficialPublicDirectory ? '#16805D' : '#D97706'}; font-weight: 600;">
          ${station.isOfficialPublicDirectory ? 'PUBLIC DIRECTORY REFERENCE · operational availability not known; no dispatch connection' : (station.isDemo ? 'DEMO UNIT · not a verified police station' : escapeHtml(station.status))}
        </small>
        ${station.phone ? `<small>Phone: ${escapeHtml(station.phone)}</small>` : ''}
        ${station.email ? `<small>Email: ${escapeHtml(station.email)}</small>` : ''}
        ${station.source_url ? `<small><a href="${escapeHtml(station.source_url)}" target="_blank" rel="noreferrer" style="color: #2563EB;">Official Directory Source</a></small>` : ''}
      </div>`;

      return new maplibregl.Marker({ element: el, anchor: 'center' })
        .setLngLat([Number(station.longitude), Number(station.latitude)])
        .setPopup(new maplibregl.Popup({ offset: 16, closeButton: false }).setHTML(popupHtml))
        .addTo(map);
    });
  }, [validStations, loaded]);

  // Update device markers (online/offline)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !loaded) return;
    deviceMarkersRef.current.forEach((marker) => marker.remove());
    deviceMarkersRef.current = validDevices.map((device) => {
      const offline = device.status === 'OFFLINE';
      const el = document.createElement('button');
      el.type = 'button';
      el.className = `map-device-pin ${offline ? 'map-device-offline' : 'map-device-online'}`;
      el.setAttribute('aria-label', `${offline ? 'Offline device' : 'Monitored device'} ${String(device.device_id).slice(0, 12)}`);
      el.title = `${offline ? 'OFFLINE · last known location' : 'Monitoring active'} · last heartbeat ${new Date(Number(device.last_seen_at)).toLocaleString()}`;
      el.innerHTML = '<span class="map-device-core"></span>';

      const latitude = Number(device.last_latitude);
      const longitude = Number(device.last_longitude);
      const lastLocation = device.last_location_at ? new Date(Number(device.last_location_at)).toLocaleString() : 'time unavailable';
      const popup = new maplibregl.Popup({ offset: 18, closeButton: false }).setHTML(
        `<div class="map-popup">
          <b>${escapeHtml(String(device.device_id).slice(0, 14))}…</b>
          <span>${offline ? 'DEVICE MISSED HEARTBEAT' : 'ACTIVE MONITORING SESSION'}</span>
          <small style="color: ${offline ? '#DC2626' : '#0F9D8A'}; font-weight: 700;">
            ${offline ? 'LAST KNOWN LOCATION · NOT LIVE' : 'LIVE TELEMETRY FIX'}
          </small>
          <small>GPS fix time: ${escapeHtml(lastLocation)}</small>
          <small style="color: #637187;">Server heartbeat monitoring · ${offline ? 'stopped checking in' : 'active'}</small>
        </div>`,
      );
      return new maplibregl.Marker({ element: el, anchor: 'center' })
        .setLngLat([longitude, latitude])
        .setPopup(popup)
        .addTo(map);
    });
  }, [validDevices, loaded]);

  function setPitch(next) {
    const map = mapRef.current;
    if (!map) return;
    const value = typeof next === 'number' ? next : (tilted ? 0 : 45);
    map.easeTo({ pitch: value, duration: 500 });
    setTilted(value > 20);
  }

  function focusNagpur() {
    mapRef.current?.flyTo({ center: NAGPUR, zoom: 12, pitch: tilted ? 45 : 0, bearing: 0, duration: 800 });
  }

  function fitAll() {
    const map = mapRef.current;
    if (!map || !validIncidents.length) {
      focusNagpur();
      return;
    }
    const bounds = new maplibregl.LngLatBounds();
    validIncidents.forEach((item) => bounds.extend([Number(item.longitude), Number(item.latitude)]));
    map.fitBounds(bounds, { padding: 60, maxZoom: 15, duration: 800 });
  }

  function applyStyle(styleKey) {
    if (styleKey === basemapStyle) return;
    setBasemapStyle(styleKey);
    const map = mapRef.current;
    if (map && BASEMAP_STYLES[styleKey]) {
      map.setStyle(BASEMAP_STYLES[styleKey]);
    }
  }

  return (
    <div className="map-frame">
      <div ref={containerRef} className="map-canvas" />
      {!loaded && (
        <div className="map-loading">
          <div className="loading-orbit" />
          <span>Loading OpenFreeMap vector street tiles…</span>
        </div>
      )}
      {mapError && !loaded && (
        <div className="map-error">
          <strong>Map tiles could not be loaded</strong>
          <span>Check network access to OpenFreeMap tile endpoints.</span>
        </div>
      )}
      <div className="map-overlay-top">
        <div className="map-source-pill">
          <span className="signal-dot signal-green" /> OPENFREEMAP · {basemapStyle.toUpperCase()}
        </div>
        <div className="map-actions">
          <div className="style-switch-group" role="group" aria-label="Map style switch">
            <button
              className={`style-btn ${basemapStyle === 'bright' ? 'is-active' : ''}`}
              title="Bright: clear, colorful street geography (default)"
              onClick={() => applyStyle('bright')}
            >
              <Sun size={12} /> Bright
            </button>
            <button
              className={`style-btn ${basemapStyle === 'liberty' ? 'is-active' : ''}`}
              title="Liberty: vibrant detailed cartography"
              onClick={() => applyStyle('liberty')}
            >
              <Sparkles size={12} /> Liberty
            </button>
            <button
              className={`style-btn ${basemapStyle === 'dark' ? 'is-active' : ''}`}
              title="Dark: night tactical view"
              onClick={() => applyStyle('dark')}
            >
              <Moon size={12} /> Dark
            </button>
          </div>
          <button className="map-control" title="Fit all mapped incidents" onClick={fitAll} aria-label="Fit incidents">
            <Maximize2 size={15} />
          </button>
          <button className="map-control" title="Center on Nagpur [79.0882, 21.1458]" onClick={focusNagpur} aria-label="Center Nagpur">
            <LocateFixed size={15} />
          </button>
          <button className={`map-control ${tilted ? 'is-active' : ''}`} title="3D perspective tilt (vector perspective)" onClick={() => setPitch()} aria-label="3D tilt">
            <Rotate3D size={15} />
          </button>
        </div>
      </div>
      <div className="map-legend">
        <span><i className="legend-dot legend-red" /> Critical SOS</span>
        <span><i className="legend-dot legend-amber" /> High Priority</span>
        <span><i className="legend-dot legend-green" /> Resolved</span>
        <span><i className="legend-dot legend-demo" /> Demo Fixture</span>
        <span><i className="legend-dot legend-station" /> Station / Unit</span>
        <span><i className="legend-dot legend-device" /> Device Last-Known</span>
      </div>
      <div className="map-counter">
        <b>{validIncidents.length}</b>
        <span>mapped incidents · {validStations.length} stations · {validDevices.length} devices</span>
      </div>
    </div>
  );
}
