import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Activity, AlertTriangle, ArrowRight, CheckCircle2, ChevronRight, Eye, EyeOff,
  Flame, KeyRound, Lock, Radio, Shield, ShieldCheck, Wifi, X,
  RadioTower, MapPinned, Video, ClipboardList, BarChart3, Users
} from 'lucide-react';
import './NagpurAnimatedLogin.css';

// Nagpur Landmark Vector SVGs
function DeekshabhoomiSvg({ width = 160, height = 90 }) {
  return (
    <svg width={width} height={height} viewBox="0 0 160 90" fill="none" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <radialGradient id="deekshaDomeGlow" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#FDE68A" stopOpacity="0.9" />
          <stop offset="60%" stopColor="#F59E0B" stopOpacity="0.7" />
          <stop offset="100%" stopColor="#B45309" stopOpacity="0.3" />
        </radialGradient>
        <linearGradient id="deekshaGoldArch" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#FDE68A" />
          <stop offset="100%" stopColor="#92400E" />
        </linearGradient>
      </defs>
      {/* Glow Halo */}
      <ellipse cx="80" cy="46" rx="52" ry="38" fill="url(#deekshaDomeGlow)" opacity="0.3" filter="blur(8px)" />
      {/* Base Platform */}
      <rect x="18" y="72" width="124" height="10" rx="3" fill="#1E293B" stroke="#F59E0B" strokeWidth="1.2" />
      <rect x="26" y="64" width="108" height="8" rx="2" fill="#0F172A" stroke="#FBBF24" strokeWidth="1" />
      {/* Lower Arches */}
      <path d="M34 64 V56 Q40 50 46 56 V64 M52 64 V56 Q58 50 64 56 V64 M68 64 V54 Q80 44 92 54 V64 M96 64 V56 Q102 50 108 56 V64 M114 64 V56 Q120 50 126 56 V64" stroke="#F59E0B" strokeWidth="1.2" fill="none" />
      {/* Main Hemisphere Dome */}
      <path d="M30 64 C30 32, 130 32, 130 64 Z" fill="url(#deekshaDomeGlow)" stroke="#FDE68A" strokeWidth="1.5" />
      {/* Decorative Horizontal Ribs */}
      <path d="M38 52 Q80 42 122 52" stroke="#FEF3C7" strokeWidth="0.8" opacity="0.7" fill="none" />
      <path d="M48 40 Q80 34 112 40" stroke="#FEF3C7" strokeWidth="0.8" opacity="0.8" fill="none" />
      {/* Pinnacle Harmika & Chhatri */}
      <rect x="76" y="24" width="8" height="7" rx="1" fill="#FDE68A" stroke="#92400E" strokeWidth="0.8" />
      <line x1="80" y1="24" x2="80" y2="12" stroke="#FDE68A" strokeWidth="1.8" />
      <circle cx="80" cy="11" r="2.2" fill="#F59E0B" />
      <path d="M74 16 L80 12 L86 16 Z" fill="#FBBF24" />
    </svg>
  );
}

function ZeroMilePillarSvg({ width = 70, height = 100 }) {
  return (
    <svg width={width} height={height} viewBox="0 0 70 100" fill="none" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="pillarSandstone" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stopColor="#F59E0B" />
          <stop offset="50%" stopColor="#FDE68A" />
          <stop offset="100%" stopColor="#D97706" />
        </linearGradient>
      </defs>
      {/* Beacon Halo */}
      <circle cx="35" cy="14" r="14" fill="#38BDF8" opacity="0.25" filter="blur(6px)" />
      {/* Hexagonal Base Pedestal */}
      <polygon points="12,94 58,94 54,84 16,84" fill="#1E293B" stroke="#F59E0B" strokeWidth="1.2" />
      <polygon points="16,84 54,84 50,74 20,74" fill="#0F172A" stroke="#FBBF24" strokeWidth="1" />
      {/* Sandstone Pillar Shaft */}
      <polygon points="23,74 47,74 44,24 26,24" fill="url(#pillarSandstone)" stroke="#B45309" strokeWidth="1.2" />
      {/* Benchmark Horizontal Inscriptions */}
      <line x1="28" y1="62" x2="42" y2="62" stroke="#78350F" strokeWidth="1" />
      <line x1="29" y1="50" x2="41" y2="50" stroke="#78350F" strokeWidth="1" />
      <line x1="30" y1="38" x2="40" y2="38" stroke="#78350F" strokeWidth="1" />
      {/* Capital Cap & Finial Stone */}
      <rect x="24" y="20" width="22" height="4" rx="1" fill="#FDE68A" stroke="#78350F" strokeWidth="0.8" />
      <polygon points="26,20 44,20 35,12" fill="#FBBF24" stroke="#92400E" strokeWidth="1" />
      <circle cx="35" cy="10" r="2" fill="#38BDF8" />
    </svg>
  );
}

function RakshaEmblemSvg({ size = 48 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="shieldGrad" x1="32" y1="4" x2="32" y2="60" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#1E3A8A" />
          <stop offset="50%" stopColor="#0F172A" />
          <stop offset="100%" stopColor="#1E293B" />
        </linearGradient>
        <linearGradient id="goldRim" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FDE68A" />
          <stop offset="50%" stopColor="#F59E0B" />
          <stop offset="100%" stopColor="#D97706" />
        </linearGradient>
      </defs>
      {/* Outer Glow Shield */}
      <path d="M32 4 L54 14 V32 C54 46 44 56 32 60 C20 56 10 46 10 32 V14 Z" fill="url(#shieldGrad)" stroke="url(#goldRim)" strokeWidth="2.5" />
      {/* Inner Accent Ring */}
      <path d="M32 9 L49 17 V32 C49 43 41 51 32 55 C23 51 15 43 15 32 V17 Z" stroke="#38BDF8" strokeWidth="1" strokeDasharray="3 2" fill="none" opacity="0.6" />
      {/* Central Flame of Vigilance */}
      <path d="M32 18 C35 25 41 29 38 37 C36 43 28 43 26 37 C24 31 30 25 32 18 Z" fill="#F59E0B" opacity="0.95" />
      <path d="M32 24 C33 28 37 31 35 36 C34 40 30 40 29 36 C28 32 31 28 32 24 Z" fill="#FDE68A" />
      {/* Protective Star Point */}
      <circle cx="32" cy="46" r="2.5" fill="#38BDF8" />
    </svg>
  );
}

function NagpurSkylineSvg() {
  return (
    <svg className="skyline-svg-silhouette" viewBox="0 0 400 36" fill="none" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
      {/* Nagpur Landmark Horizon Silhouette */}
      <path
        d="M0 36 L15 36 L25 32 L35 32 L45 36 L85 36 L92 30 L98 30 L102 24 L106 30 L112 30 L118 36 L170 36 
           C180 36, 185 22, 200 22 C215 22, 220 36, 230 36 L280 36 L286 28 L290 28 L294 14 L298 28 L302 28 L308 36 
           L350 36 L360 33 L375 33 L385 36 L400 36"
        fill="rgba(56, 189, 248, 0.12)"
        stroke="rgba(56, 189, 248, 0.45)"
        strokeWidth="1.2"
      />
    </svg>
  );
}

// Nagpur Digital Twin Sector Nodes
const NAGPUR_SECTORS = [
  { id: 'sitabuldi', name: 'Sitabuldi', sub: 'Central Zone', x: 50, y: 50, tone: 'amber', isCenter: true },
  { id: 'dharampeth', name: 'Dharampeth', sub: 'West Sector', x: 34, y: 44, tone: 'orange' },
  { id: 'trimurti', name: 'Trimurti Nagar', sub: 'South-West', x: 28, y: 64, tone: 'blue' },
  { id: 'mihan', name: 'Mihan SEZ', sub: 'South Industrial', x: 70, y: 72, tone: 'cyan' },
  { id: 'koradi', name: 'Koradi', sub: 'North Perimeter', x: 54, y: 22, tone: 'blue' },
];

export default function NagpurAnimatedLogin({ onLogin, busy, error, onHealth, health }) {
  const [token, setToken] = useState('');
  const [showToken, setShowToken] = useState(false);
  const [keepSignedIn, setKeepSignedIn] = useState(true);
  const [showTokenHelp, setShowTokenHelp] = useState(false);
  const [latencyMs, setLatencyMs] = useState(null);
  const [authSuccessing, setAuthSuccessing] = useState(false);

  const canvasRef = useRef(null);
  const animFrameId = useRef(null);

  // Measure genuine backend health and response latency
  const measureHealth = useCallback(async () => {
    const start = performance.now();
    try {
      if (onHealth) {
        const result = await onHealth();
        const duration = Math.round(performance.now() - start);
        setLatencyMs(duration);
      }
    } catch {
      setLatencyMs(null);
    }
  }, [onHealth]);

  useEffect(() => {
    measureHealth();
  }, [measureHealth]);

  // Frontend-only Canvas Animated Holographic Map
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let width = (canvas.width = canvas.offsetWidth);
    let height = (canvas.height = canvas.offsetHeight);

    const handleResize = () => {
      if (!canvas) return;
      width = canvas.width = canvas.offsetWidth;
      height = canvas.height = canvas.offsetHeight;
    };
    window.addEventListener('resize', handleResize);

    // Respect prefers-reduced-motion
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Road network node coordinates mapped to canvas
    const getCoords = (pctX, pctY) => ({
      x: (pctX / 100) * width,
      y: (pctY / 100) * height,
    });

    // Arterial road network connections (Nagpur Arteries)
    const routes = [
      { from: [50, 50], to: [34, 44] }, // Sitabuldi -> Dharampeth
      { from: [34, 44], to: [28, 64] }, // Dharampeth -> Trimurti Nagar
      { from: [50, 50], to: [28, 64] }, // Sitabuldi -> Wardha Rd / Trimurti
      { from: [50, 50], to: [70, 72] }, // Sitabuldi -> Mihan SEZ (Wardha Corridor)
      { from: [50, 50], to: [54, 22] }, // Sitabuldi -> Koradi (Kamptee / Koradi Rd)
      { from: [34, 44], to: [54, 22] }, // Ring Road West
      { from: [28, 64], to: [70, 72] }, // Ring Road South
      { from: [70, 72], to: [85, 45] }, // Ring Road East
      { from: [85, 45], to: [54, 22] }, // Ring Road North-East
    ];

    // Light pulses moving across roads
    const pulses = routes.map((route, i) => ({
      route,
      t: (i * 0.15) % 1,
      speed: 0.003 + (i % 3) * 0.0015,
      color: i % 2 === 0 ? '#38BDF8' : '#F59E0B',
      size: 3 + (i % 2),
    }));

    // Floating particles
    const particles = Array.from({ length: 24 }, () => ({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.3,
      vy: -0.2 - Math.random() * 0.3,
      radius: 1 + Math.random() * 1.5,
      alpha: 0.1 + Math.random() * 0.4,
      hue: Math.random() > 0.4 ? '199, 89%, 48%' : '38, 92%, 50%',
    }));

    let animationRunning = true;

    // Pause when page is hidden to save CPU and battery
    const handleVisibility = () => {
      animationRunning = !document.hidden;
      if (animationRunning && !prefersReducedMotion) {
        lastTime = performance.now();
        render(lastTime);
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    let lastTime = performance.now();

    const render = (currentTime) => {
      if (!animationRunning || prefersReducedMotion) {
        // Static frame render
        drawScene(0);
        return;
      }

      const delta = (currentTime - lastTime) / 1000;
      lastTime = currentTime;

      drawScene(delta);
      animFrameId.current = requestAnimationFrame(render);
    };

    const drawScene = (delta) => {
      ctx.clearRect(0, 0, width, height);

      // 1. Draw Ring Road / Outer Perimeter contour
      ctx.save();
      ctx.beginPath();
      ctx.strokeStyle = 'rgba(56, 189, 248, 0.18)';
      ctx.lineWidth = 1.2;
      ctx.setLineDash([4, 4]);

      const perimeter = [
        getCoords(54, 18),
        getCoords(85, 42),
        getCoords(72, 78),
        getCoords(26, 68),
        getCoords(20, 38),
      ];
      ctx.moveTo(perimeter[0].x, perimeter[0].y);
      for (let i = 1; i < perimeter.length; i++) {
        ctx.lineTo(perimeter[i].x, perimeter[i].y);
      }
      ctx.closePath();
      ctx.stroke();
      ctx.restore();

      // 2. Draw Arterial Road Lines
      routes.forEach(({ from, to }) => {
        const start = getCoords(from[0], from[1]);
        const end = getCoords(to[0], to[1]);

        ctx.beginPath();
        ctx.strokeStyle = 'rgba(56, 189, 248, 0.28)';
        ctx.lineWidth = 1.5;
        ctx.moveTo(start.x, start.y);
        ctx.lineTo(end.x, end.y);
        ctx.stroke();

        // Secondary glow line
        ctx.beginPath();
        ctx.strokeStyle = 'rgba(37, 99, 235, 0.12)';
        ctx.lineWidth = 4;
        ctx.moveTo(start.x, start.y);
        ctx.lineTo(end.x, end.y);
        ctx.stroke();
      });

      // 3. Draw Traveling Light Pulses
      pulses.forEach((pulse) => {
        if (!prefersReducedMotion && delta > 0) {
          pulse.t += pulse.speed;
          if (pulse.t > 1) pulse.t = 0;
        }

        const start = getCoords(pulse.route.from[0], pulse.route.from[1]);
        const end = getCoords(pulse.route.to[0], pulse.route.to[1]);
        const px = start.x + (end.x - start.x) * pulse.t;
        const py = start.y + (end.y - start.y) * pulse.t;

        ctx.save();
        ctx.shadowBlur = 10;
        ctx.shadowColor = pulse.color;
        ctx.fillStyle = pulse.color;
        ctx.beginPath();
        ctx.arc(px, py, pulse.size, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      });

      // 4. Draw Atmospheric Floating Particles
      particles.forEach((p) => {
        if (!prefersReducedMotion && delta > 0) {
          p.x += p.vx;
          p.y += p.vy;
          if (p.y < 0) {
            p.y = height;
            p.x = Math.random() * width;
          }
          if (p.x < 0) p.x = width;
          if (p.x > width) p.x = 0;
        }

        ctx.beginPath();
        ctx.fillStyle = `hsla(${p.hue}, ${p.alpha})`;
        ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
        ctx.fill();
      });
    };

    animFrameId.current = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animFrameId.current);
      window.removeEventListener('resize', handleResize);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, []);

  // Form submission handler with duplicate protection and success transition
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (busy || !token.trim()) return;

    try {
      await onLogin(token.trim());
      setAuthSuccessing(true);
    } catch {
      // error handled by parent auth state
    }
  };

  const isConfiguredBackend = Boolean(health && health.status === 'ONLINE');
  const isWakingUp = Boolean(health && health.status === 'UNREACHABLE');

  return (
    <div className="nagpur-login-viewport" id="rakshaLoginScreen">
      {/* Background Visual Layers */}
      <div className="nagpur-ambient-glow-cyan" />
      <div className="nagpur-ambient-glow-amber" />
      <div className="nagpur-control-room-grid" />

      {/* Top Navigation Banner */}
      <header className="nagpur-top-bar">
        <div className="nagpur-branding-group">
          <div className="raksha-shield-emblem" aria-hidden="true">
            <RakshaEmblemSvg size={44} />
          </div>
          <div className="nagpur-brand-text">
            <h1 className="nagpur-brand-title">
              RAKSHA<span>360</span>
            </h1>
            <span className="nagpur-brand-dept">NAGPUR POLICE CONTROL ROOM</span>
            <span className="nagpur-brand-tagline">SAFE CITIZENS &bull; SMART POLICING &bull; STRONGER NAGPUR</span>
          </div>
        </div>

        <div className="nagpur-marathi-lockup" aria-label="Nagpur City Police Identity">
          <span className="nagpur-marathi-main">नागपूर शहर पोलीस</span>
          <span className="nagpur-marathi-sub">सद्रक्षणाय &bull; सुरक्षा &bull; सेवा</span>
        </div>

        <div className="nagpur-tricolor-badge">
          <div className="tricolor-strip" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
          <span>FOR A SAFER NAGPUR</span>
        </div>
      </header>

      {/* Main Command Center Stage */}
      <main className="nagpur-main-stage">
        {/* Left Visual Area: Landmarks & 3D Holographic Map */}
        <section className="nagpur-showcase-pane" aria-label="Nagpur Control Room Geospatial Twin">
          {/* Horizon with Deekshabhoomi and Zero Mile */}
          <div className="nagpur-landmarks-horizon">
            <div className="landmark-callout landmark-callout-deekshabhoomi">
              <div className="landmark-badge">
                <i /> Deekshabhoomi Nagpur
              </div>
              <div className="landmark-svg-container">
                <DeekshabhoomiSvg width={150} height={85} />
              </div>
            </div>

            <div className="landmark-callout landmark-callout-zeromile">
              <div className="landmark-badge">
                <i /> Zero Mile Stone Nagpur
              </div>
              <div className="landmark-svg-container">
                <ZeroMilePillarSvg width={65} height={92} />
              </div>
            </div>
          </div>

          {/* 3D Holographic Map Stage */}
          <div className="nagpur-map-stage-3d">
            {/* Top HUD Cards */}
            <div className="nagpur-hud-incident-pill">
              <div className="nagpur-hud-incident-head">
                <span className="hud-pulse-dot" /> LIVE OPERATIONS READY
              </div>
              <div className="nagpur-hud-metric-row">
                <span className="nagpur-hud-metric-val">NAGPUR</span>
                <span className="nagpur-hud-metric-sub">Central Sector</span>
              </div>
            </div>

            <div className="nagpur-hud-metrics-bar">
              <div className="nagpur-metric-chip">
                <Users size={16} className="metric-chip-icon" />
                <div className="metric-chip-info">
                  <span className="metric-chip-val">Nagpur City</span>
                  <span className="metric-chip-lbl">Commissionerate</span>
                </div>
              </div>
              <div className="nagpur-metric-chip">
                <ShieldCheck size={16} className="metric-chip-icon" />
                <div className="metric-chip-info">
                  <span className="metric-chip-val">112 Ready</span>
                  <span className="metric-chip-lbl">Emergency Link</span>
                </div>
              </div>
            </div>

            {/* Isometric Plane & Vector Canvas */}
            <div className="nagpur-isomap-plane">
              <canvas ref={canvasRef} className="nagpur-vector-canvas" />

              {/* Decorative Nagpur Sector Location Pins */}
              {NAGPUR_SECTORS.map((sector) => (
                <div
                  key={sector.id}
                  className="nagpur-pin-node"
                  style={{
                    left: `${sector.x}%`,
                    top: `${sector.y}%`,
                    color: sector.tone === 'amber' ? '#F59E0B' : sector.tone === 'orange' ? '#FB923C' : '#38BDF8',
                  }}
                >
                  <div className="nagpur-pin-marker" style={{ background: sector.isCenter ? '#F59E0B' : 'rgba(8,15,31,0.9)' }}>
                    <div className="nagpur-pin-pulse" />
                    <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#FFFFFF' }} />
                  </div>
                  <div className="nagpur-pin-card">
                    <span>{sector.name}</span>
                    <small>{sector.sub}</small>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Bottom Control Room Capabilities Ribbon */}
          <div className="nagpur-capabilities-dock" aria-label="Control Room Features">
            <div className="capability-dock-btn">
              <RadioTower size={17} />
              <span>Incident Stream</span>
            </div>
            <div className="capability-dock-btn">
              <MapPinned size={17} />
              <span>Safety Map</span>
            </div>
            <div className="capability-dock-btn">
              <Video size={17} />
              <span>CCTV Network</span>
            </div>
            <div className="capability-dock-btn">
              <ClipboardList size={17} />
              <span>Case Manager</span>
            </div>
            <div className="capability-dock-btn">
              <BarChart3 size={17} />
              <span>Safety Insights</span>
            </div>
            <div className="capability-dock-btn">
              <Users size={17} />
              <span>Field Units</span>
            </div>
          </div>
        </section>

        {/* Right Area: Holographic Operator Login Terminal */}
        <section className="nagpur-login-terminal-pane" aria-label="Operator Authentication Terminal">
          <div className="nagpur-terminal-card">
            <div className="terminal-underglow" />

            {/* Terminal Header */}
            <div className="terminal-header">
              <div className="terminal-title-stack">
                <span className="terminal-eyebrow">WELCOME TO</span>
                <h2 className="terminal-main-title">
                  RAKSHA<span>360</span>
                </h2>
                <span className="terminal-station-title">NAGPUR POLICE CONTROL ROOM</span>
                <span className="terminal-desc-sub">INTEGRATED SAFETY OPERATIONS PLATFORM</span>
              </div>

              {/* Mini Nagpur Radar locator badge */}
              <div className="nagpur-mini-radar-badge" title="Nagpur Coordinate Radar Grid">
                <div className="radar-sweep-beam" />
                <div className="radar-crosshairs" />
                <span className="radar-label">NAGPUR</span>
              </div>
            </div>

            {/* Operator Session Subheading */}
            <div className="operator-callout-card">
              <div className="operator-icon-wrap">
                <Lock size={16} />
              </div>
              <div className="operator-info-wrap">
                <span className="operator-head-text">SECURE OPERATOR ACCESS</span>
                <span className="operator-sub-text">Enter your dashboard access token to continue</span>
              </div>
            </div>

            {/* Access Form */}
            <form className="terminal-auth-form" onSubmit={handleSubmit}>
              <div className="terminal-field-group">
                <label className="terminal-field-label" htmlFor="dashboardTokenInput">
                  Dashboard Access Token
                </label>
                <div className="terminal-input-wrapper">
                  <span className="terminal-input-icon">
                    <KeyRound size={17} />
                  </span>
                  <input
                    id="dashboardTokenInput"
                    type={showToken ? 'text' : 'password'}
                    className="terminal-token-input"
                    placeholder="Enter dashboard access token"
                    value={token}
                    onChange={(e) => setToken(e.target.value)}
                    autoComplete="current-password"
                    disabled={busy || authSuccessing}
                    required
                  />
                  <button
                    type="button"
                    className="terminal-visibility-btn"
                    onClick={() => setShowToken(!showToken)}
                    title={showToken ? 'Hide token' : 'Show token'}
                    aria-label={showToken ? 'Hide token' : 'Show token'}
                  >
                    {showToken ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {/* Error Alert */}
              {error && (
                <div className="terminal-inline-error" role="alert">
                  <AlertTriangle size={15} style={{ flexShrink: 0 }} />
                  <span>{error}</span>
                </div>
              )}

              {/* Success Transition Alert */}
              {authSuccessing && (
                <div className="terminal-success-pill" role="status">
                  <CheckCircle2 size={16} />
                  <span>Session Authenticated &bull; Opening Control Room…</span>
                </div>
              )}

              {/* Radiant Action Button */}
              <button
                type="submit"
                className="terminal-submit-btn"
                disabled={busy || authSuccessing}
                id="accessControlRoomBtn"
              >
                <div className="terminal-submit-sweep" />
                {busy ? (
                  <>
                    <Activity size={18} className="spin-icon" />
                    <span>Verifying session…</span>
                  </>
                ) : (
                  <>
                    <span>Access Control Room</span>
                    <ArrowRight size={18} />
                  </>
                )}
              </button>

              {/* Checkbox & Forgot Token Link */}
              <div className="terminal-options-row">
                <label className="terminal-checkbox-label">
                  <input
                    type="checkbox"
                    className="terminal-checkbox"
                    checked={keepSignedIn}
                    onChange={(e) => setKeepSignedIn(e.target.checked)}
                  />
                  <span>Keep me signed in on this device</span>
                </label>

                <button
                  type="button"
                  className="terminal-forgot-link"
                  onClick={() => setShowTokenHelp(true)}
                >
                  Forgot token?
                </button>
              </div>
            </form>

            {/* Real-Time Backend Connection Pill */}
            <div className="terminal-connection-pill">
              <div className="conn-pill-left">
                <span
                  className={`conn-status-dot ${
                    isConfiguredBackend ? '' : isWakingUp ? 'dot-checking' : 'dot-error'
                  }`}
                />
                <div className="conn-text-stack">
                  <span className="conn-label-title">Backend Connection</span>
                  <span className="conn-label-sub">
                    {health?.service
                      ? `Connected to ${health.service}`
                      : isWakingUp
                      ? 'Reconnecting to Raksha360 API…'
                      : 'Connected to Raksha360 API'}
                  </span>
                </div>
              </div>

              <div className="conn-pill-right">
                <Wifi size={13} />
                <span>{latencyMs ? `${latencyMs} ms` : health ? 'Active' : 'Checking…'}</span>
              </div>
            </div>

            {/* Operational Disclosure Notice */}
            <div className="terminal-disclosure-card">
              <AlertTriangle size={18} className="disclosure-icon" />
              <div className="disclosure-body">
                <span className="disclosure-title">Police dispatch (112) gateway not configured</span>
                <span className="disclosure-desc">
                  This system is for internal coordination and situational awareness.
                </span>
              </div>
            </div>

            {/* Architectural Skyline Silhouette Footer */}
            <div className="terminal-skyline-footer">
              <NagpurSkylineSvg />
              <div className="skyline-caption">
                NAGPUR <span>&bull; SMART. SAFE. TOGETHER.</span>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* Forgot Token Information Modal */}
      {showTokenHelp && (
        <div className="nagpur-token-help-modal" onClick={() => setShowTokenHelp(false)}>
          <div className="nagpur-token-modal-card" onClick={(e) => e.stopPropagation()}>
            <h3>
              <KeyRound size={18} style={{ color: '#F59E0B' }} /> Dashboard Access Token
            </h3>
            <p>
              The Raksha360 dashboard access token is configured securely on the backend server environment variable:
              <br />
              <code>RAKSHA360_DASHBOARD_TOKEN</code>.
            </p>
            <p>
              In production, please consult your deployment administrator or inspect the Render environment settings to obtain or rotate the authorized operator token.
            </p>
            <button
              type="button"
              className="primary-button full-width"
              onClick={() => setShowTokenHelp(false)}
            >
              Understood
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
