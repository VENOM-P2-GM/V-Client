import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useI18n } from './i18n.jsx';
import { api } from './api.js';
import { Icon, StatusDot } from './components.jsx';
import Home from './pages/Home.jsx';
import Profiles from './pages/Profiles.jsx';
import Mods from './pages/Mods.jsx';
import Accounts from './pages/Accounts.jsx';
import Settings from './pages/Settings.jsx';
import News from './pages/News.jsx';

const ShellCtx = createContext(null);
export const useShell = () => useContext(ShellCtx);

const NAV = [
  { id: 'home', icon: 'home' },
  { id: 'profiles', icon: 'folder' },
  { id: 'mods', icon: 'puzzle' },
  { id: 'accounts', icon: 'users' },
  { id: 'settings', icon: 'cog' },
  { id: 'news', icon: 'news' }
];

export default function App() {
  const { t, lang, setLang } = useI18n();
  const [tab, setTab] = useState('home');
  const [health, setHealth] = useState(null);
  const [settings, setSettings] = useState(null);
  const [profiles, setProfiles] = useState([]);
  const [jobs, setJobs] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    try {
      const [h, s, p, j, a] = await Promise.all([
        api.health(),
        api.settings.get(),
        api.profiles.list(),
        api.jobs.list(),
        api.accounts.list()
      ]);
      setHealth(h);
      setSettings(s);
      setProfiles(p);
      setJobs(j);
      setAccounts(a);
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    refresh();
    const iv = setInterval(refresh, 3000);
    return () => clearInterval(iv);
  }, [refresh]);

  useEffect(() => {
    if (!settings) return;
    document.documentElement.classList.toggle('theme-toxic', settings.theme === 'toxic');
    if (settings.language && settings.language !== lang) setLang(settings.language);
  }, [settings, lang, setLang]);

  const value = useMemo(
    () => ({ health, settings, profiles, jobs, accounts, refresh, setSettings, setProfiles, setAccounts, setJobs, goto: setTab }),
    [health, settings, profiles, jobs, accounts, refresh]
  );

  const runningCount = jobs.filter((j) => ['preparing', 'launching', 'running'].includes(j.status)).length;

  return (
    <ShellCtx.Provider value={value}>
      <div className="flex h-screen overflow-hidden">
        <aside className="flex w-60 shrink-0 flex-col border-e border-white/10 bg-ink-900/80 backdrop-blur">
          <div className="flex items-center gap-3 px-5 py-5">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-venom to-venom2 shadow-glow">
              <svg viewBox="0 0 32 32" className="h-7 w-7 text-white">
                <path d="M3 4h9l7 13 8-13h9L19 28h-6Z" fill="currentColor" />
              </svg>
            </div>
            <div>
              <div className="text-lg font-black leading-none tracking-tight">V Client</div>
              <div className="mt-1 text-[10px] font-bold uppercase tracking-widest text-venom">VENOM-P2-GM</div>
            </div>
          </div>
          <nav className="mt-2 flex-1 space-y-1 px-3">
            {NAV.map((n) => (
              <button
                key={n.id}
                onClick={() => setTab(n.id)}
                className={`flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-bold transition ${
                  tab === n.id ? 'bg-venom/15 text-venom shadow-glow' : 'text-zinc-400 hover:bg-white/5 hover:text-zinc-200'
                }`}
              >
                <Icon name={n.icon} className="h-5 w-5" />
                <span className="flex-1 text-start">{t('nav.' + n.id)}</span>
                {n.id === 'profiles' && runningCount > 0 && (
                  <span className="rounded-full bg-acid/20 px-2 py-0.5 text-[10px] font-black text-acid">{runningCount}</span>
                )}
              </button>
            ))}
          </nav>
          <div className="m-3 rounded-xl border border-white/10 bg-white/[0.03] p-3 text-[11px]">
            <div className="flex items-center gap-2">
              <span className={`h-2 w-2 animate-pulse rounded-full ${health?.mojang ? 'bg-acid' : 'bg-amber-400'}`} />
              <span className="font-bold text-zinc-300">
                {health ? (health.mojang ? t('status.online') : t('status.offline')) : t('common.loading')}
              </span>
            </div>
            {health && (
              <div className="mt-1.5 flex items-center gap-2 text-zinc-500">
                <span className={`h-1.5 w-1.5 rounded-full ${health.java ? 'bg-acid' : 'bg-zinc-600'}`} />
                {health.java ? `${t('status.java')} (JDK ${health.java.major})` : t('status.nojava')}
              </div>
            )}
            {error && <div className="mt-1.5 text-red-400">{error}</div>}
          </div>
        </aside>
        <main className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-6xl p-6 lg:p-8">
            {tab === 'home' && <Home />}
            {tab === 'profiles' && <Profiles />}
            {tab === 'mods' && <Mods />}
            {tab === 'accounts' && <Accounts />}
            {tab === 'settings' && <Settings />}
            {tab === 'news' && <News />}
          </div>
        </main>
      </div>
    </ShellCtx.Provider>
  );
}
