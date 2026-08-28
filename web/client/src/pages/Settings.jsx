import React, { useEffect, useState } from 'react';
import { useI18n } from '../i18n.jsx';
import { api } from '../api.js';
import { useShell } from '../App.jsx';
import { Icon, Spinner, Field, PageHeader } from '../components.jsx';

export default function Settings() {
  const { t, lang, setLang } = useI18n();
  const { settings, setSettings, health } = useShell();
  const [form, setForm] = useState(null);
  const [saved, setSaved] = useState(false);
  const [javaList, setJavaList] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    if (settings && !form) setForm({ ...settings });
  }, [settings]);

  const save = async () => {
    setErr('');
    try {
      const s = await api.settings.put({
        defaultMemoryMax: form.defaultMemoryMax,
        defaultMemoryMin: form.defaultMemoryMin,
        javaPath: form.javaPath,
        assetMode: form.assetMode,
        language: form.language,
        theme: form.theme,
        msClientId: form.msClientId
      });
      setSettings(s);
      setLang(s.language);
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } catch (e) {
      setErr(e.message);
    }
  };

  const detect = async () => {
    setJavaList(null);
    try {
      setJavaList(await api.java());
    } catch (e) {
      setErr(e.message);
    }
  };

  if (!form)
    return (
      <div className="flex justify-center p-10">
        <Spinner />
      </div>
    );

  return (
    <div className="space-y-6">
      <PageHeader title={t('settings.title')} />
      {err && <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-300">{err}</div>}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="card space-y-4 p-5">
          <Field label={t('settings.language')}>
            <div className="grid grid-cols-2 gap-2">
              {[
                { id: 'ar', label: 'العربية' },
                { id: 'en', label: 'English' }
              ].map((l) => (
                <button
                  key={l.id}
                  onClick={() => setForm({ ...form, language: l.id })}
                  className={`rounded-xl border px-3 py-2.5 text-sm font-bold transition ${
                    form.language === l.id ? 'border-venom/60 bg-venom/10 text-venom' : 'border-white/10 bg-white/[0.03] text-zinc-400'
                  }`}
                >
                  {l.label}
                </button>
              ))}
            </div>
          </Field>
          <Field label={t('settings.theme')}>
            <div className="grid grid-cols-2 gap-2">
              {[
                { id: 'venom', label: t('settings.themeVenom') },
                { id: 'toxic', label: t('settings.themeToxic') }
              ].map((th) => (
                <button
                  key={th.id}
                  onClick={() => setForm({ ...form, theme: th.id })}
                  className={`rounded-xl border px-3 py-2.5 text-sm font-bold transition ${
                    form.theme === th.id ? 'border-venom/60 bg-venom/10 text-venom' : 'border-white/10 bg-white/[0.03] text-zinc-400'
                  }`}
                >
                  {th.label}
                </button>
              ))}
            </div>
          </Field>
          <Field label={t('settings.dataRoot')}>
            <div className="input flex items-center bg-black/40 font-mono text-xs" dir="ltr">
              {form.dataRoot}
            </div>
          </Field>
        </div>

        <div className="card space-y-4 p-5">
          <Field label={`${t('settings.memMax')} — ${form.defaultMemoryMax}`}>
            <input
              type="range"
              min={1024}
              max={16384}
              step={512}
              value={form.defaultMemoryMax}
              onChange={(e) => setForm({ ...form, defaultMemoryMax: +e.target.value })}
              className="w-full"
              style={{ accentColor: 'rgb(var(--c1))' }}
            />
          </Field>
          <Field label={`${t('settings.memMin')} — ${form.defaultMemoryMin}`}>
            <input
              type="range"
              min={512}
              max={8192}
              step={256}
              value={form.defaultMemoryMin}
              onChange={(e) => setForm({ ...form, defaultMemoryMin: +e.target.value })}
              className="w-full"
              style={{ accentColor: 'rgb(var(--c1))' }}
            />
          </Field>
          <Field label={t('settings.java')}>
            <div className="flex gap-2">
              <input
                className="input flex-1"
                dir="ltr"
                placeholder={t('settings.javaHint')}
                value={form.javaPath}
                onChange={(e) => setForm({ ...form, javaPath: e.target.value })}
              />
              <button className="btn btn-ghost shrink-0" onClick={detect}>
                {javaList === null && form.javaPath === '' ? <Icon name="shield" className="h-4 w-4" /> : t('settings.javaDetect')}
              </button>
            </div>
          </Field>
          {javaList !== null && (
            <div className="space-y-1.5">
              {javaList.list.length ? (
                javaList.list.slice(0, 5).map((j, i) => (
                  <button
                    key={i}
                    onClick={() => setForm({ ...form, javaPath: j.path })}
                    className="flex w-full items-center justify-between rounded-lg bg-black/40 px-3 py-2 text-start font-mono text-[11px] text-zinc-400 transition hover:bg-black/60"
                    dir="ltr"
                  >
                    <span className="truncate">{j.path}</span>
                    <span className="shrink-0 text-venom">JDK {j.major}</span>
                  </button>
                ))
              ) : (
                <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-300">{t('settings.javaNone')}</div>
              )}
            </div>
          )}
          {health?.java && javaList === null && (
            <div className="text-[11px] text-zinc-500">
              {t('status.java')}: <span className="font-mono" dir="ltr">{health.java.path}</span>
            </div>
          )}
        </div>

        <div className="card space-y-4 p-5">
          <Field label={t('settings.assetMode')}>
            <div className="grid grid-cols-2 gap-2">
              {[
                { id: 'shared', label: t('settings.assetShared'), desc: t('settings.assetSharedDesc') },
                { id: 'isolated', label: t('settings.assetIsolated'), desc: t('settings.assetIsolatedDesc') }
              ].map((m) => (
                <button
                  key={m.id}
                  onClick={() => setForm({ ...form, assetMode: m.id })}
                  className={`rounded-xl border p-3 text-start transition ${
                    form.assetMode === m.id ? 'border-venom/60 bg-venom/10' : 'border-white/10 bg-white/[0.03] hover:bg-white/[0.06]'
                  }`}
                >
                  <div className="text-sm font-extrabold">{m.label}</div>
                  <div className="mt-1 text-[11px] leading-relaxed text-zinc-500">{m.desc}</div>
                </button>
              ))}
            </div>
          </Field>
        </div>

        <div className="card space-y-4 p-5">
          <Field label={t('settings.msClient')}>
            <input
              className="input"
              dir="ltr"
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx"
              value={form.msClientId}
              onChange={(e) => setForm({ ...form, msClientId: e.target.value })}
            />
          </Field>
          <p className="text-[11px] leading-relaxed text-zinc-500">{t('settings.msClientDesc')}</p>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button className="btn btn-primary" onClick={save}>
          <Icon name="check" className="h-4 w-4" /> {t('settings.save')}
        </button>
        {saved && <span className="text-sm font-bold text-acid">{t('settings.saved')}</span>}
      </div>
    </div>
  );
}
