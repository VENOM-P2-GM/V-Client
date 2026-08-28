import React, { useEffect, useRef, useState } from 'react';
import { useI18n } from '../i18n.jsx';
import { api } from '../api.js';
import { useShell } from '../App.jsx';
import { Icon, Spinner, Toggle, EmptyState, PageHeader, humanSize } from '../components.jsx';

export default function Mods() {
  const { t } = useI18n();
  const { profiles } = useShell();
  const [profileId, setProfileId] = useState(() => localStorage.getItem('vc-profile') || '');
  const [mods, setMods] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [err, setErr] = useState('');
  const fileRef = useRef(null);

  const profile = profiles.find((p) => p.id === profileId) || profiles[0];

  useEffect(() => {
    if (profile) {
      setProfileId(profile.id);
      localStorage.setItem('vc-profile', profile.id);
    }
  }, [profile?.id]);

  useEffect(() => {
    if (!profile) {
      setMods([]);
      return;
    }
    let on = true;
    api.mods
      .list(profile.id)
      .then((m) => on && setMods(m))
      .catch((e) => on && setErr(e.message));
    return () => {
      on = false;
    };
  }, [profile?.id]);

  const upload = async (files) => {
    if (!profile || !files?.length) return;
    setUploading(true);
    setErr('');
    try {
      for (const f of files) await api.mods.upload(profile.id, f);
      setMods(await api.mods.list(profile.id));
    } catch (e) {
      setErr(e.message);
    }
    setUploading(false);
    if (fileRef.current) fileRef.current.value = '';
  };

  const toggle = async (m) => {
    setErr('');
    try {
      await api.mods.toggle(profile.id, m.name, m.disabled);
      setMods(await api.mods.list(profile.id));
    } catch (e) {
      setErr(e.message);
    }
  };

  const del = async (m) => {
    setErr('');
    try {
      await api.mods.del(profile.id, m.name);
      setMods(await api.mods.list(profile.id));
    } catch (e) {
      setErr(e.message);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader title={t('mods.title')} desc={t('mods.note')}>
        {profile && (
          <>
            <select className="input !w-44" value={profile.id} onChange={(e) => setProfileId(e.target.value)}>
              {profiles.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
            <button className="btn btn-primary" disabled={!profile || uploading} onClick={() => fileRef.current?.click()}>
              {uploading ? <Spinner className="h-4 w-4" /> : <Icon name="upload" className="h-4 w-4" />}
              {uploading ? t('mods.uploading') : t('mods.upload')}
            </button>
            <input ref={fileRef} type="file" accept=".jar" multiple className="hidden" onChange={(e) => upload([...e.target.files])} />
          </>
        )}
      </PageHeader>

      {err && <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{err}</div>}

      {!profile ? (
        <EmptyState icon="puzzle" title={t('mods.empty')} desc={t('mods.emptyDesc')} />
      ) : mods === null ? (
        <div className="flex justify-center p-10">
          <Spinner />
        </div>
      ) : !mods.length ? (
        <EmptyState icon="puzzle" title={t('mods.empty')} desc={t('mods.emptyDesc')} />
      ) : (
        <div className="card divide-y divide-white/5 overflow-hidden">
          {mods.map((m) => (
            <div key={m.name} className="flex items-center gap-3 px-5 py-3.5">
              <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${m.disabled ? 'bg-white/5 text-zinc-600' : 'bg-venom/15 text-venom'}`}>
                <Icon name="puzzle" className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate font-mono text-sm font-bold" dir="ltr">
                  {m.name}
                </div>
                <div className="text-[11px] text-zinc-500">
                  {humanSize(m.sizeBytes)} · {m.disabled ? t('mods.disabled') : t('mods.enabled')}
                </div>
              </div>
              <Toggle checked={!m.disabled} onChange={() => toggle(m)} />
              <button className="text-zinc-600 transition hover:text-red-400" onClick={() => del(m)} title={t('mods.remove')}>
                <Icon name="trash" className="h-4 w-4" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
