import React, { useEffect, useState } from 'react';
import { useI18n } from '../i18n.jsx';
import { api } from '../api.js';
import { useShell } from '../App.jsx';
import { Icon, Spinner, StatusDot, Modal, Field, EmptyState, PageHeader, humanSize } from '../components.jsx';

export default function Profiles() {
  const { t, lang } = useI18n();
  const { profiles, refresh } = useShell();
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState(null);
  const [detailData, setDetailData] = useState(null);
  const [del, setDel] = useState(null);
  const [form, setForm] = useState({ name: '', engine: 'vengine', version: '1.1.0', description: '' });
  const [mcv, setMcv] = useState(null);
  const [err, setErr] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (form.engine === 'minecraft' && !mcv) {
      api.minecraftVersions().then(setMcv).catch(() => setMcv({ versions: [] }));
    }
  }, [form.engine, mcv]);

  const create = async () => {
    setErr('');
    try {
      const version = form.engine === 'minecraft' ? form.version || 'latest' : form.version || '1.1.0';
      await api.profiles.create({ name: form.name, engine: form.engine, version, description: form.description });
      setOpen(false);
      setForm({ name: '', engine: 'vengine', version: '1.1.0', description: '' });
      refresh();
    } catch (e) {
      setErr(e.message);
    }
  };

  const openDetail = async (id) => {
    if (detail === id) {
      setDetail(null);
      setDetailData(null);
      return;
    }
    setDetail(id);
    try {
      setDetailData(await api.profiles.get(id));
    } catch {
      setDetailData(null);
    }
  };

  const confirmDel = async () => {
    setErr('');
    try {
      await api.profiles.del(del);
      setDel(null);
      refresh();
    } catch (e) {
      setErr(e.message);
    }
  };

  const copy = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1200);
    } catch {}
  };

  return (
    <div className="space-y-6">
      <PageHeader title={t('profiles.title')} desc={t('profiles.emptyDesc')}>
        <button className="btn btn-primary" onClick={() => setOpen(true)}>
          <Icon name="plus" className="h-4 w-4" /> {t('profiles.new')}
        </button>
      </PageHeader>

      {!profiles.length ? (
        <EmptyState icon="folder" title={t('profiles.empty')} desc={t('profiles.emptyDesc')} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {profiles.map((p) => (
            <div key={p.id} className={`card card-hover p-5 ${detail === p.id ? 'border-venom/50' : ''}`}>
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-venom/15 text-venom">
                    <Icon name={p.engine === 'minecraft' ? 'gamepad' : 'cpu'} className="h-6 w-6" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2 font-extrabold">
                      {p.name} {p.running && <StatusDot status={p.running.status} />}
                    </div>
                    <div className="mt-0.5 text-xs text-zinc-500">
                      {p.engine === 'minecraft' ? t('engine.mine') : t('engine.demo')} · {t('common.version')} {p.version}
                    </div>
                  </div>
                </div>
                <button className="btn btn-ghost !px-2.5 !py-2" onClick={() => openDetail(p.id)} title={t('profiles.envTitle')}>
                  <Icon name="folder" className="h-4 w-4" />
                </button>
              </div>
              {p.description && <p className="mt-3 text-sm text-zinc-400">{p.description}</p>}
              <div className="mt-4 flex flex-wrap gap-2 text-[11px]">
                <span className="chip">
                  {t('profiles.size')}: {humanSize(p.sizeBytes)}
                </span>
                <span className="chip">
                  {t('profiles.launches')}: {p.launchCount}
                </span>
                <span className="chip">
                  {t('profiles.last')}: {p.lastLaunch ? new Date(p.lastLaunch).toLocaleDateString(lang === 'ar' ? 'ar-EG' : 'en-GB') : t('home.never')}
                </span>
              </div>
              <div className="mt-4">
                <button className="btn btn-danger w-full !py-2 text-xs" onClick={() => setDel(p.id)}>
                  <Icon name="trash" className="h-4 w-4" /> {t('common.delete')}
                </button>
              </div>
              {detail === p.id && detailData && (
                <div className="mt-4 space-y-2 border-t border-white/10 pt-4 text-xs">
                  <div className="font-extrabold text-zinc-300">{t('profiles.envTitle')}</div>
                  <div className="flex items-center justify-between rounded-lg bg-black/40 px-3 py-2 font-mono" dir="ltr">
                    <span className="truncate text-zinc-400">{p.root}</span>
                    <button onClick={() => copy(p.root)} className="shrink-0 text-zinc-500 hover:text-venom">
                      {copied ? t('profiles.copied') : t('profiles.copy')}
                    </button>
                  </div>
                  {Object.entries(detailData.sizes || {})
                    .filter(([k]) => k !== 'total')
                    .map(([k, v]) => (
                      <div key={k} className="flex items-center justify-between rounded-lg bg-white/[0.03] px-3 py-1.5">
                        <span className="font-mono text-zinc-400">{k}/</span>
                        <span className="text-zinc-500">{humanSize(v)}</span>
                      </div>
                    ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <Modal open={open} onClose={() => setOpen(false)} title={t('profiles.new')} wide>
        <div className="space-y-4">
          <Field label={t('profiles.name')}>
            <input className="input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Default" maxLength={40} />
          </Field>
          <Field label={t('profiles.engine')}>
            <div className="grid grid-cols-2 gap-3">
              {[
                { id: 'vengine', icon: 'cpu', name: t('profiles.vengineName'), desc: t('profiles.vengineDesc') },
                { id: 'minecraft', icon: 'gamepad', name: t('profiles.minecraftName'), desc: t('profiles.minecraftDesc') }
              ].map((e) => (
                <button
                  key={e.id}
                  onClick={() => setForm({ ...form, engine: e.id, version: e.id === 'vengine' ? '1.1.0' : 'latest' })}
                  className={`rounded-xl border p-3 text-start transition ${
                    form.engine === e.id ? 'border-venom/60 bg-venom/10' : 'border-white/10 bg-white/[0.03] hover:bg-white/[0.06]'
                  }`}
                >
                  <div className="flex items-center gap-2 text-sm font-extrabold">
                    <Icon name={e.icon} className="h-4 w-4 text-venom" /> {e.name}
                  </div>
                  <div className="mt-1 text-[11px] leading-relaxed text-zinc-500">{e.desc}</div>
                </button>
              ))}
            </div>
          </Field>
          <Field label={t('profiles.version')}>
            {form.engine === 'minecraft' ? (
              <select className="input" value={form.version} onChange={(e) => setForm({ ...form, version: e.target.value })}>
                <option value="latest">
                  {t('home.latest')} ({mcv?.latest?.release || '…'})
                </option>
                {(mcv?.versions || []).slice(0, 150).map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.id} ({v.type})
                  </option>
                ))}
              </select>
            ) : (
              <select className="input" value={form.version} onChange={(e) => setForm({ ...form, version: e.target.value })}>
                {['1.1.0', '1.0.0'].map((v) => (
                  <option key={v} value={v}>
                    {v}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label={t('profiles.desc')}>
            <input className="input" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} maxLength={200} />
          </Field>
          {err && <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{err}</div>}
          <button className="btn btn-primary w-full" onClick={create}>
            {t('profiles.create')}
          </button>
        </div>
      </Modal>

      <Modal open={!!del} onClose={() => setDel(null)} title={t('common.delete')}>
        <p className="text-sm text-zinc-400">{t('profiles.deleteConfirm')}</p>
        {err && <div className="mt-3 rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{err}</div>}
        <div className="mt-4 flex gap-2">
          <button className="btn btn-danger flex-1" onClick={confirmDel}>
            {t('common.confirm')}
          </button>
          <button className="btn btn-ghost flex-1" onClick={() => setDel(null)}>
            {t('common.cancel')}
          </button>
        </div>
      </Modal>
    </div>
  );
}
