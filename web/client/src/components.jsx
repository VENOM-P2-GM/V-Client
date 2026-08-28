import React, { useEffect, useRef } from 'react';
import { useI18n } from './i18n.jsx';

const ICONS = {
  home: <path d="M3 10.5 12 3l9 7.5V21a1 1 0 0 1-1 1h-5v-7h-6v7H4a1 1 0 0 1-1-1Z" />,
  folder: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />,
  puzzle: <path d="M9 4a2 2 0 1 1 4 0v1h4a1 1 0 0 1 1 1v4h1a2 2 0 1 1 0 4h-1v4a1 1 0 0 1-1 1h-4v1a2 2 0 1 1-4 0v-1H5a1 1 0 0 1-1-1v-4H3a2 2 0 1 1 0-4h1V6a1 1 0 0 1 1-1h4Z" />,
  users: <path d="M16 11a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm-8 1a3 3 0 1 0-3-3 3 3 0 0 0 3 3Zm0 2c-2.7 0-6 1.3-6 4v2h7v-2c0-1.5.8-2.7 2-3.4A9.6 9.6 0 0 0 8 13Zm8 1c-2.7 0-6 1.3-6 4v2h12v-2c0-2.7-3.3-4-6-4Z" />,
  cog: <path d="M12 8a4 4 0 1 0 4 4 4 4 0 0 0-4-4Zm9 4a7.8 7.8 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-2-1.2L16 3h-4l-.4 2.6a7.6 7.6 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7.8 7.8 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 2 1.2L12 21h4l.4-2.6a7.6 7.6 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6a7.8 7.8 0 0 0 .1-1.2Z" />,
  news: <path d="M4 4h13a2 2 0 0 1 2 2v12a3 3 0 0 1-3 3H5a2 2 0 0 1-2-2V5a1 1 0 0 1 1-1Zm2 3v2h9V7Zm0 4v2h9v-2Zm0 4v2h6v-2Zm8-2a2 2 0 1 0 2 2 2 2 0 0 0-2-2Z" />,
  play: <path d="M7 4.5v15l13-7.5Z" />,
  stop: <path d="M5 5h14v14H5Z" />,
  trash: <path d="M9 3h6l1 2h4v2H4V5h4Zm-1 6h10l-.8 10.2A2 2 0 0 1 15.2 21H8.8a2 2 0 0 1-2-1.8Z" />,
  plus: <path d="M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6Z" />,
  upload: <path d="M11 3h2v8h3l-4 4-4-4h3Zm-8 16h16v2H3Z" />,
  check: <path d="m9.5 16.2-4-4L4 13.7l5.5 5.5L20 8.7l-1.5-1.5Z" />,
  x: <path d="m6 5 6 6 6-6L20 6.5 14 12l6 6-1.5 1.5-6-6-6 6L5 18l6-6-6-6Z" />,
  shield: <path d="M12 2 4 5.5V11c0 5 3.4 9.3 8 10.5 4.6-1.2 8-5.5 8-10.5V5.5Z" />,
  cpu: <path d="M9 2h2v3h2V2h2v3h2a2 2 0 0 1 2 2v2h3v2h-3v2h3v2h-3v2a2 2 0 0 1-2 2h-2v3h-2v-3h-2v3H9v-3H7a2 2 0 0 1-2-2v-2H2v-2h3v-2H2V9h3V7a2 2 0 0 1 2-2h2Zm-1 5v8h8V7Z" />,
  terminal: <path d="M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Zm2 3 4 4-4 4v-2.5l2.5-2.5L6 10.5Zm6 8h6v2h-6Z" />,
  gamepad: <path d="M7 6h10a6 6 0 0 1 6 6 4 4 0 0 1-7 2.6L14.5 14h-5L6 14.6A4 4 0 0 1 1 12a6 6 0 0 1 6-6Zm0 3H5v2H3v2h2v2h2v-2h2v-2H7Zm5-1a1.5 1.5 0 1 0 1.5 1.5A1.5 1.5 0 0 0 12 8Zm4 3a1.5 1.5 0 1 0 1.5 1.5A1.5 1.5 0 0 0 16 11Z" />,
  alert: <path d="M13.7 3.6a1.5 1.5 0 0 0-2.6 0L1.8 18.6a1.5 1.5 0 0 0 1.3 2.4h17.8a1.5 1.5 0 0 0 1.3-2.4ZM11 10h2v5h-2Zm0 7h2v2h-2Z" />,
  copy: <path d="M8 8h11a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1Zm-3 4H3v9a1 1 0 0 0 1 1h9v-2H4Z" />,
  clock: <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2Zm1 5h-2v6l5 3 1-1.7-4-2.3Z" />
};

export function Icon({ name, className = 'h-5 w-5' }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      {ICONS[name] || null}
    </svg>
  );
}

export function Spinner({ className = 'h-5 w-5' }) {
  return <div className={`${className} animate-spin rounded-full border-2 border-white/20 border-t-venom`} />;
}

export function ProgressBar({ value = 0, className = '' }) {
  return (
    <div className={`h-2 w-full overflow-hidden rounded-full bg-white/10 ${className}`}>
      <div
        className="h-full rounded-full bg-gradient-to-l from-venom to-venom2 transition-all duration-300"
        style={{ width: `${Math.min(100, value)}%` }}
      />
    </div>
  );
}

const STATUS_COLORS = {
  running: 'bg-acid',
  preparing: 'bg-amber-400',
  launching: 'bg-amber-400',
  queued: 'bg-zinc-400',
  stopped: 'bg-zinc-500',
  error: 'bg-red-500',
  cancelled: 'bg-zinc-500',
  dryrun: 'bg-amber-400'
};

export function StatusDot({ status, className = '' }) {
  return (
    <span
      className={`inline-block h-2 w-2 rounded-full ${STATUS_COLORS[status] || 'bg-zinc-500'} ${status === 'running' ? 'animate-pulse' : ''} ${className}`}
    />
  );
}

export function Modal({ open, onClose, title, children, wide = false }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onClose} />
      <div className={`card relative w-full ${wide ? 'max-w-2xl' : 'max-w-md'} p-5 shadow-glow`}>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-extrabold">{title}</h3>
          <button onClick={onClose} className="text-zinc-500 transition hover:text-zinc-200">
            <Icon name="x" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Toggle({ checked, onChange }) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 shrink-0 rounded-full transition ${checked ? 'bg-venom' : 'bg-white/15'}`}
    >
      <span
        className="absolute top-0.5 h-5 w-5 rounded-full bg-white transition-all"
        style={{ insetInlineStart: checked ? '22px' : '2px' }}
      />
    </button>
  );
}

export function LogTerminal({ lines = [], height = 'h-48' }) {
  const ref = useRef(null);
  const stick = useRef(true);
  useEffect(() => {
    const el = ref.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  }, [lines]);
  return (
    <div
      ref={ref}
      dir="ltr"
      onScroll={(e) => {
        const el = e.currentTarget;
        stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
      }}
      className={`${height} w-full overflow-y-auto rounded-xl border border-white/10 bg-black/60 p-3 font-mono text-[11.5px] leading-relaxed`}
    >
      {lines.length === 0 && <div className="text-zinc-600">—</div>}
      {lines.map((l, i) => (
        <div key={i} className={`whitespace-pre-wrap break-all ${l.stream === 'err' ? 'text-red-400' : l.stream === 'out' ? 'text-zinc-200' : 'text-venom'}`}>
          {l.line}
        </div>
      ))}
    </div>
  );
}

export function Field({ label, children }) {
  return (
    <div>
      <div className="label">{label}</div>
      {children}
    </div>
  );
}

export function EngineBadge({ engine }) {
  const { t } = useI18n();
  if (engine === 'minecraft') return <span className="chip border-venom/30 bg-venom/10 text-venom">{t('engine.mine')}</span>;
  return <span className="chip border-acid/30 bg-acid/10 text-acid">{t('engine.demo')}</span>;
}

export function EmptyState({ icon = 'folder', title, desc, children }) {
  return (
    <div className="card flex flex-col items-center justify-center gap-3 p-10 text-center">
      <div className="rounded-2xl bg-venom/15 p-4 text-venom">
        <Icon name={icon} className="h-8 w-8" />
      </div>
      <div className="text-lg font-extrabold">{title}</div>
      {desc && <div className="max-w-sm text-sm text-zinc-400">{desc}</div>}
      {children}
    </div>
  );
}

export function Avatar({ name, uuid, size = 'h-10 w-10 text-sm' }) {
  const hue = uuid ? parseInt(uuid.replace(/-/g, '').slice(0, 6), 16) % 360 : 260;
  return (
    <div
      className={`${size} flex shrink-0 items-center justify-center rounded-xl font-extrabold text-white`}
      style={{ background: `linear-gradient(135deg, hsl(${hue} 70% 45%), hsl(${(hue + 60) % 360} 70% 30%))` }}
    >
      {(name || '?').slice(0, 1).toUpperCase()}
    </div>
  );
}

export function PageHeader({ title, desc, children }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 className="text-2xl font-black tracking-tight">{title}</h2>
        {desc && <p className="mt-1 text-sm text-zinc-500">{desc}</p>}
      </div>
      {children && <div className="flex items-center gap-2">{children}</div>}
    </div>
  );
}

export function humanSize(n) {
  if (!Number.isFinite(n)) return '0 B';
  if (n < 1024) return `${n} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let i = -1;
  do {
    n /= 1024;
    i++;
  } while (n >= 1024 && i < units.length - 1);
  return `${n.toFixed(n >= 100 ? 0 : 1)} ${units[i]}`;
}
