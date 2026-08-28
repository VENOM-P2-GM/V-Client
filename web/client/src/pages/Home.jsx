import React, { useEffect, useMemo, useState } from 'react';
import { useI18n } from '../i18n.jsx';
import { api } from '../api.js';
import { useShell } from '../App.jsx';
import { Icon, Spinner, ProgressBar, LogTerminal, EmptyState, EngineBadge, StatusDot, Field } from '../components.jsx';
import { jobSocket } from '../ws.js';

const STAGE_ORDER = {
  vengine: ['validate', 'environment', 'install', 'session', 'launch', 'running'],
  minecraft: ['validate', 'environment', 'manifest', 'version', 'libraries', 'client', 'assets', 'natives', 'session', 'java', 'launch', 'running']
};

function StageList({ engine, stage, status }) {
  const { t } = useI18n();
  const order = STAGE_ORDER[engine] || STAGE_ORDER.vengine;
  const idx = order.indexOf(stage);
  const finished = ['stopped', 'cancelled', 'dryrun', 'error'].includes(status);
  return (
    <div className="grid grid-cols-2 gap-1.5">
      {order.map((s, i) => {
        let st;
        if (!finished) st = i < idx ? 'done' : i === idx ? 'now' : 'todo';
        else if (status === 'stopped' || status === 'dryrun') st = i <= idx ? 'done' : 'todo';
        else st = i < idx ? 'done' : i === idx ? 'fail' : 'todo';
        return (
          <div
            key={s}
            className={`flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-[11px] font-bold ${
              st === 'done'
                ? 'bg-acid/10 text-acid'
                : st === 'now'
                  ? 'bg-venom/15 text-venom'
                  : st === 'fail'
                    ? 'bg-red-500/10 text-red-400'
                    : 'text-zinc-600'
            }`}
          >
            {st === 'done' ? (
              <Icon name="check" className="h-3.5 w-3.5" />
            ) : st === 'now' ? (
              <Spinner className="h-3 w-3" />
            ) : (
              <span className="w-3.5 text-center">·</span>
            )}
            {t('stage.' + s)}
          </div>
        );
      })}
    </div>
  );
}

function EnvCard({ icon, title, value, mono = false }) {
  return (
    <div className="card p-4">
      <div className="flex items-center gap-2 text-xs font-bold text-zinc-400">
        <Icon name={icon} className="h-4 w-4 text-venom" /> {title}
      </div>
      <div className={`mt-2 break-all text-sm font-semibold ${mono ? 'font-mono text-[12px] text-zinc-300' : 'text-zinc-200'}`} dir="ltr">
        {value || '—'}
      </div>
    </div>
  );
}

export default function Home() {
  const { t, lang } = useI18n();
  const shell = useShell();
  const { profiles, accounts, jobs, refresh, goto } = shell;

  const [profileId, setProfileId] = useState(() => localStorage.getItem('vc-profile') || '');
  const [accountId, setAccountId] = useState(() => localStorage.getItem('vc-account') || '');
  const [version, setVersion] = useState('');
  const [memMax, setMemMax] = useState(4096);
  const [mcVersions, setMcVersions] = useState(null);
  const [activeJobId, setActiveJobId] = useState(null);
  const [job, setJob] = useState(null);
  const [lines, setLines] = useState([]);
  const [launchErr, setLaunchErr] = useState('');

  const profile = profiles.find((p) => p.id === profileId) || profiles[0] || null;
  const account = accounts.find((a) => a.id === accountId) || accounts[0] || null;
  const engine = profile?.engine || 'vengine';

  useEffect(() => {
    if (profile) {
      setProfileId(profile.id);
      localStorage.setItem('vc-profile', profile.id);
    }
  }, [profile?.id]);
  useEffect(() => {
    if (account) {
      setAccountId(account.id);
      localStorage.setItem('vc-account', account.id);
    }
  }, [accounts.length]);
  useEffect(() => {
    if (profile) {
      setMemMax(profile.config?.memoryMax || 4096);
    }
  }, [profile?.id]);

  const versionOptions = useMemo(() => {
    if (engine !== 'minecraft') return ['1.1.0', '1.0.0'];
    const list = (mcVersions?.versions || []).map((v) => v.id);
    return list.length ? list : [];
  }, [engine, mcVersions]);

  useEffect(() => {
    if (engine !== 'minecraft') {
      setMcVersions(null);
      return;
    }
    api.minecraftVersions().then(setMcVersions).catch(() => setMcVersions({ source: 'error', versions: [] }));
  }, [engine]);

  const effectiveVersion = versionOptions.includes(version) ? version : versionOptions[0] || (engine === 'minecraft' ? 'latest' : '1.1.0');

  const activeJob = jobs.find((j) => ['queued', 'preparing', 'launching', 'running'].includes(j.status)) || null;
  useEffect(() => {
    if (activeJob) setActiveJobId(activeJob.id);
  }, [activeJob?.id]);

  useEffect(() => {
    if (!activeJobId) {
      setLines([]);
      return;
    }
    let ws = null;
    const applyJob = (j) => setJob((prev) => ({ ...(prev || {}), ...j }));
    ws = jobSocket(activeJobId, {
      onMsg: (m) => {
        if (m.type === 'hello') {
          applyJob(m.job);
          setLines(m.tail || []);
        } else if (m.type === 'log') setLines((prev) => [...prev.slice(-400), m]);
        else if (m.type === 'progress') setJob((prev) => ({ ...(prev || {}), stage: m.stage, progress: m.pct, stageMsg: m.msg }));
        else if (m.type === 'state') setJob((prev) => ({ ...(prev || {}), status: m.status }));
        else if (m.type === 'done') {
          setJob((prev) => ({ ...(prev || {}), status: m.status, error: m.error || null, report: m.report || null }));
          refresh();
        }
      }
    });
    const tick = async () => {
      try {
        const j = await api.jobs.get(activeJobId);
        applyJob(j);
        setLines((prev) => {
          const tail = j.logTail || [];
          if (!tail.length) return prev;
          if (!prev.length || tail[tail.length - 1].ts !== prev[prev.length - 1].ts) return tail;
          return prev;
        });
      } catch {}
    };
    tick();
    const pollIv = setInterval(tick, 2500);
    return () => {
      try {
        ws?.close();
      } catch {}
      clearInterval(pollIv);
    };
  }, [activeJobId]);

  const launch = async () => {
    if (!profile || !account) return;
    setLaunchErr('');
    try {
      const j = await api.jobs.start({
        profileId: profile.id,
        accountId: account.id,
        engine,
        version: effectiveVersion,
        memoryMax: memMax
      });
      setJob(j);
      setLines([]);
      setActiveJobId(j.id);
      refresh();
    } catch (e) {
      setLaunchErr(e.message);
    }
  };

  const stop = async () => {
    if (!job) return;
    setLaunchErr('');
    try {
      if (job.status === 'running') await api.jobs.kill(job.id);
      else await api.jobs.cancel(job.id);
      refresh();
    } catch (e) {
      setLaunchErr(e.message);
    }
  };

  const liveJob = activeJobId && job && job.id === activeJobId ? job : null;
  const jobActive = liveJob && ['queued', 'preparing', 'launching', 'running'].includes(liveJob.status);

  return (
    <div className="space-y-6">
      <div className="relative overflow-hidden rounded-3xl border border-white/10">
        <img src="/hero.jpg" alt="" className="absolute inset-0 h-full w-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-ink-950 via-ink-950/85 to-ink-950/30" />
        <div className="relative z-10 grid gap-8 p-7 md:grid-cols-2 md:p-9">
          <div className="flex flex-col justify-center">
            <div className="chip w-fit border-venom/30 bg-venom/10 text-venom">
              <Icon name="shield" className="h-3.5 w-3.5" /> {t('home.isolated')}
            </div>
            <h1 className="mt-4 text-4xl font-black leading-tight">{profile ? profile.name : t('home.noProfileTitle')}</h1>
            <p className="mt-2 max-w-md text-sm text-zinc-400">{profile ? profile.description || t('home.envDesc') : t('home.noProfileDesc')}</p>
            {profile && (
              <div className="mt-5 flex flex-wrap items-center gap-3 text-xs text-zinc-400">
                <EngineBadge engine={engine} />
                <span className="chip">
                  {t('common.version')}: <b className="text-zinc-200">{effectiveVersion}</b>
                </span>
                <span className="chip">
                  {t('home.lastLaunch')}:{' '}
                  <b className="text-zinc-200">
                    {profile.lastLaunch ? new Date(profile.lastLaunch).toLocaleString(lang === 'ar' ? 'ar-EG' : 'en-GB') : t('home.never')}
                  </b>
                </span>
              </div>
            )}
          </div>

          <div>
            {liveJob ? (
              <div className="card space-y-4 p-5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-extrabold">
                    <StatusDot status={liveJob.status} />
                    {liveJob.status === 'running' ? t('home.runningTitle') : t('home.launching')}
                  </div>
                  <button className="btn btn-danger" onClick={stop}>
                    <Icon name="stop" className="h-4 w-4" />
                    {liveJob.status === 'running' ? t('home.stop') : t('home.cancel')}
                  </button>
                </div>
                <ProgressBar value={liveJob.progress || 0} />
                <div className="min-h-4 text-xs text-zinc-500">{liveJob.stageMsg || t('stage.' + (liveJob.stage || 'queued'))}</div>
                <StageList engine={liveJob.engine} stage={liveJob.stage} status={liveJob.status} />
                <LogTerminal lines={lines} />
                {liveJob.status === 'dryrun' && (
                  <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-3 text-sm font-bold text-amber-300">
                    {t('home.dryrunTitle')}
                    <div className="mt-1 text-xs font-normal">{t('home.dryrunDesc')}</div>
                  </div>
                )}
                {liveJob.status === 'error' && (
                  <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{liveJob.error || t('home.failed')}</div>
                )}
                {liveJob.status === 'stopped' && (
                  <div className="rounded-xl border border-white/10 bg-white/5 p-3 text-sm text-zinc-300">
                    {t('home.stopped')}
                    {liveJob.report?.durationMs ? ` · ${(liveJob.report.durationMs / 1000).toFixed(1)}s` : ''}
                  </div>
                )}
              </div>
            ) : (
              <div className="card space-y-4 p-5">
                {profile && (
                  <>
                    <Field label={t('home.profile')}>
                      <select className="input" value={profile.id} onChange={(e) => setProfileId(e.target.value)}>
                        {profiles.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <div className="grid grid-cols-2 gap-3">
                      <Field label={t('home.account')}>
                        <select className="input" value={account?.id || ''} onChange={(e) => setAccountId(e.target.value)}>
                          {accounts.map((a) => (
                            <option key={a.id} value={a.id}>
                              {a.name}
                            </option>
                          ))}
                        </select>
                      </Field>
                      <Field label={t('home.version')}>
                        <select className="input" value={effectiveVersion} onChange={(e) => setVersion(e.target.value)}>
                          {versionOptions.length ? (
                            versionOptions.map((v) => (
                              <option key={v} value={v}>
                                {v}
                              </option>
                            ))
                          ) : (
                            <option value="latest">{t('home.latest')}</option>
                          )}
                        </select>
                      </Field>
                    </div>
                    <Field label={`${t('home.memory')} — ${(memMax / 1024).toFixed(1)} GB`}>
                      <input
                        type="range"
                        min={1024}
                        max={16384}
                        step={512}
                        value={memMax}
                        onChange={(e) => setMemMax(+e.target.value)}
                        className="w-full"
                        style={{ accentColor: 'rgb(var(--c1))' }}
                      />
                    </Field>
                  </>
                )}
                {launchErr && <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{launchErr}</div>}
                <button className="btn btn-primary w-full py-3.5 text-base" disabled={!profile || !account || jobActive} onClick={launch}>
                  <Icon name="play" className="h-5 w-5" /> {t('home.launch')}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {!profile && (
        <EmptyState icon="folder" title={t('home.noProfileTitle')} desc={t('home.noProfileDesc')}>
          <button className="btn btn-primary" onClick={() => goto('profiles')}>
            <Icon name="plus" className="h-4 w-4" /> {t('home.createProfile')}
          </button>
        </EmptyState>
      )}

      {profile && (
        <div className="grid gap-4 md:grid-cols-3">
          <EnvCard
            icon="cpu"
            title={t('home.envProcess')}
            value={liveJob?.status === 'running' ? (liveJob.pid ? `PID ${liveJob.pid}` : t('common.loading')) : '—'}
          />
          <EnvCard icon="folder" title={t('home.envData')} value={profile.root} mono />
          <EnvCard icon="shield" title={t('home.envSession')} value={profile.envPaths?.session} mono />
        </div>
      )}
    </div>
  );
}
