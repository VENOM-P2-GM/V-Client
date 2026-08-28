import React, { useEffect, useState } from 'react';
import { useI18n } from '../i18n.jsx';
import { api } from '../api.js';
import { useShell } from '../App.jsx';
import { Icon, Spinner, Avatar, EmptyState, PageHeader, Field } from '../components.jsx';

export default function Accounts() {
  const { t } = useI18n();
  const { accounts, refresh } = useShell();
  const [name, setName] = useState('');
  const [preview, setPreview] = useState(null);
  const [err, setErr] = useState('');
  const [selected, setSelected] = useState(() => localStorage.getItem('vc-account') || '');

  // microsoft device flow state
  const [ms, setMs] = useState(null); // {flowId, user_code, verification_uri, status, msg}

  useEffect(() => {
    if (!/^[A-Za-z0-9_]{3,24}$/.test(name)) {
      setPreview(null);
      return;
    }
    const to = setTimeout(() => api.accounts.preview(name).then(setPreview).catch(() => setPreview(null)), 350);
    return () => clearTimeout(to);
  }, [name]);

  const add = async () => {
    setErr('');
    try {
      await api.accounts.add(name);
      setName('');
      setPreview(null);
      refresh();
    } catch (e) {
      setErr(e.message);
    }
  };

  const del = async (id) => {
    setErr('');
    try {
      await api.accounts.del(id);
      refresh();
    } catch (e) {
      setErr(e.message);
    }
  };

  const select = (id) => {
    setSelected(id);
    localStorage.setItem('vc-account', id);
  };

  const startMs = async () => {
    setErr('');
    try {
      const f = await api.accounts.msDevice();
      setMs({ ...f, status: 'pending', msg: '' });
    } catch (e) {
      setErr(e.message);
      setMs(null);
    }
  };

  useEffect(() => {
    if (!ms || ms.status !== 'pending') return;
    const iv = setInterval(async () => {
      try {
        const r = await api.accounts.msPoll(ms.flowId);
        if (r.status === 'done') {
          setMs((p) => ({ ...p, status: 'done', msg: r.account?.name || '' }));
          refresh();
        }
      } catch (e) {
        setMs((p) => (p ? { ...p, status: 'error', msg: e.message } : p));
      }
    }, 5000);
    return () => clearInterval(iv);
  }, [ms?.flowId, ms?.status]);

  return (
    <div className="space-y-6">
      <PageHeader title={t('accounts.title')} />
      {err && <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{err}</div>}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="card space-y-4 p-5">
          <div className="flex items-center gap-2 font-extrabold">
            <Icon name="users" className="h-5 w-5 text-venom" /> {t('accounts.offline')}
          </div>
          <p className="text-xs text-zinc-500">{t('accounts.offlineDesc')}</p>
          <Field label={t('accounts.name')}>
            <input className="input" dir="ltr" value={name} onChange={(e) => setName(e.target.value)} placeholder="Steve" maxLength={24} />
          </Field>
          {preview && (
            <div className="rounded-lg bg-black/40 px-3 py-2 font-mono text-[11px] text-zinc-500" dir="ltr">
              {preview.uuid}
            </div>
          )}
          <button className="btn btn-primary w-full" disabled={!name} onClick={add}>
            <Icon name="plus" className="h-4 w-4" /> {t('accounts.add')}
          </button>
        </div>

        <div className="card space-y-4 p-5">
          <div className="flex items-center gap-2 font-extrabold">
            <Icon name="shield" className="h-5 w-5 text-acid" /> {t('accounts.microsoft')}
          </div>
          <p className="text-xs text-zinc-500">{t('accounts.msDesc')}</p>
          {ms ? (
            <div className="space-y-3">
              {ms.status === 'pending' && (
                <>
                  <div className="rounded-xl bg-black/40 p-4 text-center">
                    <div className="text-xs font-bold text-zinc-500">{t('accounts.msCode')}</div>
                    <div className="mt-1 font-mono text-3xl font-black tracking-[0.3em] text-venom" dir="ltr">
                      {ms.user_code}
                    </div>
                  </div>
                  <a
                    href={ms.verification_uri}
                    target="_blank"
                    rel="noreferrer"
                    className="btn btn-ghost w-full"
                    dir="ltr"
                  >
                    {ms.verification_uri}
                  </a>
                  <div className="flex items-center gap-2 text-xs text-zinc-500">
                    <Spinner className="h-3.5 w-3.5" /> {t('accounts.msPending')}
                  </div>
                </>
              )}
              {ms.status === 'done' && (
                <div className="rounded-xl border border-acid/30 bg-acid/10 p-3 text-sm font-bold text-acid">
                  {t('accounts.msDone')} — {ms.msg}
                </div>
              )}
              {ms.status === 'error' && (
                <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{ms.msg}</div>
              )}
            </div>
          ) : (
            <button className="btn btn-ghost w-full" onClick={startMs}>
              {t('accounts.msStart')}
            </button>
          )}
        </div>
      </div>

      <div>
        {!accounts.length ? (
          <EmptyState icon="users" title={t('accounts.empty')} desc={t('accounts.emptyDesc')} />
        ) : (
          <div className="card divide-y divide-white/5 overflow-hidden">
            {accounts.map((a) => (
              <div key={a.id} className="flex items-center gap-3 px-5 py-3.5">
                <Avatar name={a.name} uuid={a.uuid} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-extrabold">{a.name}</span>
                    <span className={`chip !py-0.5 text-[10px] ${a.type === 'microsoft' ? 'border-acid/30 text-acid' : 'border-white/10 text-zinc-400'}`}>
                      {a.type}
                    </span>
                    {selected === a.id && <span className="chip !py-0.5 text-[10px] border-venom/30 text-venom">{t('accounts.selected')}</span>}
                  </div>
                  <div className="mt-0.5 truncate font-mono text-[11px] text-zinc-500" dir="ltr">
                    {a.uuid}
                  </div>
                </div>
                <button className={`btn !px-3 !py-2 text-xs ${selected === a.id ? 'btn-primary' : 'btn-ghost'}`} onClick={() => select(a.id)}>
                  {t('home.launch')}
                </button>
                <button className="text-zinc-600 transition hover:text-red-400" onClick={() => del(a.id)} title={t('accounts.remove')}>
                  <Icon name="trash" className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
