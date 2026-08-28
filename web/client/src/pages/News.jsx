import React, { useEffect, useState } from 'react';
import { useI18n } from '../i18n.jsx';
import { api } from '../api.js';
import { Icon, Spinner, PageHeader } from '../components.jsx';

const TREE = `~/.vclient/
└── profiles/
    └── <profile-id>/          ← isolated environment
        ├── env.json           profile identity
        ├── config.json        memory / java / extra JVM
        ├── game/              versions · libraries · assets
        ├── saves/             world saves (per profile!)
        ├── mods/              this profile's mods only
        ├── resourcepacks/  shaderpacks/  config/
        ├── auth/session.json  isolated auth session
        ├── logs/              launch + game logs
        └── reports/           last full launch command`;

export default function News() {
  const { t, lang } = useI18n();
  const [news, setNews] = useState(null);

  useEffect(() => {
    api.news().then(setNews).catch(() => setNews({ items: [] }));
  }, []);

  const hl = (item) => (lang === 'ar' ? item.highlightsAr : item.highlightsEn);

  return (
    <div className="space-y-6">
      <PageHeader title={t('news.title')} />
      {news === null ? (
        <div className="flex justify-center p-10">
          <Spinner />
        </div>
      ) : (
        <div className="space-y-4">
          {news.items.map((item) => (
            <div key={item.tag} className="card p-5">
              <div className="flex items-center gap-3">
                <span className="chip border-venom/30 bg-venom/10 font-mono text-venom">{item.tag}</span>
                <h3 className="font-extrabold">{lang === 'ar' ? item.titleAr : item.titleEn}</h3>
                {item.date !== '—' && <span className="ms-auto text-xs text-zinc-600">{item.date}</span>}
              </div>
              <ul className="mt-3 space-y-1.5">
                {hl(item).map((h, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm text-zinc-400">
                    <Icon name="check" className="mt-0.5 h-3.5 w-3.5 shrink-0 text-acid" />
                    {h}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      <div className="card space-y-4 p-6">
        <h3 className="text-lg font-extrabold">{t('news.isolationTitle')}</h3>
        <p className="text-sm text-zinc-400">{t('news.isolationDesc')}</p>
        <div className="grid gap-3 md:grid-cols-3">
          {[
            { icon: 'cpu', title: t('news.process'), desc: t('news.processDesc') },
            { icon: 'folder', title: t('news.data'), desc: t('news.dataDesc') },
            { icon: 'shield', title: t('news.session'), desc: t('news.sessionDesc') }
          ].map((c) => (
            <div key={c.title} className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
              <Icon name={c.icon} className="h-5 w-5 text-venom" />
              <div className="mt-2 text-sm font-extrabold">{c.title}</div>
              <div className="mt-1 text-xs leading-relaxed text-zinc-500">{c.desc}</div>
            </div>
          ))}
        </div>
        <div>
          <div className="label">{t('news.treeTitle')}</div>
          <pre className="overflow-x-auto rounded-xl border border-white/10 bg-black/60 p-4 font-mono text-[11.5px] leading-relaxed text-zinc-300" dir="ltr">
            {TREE}
          </pre>
        </div>
        <p className="text-[11px] leading-relaxed text-zinc-600">{t('news.legal')}</p>
      </div>
    </div>
  );
}
