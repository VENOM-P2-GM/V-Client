import { Router } from 'express';
import { getSettings, saveSettings } from '../core/settings.js';
import { resolvePaths, defaultDataRoot } from '../core/paths.js';
import { findJava } from '../core/java.js';
import { getManifest, MANIFEST_URL } from '../core/mojang.js';

const h = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

let netCheck = { at: 0, ok: null };
async function mojangReachable() {
  if (Date.now() - netCheck.at < 5 * 60 * 1000 && netCheck.ok !== null) return netCheck.ok;
  let ok = false;
  try {
    const c = await fetch(MANIFEST_URL, { signal: AbortSignal.timeout(4000) });
    ok = c.ok;
  } catch {}
  netCheck = { at: Date.now(), ok };
  return ok;
}

const r = Router();

r.get('/health', h(async (req, res) => {
  const s = await getSettings();
  const java = await findJava(s.javaPath || '');
  res.json({
    ok: true,
    app: 'v-client',
    version: '1.0.0',
    platform: process.platform,
    node: process.versions.node,
    dataRoot: s.dataRoot || defaultDataRoot(),
    java: java.best || null,
    javaCount: java.list.length,
    mojang: await mojangReachable()
  });
}));

r.get('/java', h(async (req, res) => {
  const s = await getSettings();
  const java = await findJava(s.javaPath || '');
  res.json(java);
}));

r.get('/settings', h(async (req, res) => {
  const s = await getSettings();
  const P = resolvePaths(s);
  res.json({ ...s, dataRoot: s.dataRoot || P.root });
}));

r.put('/settings', h(async (req, res) => {
  const allowed = ['defaultMemoryMax', 'defaultMemoryMin', 'javaPath', 'assetMode', 'language', 'theme', 'msClientId'];
  const patch = {};
  for (const k of allowed) if (req.body[k] !== undefined) patch[k] = req.body[k];
  if (patch.defaultMemoryMax !== undefined) patch.defaultMemoryMax = Math.min(32768, Math.max(512, Number(patch.defaultMemoryMax) || 4096));
  if (patch.defaultMemoryMin !== undefined) patch.defaultMemoryMin = Math.min(32768, Math.max(256, Number(patch.defaultMemoryMin) || 1024));
  if (patch.assetMode && !['shared', 'isolated'].includes(patch.assetMode)) patch.assetMode = 'shared';
  if (patch.language && !['ar', 'en'].includes(patch.language)) patch.language = 'ar';
  if (patch.theme && !['venom', 'toxic'].includes(patch.theme)) patch.theme = 'venom';
  const s = await saveSettings(patch);
  res.json(s);
}));

r.get('/engines', (req, res) => {
  res.json([
    {
      id: 'vengine',
      name: 'V-Engine',
      kind: 'demo',
      versions: ['1.1.0', '1.0.0'],
      isolated: true,
      nameAr: 'محرك V الداخلي',
      nameEn: 'V-Engine (built-in)',
      descAr: 'محرك خفيف مدمج لتشغيل بيئة معزولة كاملة وتقييمها بدون إنترنت',
      descEn: 'Lightweight built-in engine that runs a full isolated environment, no internet needed'
    },
    {
      id: 'minecraft',
      name: 'Minecraft Java Edition',
      kind: 'minecraft',
      versions: null,
      isolated: true,
      nameAr: 'ماينكرافت — إصدارة جاڤا',
      nameEn: 'Minecraft Java Edition',
      descAr: 'يشغل اللعبة في بيئة معزولة كاملة (يحتاج جاڤا + إنترنت على جهازك)',
      descEn: 'Runs the game in a fully isolated environment (requires Java + internet on your machine)'
    }
  ]);
});

r.get('/minecraft/versions', h(async (req, res) => {
  const { manifest, source } = await getManifest();
  res.json({
    source,
    latest: manifest.latest || null,
    versions: manifest.versions.map((v) => ({ id: v.id, type: v.type, releaseTime: v.releaseTime || v.time }))
  });
}));

export default r;
