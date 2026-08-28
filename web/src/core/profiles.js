import fsp from 'node:fs/promises';
import path from 'node:path';
import { readJson, writeJson, ensureDir, uid, slug, fileExists, httpErr } from './util.js';
import { resolvePaths, envPaths, ENV_DIRS } from './paths.js';
import { getSettings } from './settings.js';

export function assertSafeId(id) {
  if (!/^[a-zA-Z0-9][a-zA-Z0-9-]{0,63}$/.test(String(id || ''))) throw httpErr(400, 'invalid profile id');
}

export async function profilesDir() {
  const P = resolvePaths(await getSettings());
  return ensureDir(P.profilesDir);
}

export async function listProfiles() {
  const dir = await profilesDir();
  const items = await fsp.readdir(dir, { withFileTypes: true });
  const out = [];
  await Promise.all(
    items
      .filter((d) => d.isDirectory())
      .map(async (d) => {
        const root = path.join(dir, d.name);
        const env = await readJson(path.join(root, 'env.json'));
        if (!env) return;
        const config = await readJson(path.join(root, 'config.json'), {});
        out.push({ id: d.name, ...env, config, root });
      })
  );
  out.sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
  return out;
}

export async function getProfile(id) {
  assertSafeId(id);
  const dir = await profilesDir();
  const root = path.join(dir, id);
  const env = await readJson(path.join(root, 'env.json'));
  if (!env) throw httpErr(404, 'profile not found');
  const config = await readJson(path.join(root, 'config.json'), {});
  return { id, ...env, config, root, envPaths: envPaths(root) };
}

function defaultOptionsFile(engine) {
  if (engine === 'minecraft') {
    return [
      'difficulty:easy',
      'fpsLimit:260',
      'graphics:fast',
      'renderDistance:12',
      'soundCategory_master:100',
      'guiScale:0',
      'lang:en_us',
      'lastServer:'
    ].join('\n');
  }
  return JSON.stringify(
    { fpsLimit: 240, renderDistance: 12, difficulty: 'normal', gamemode: 'survival' },
    null,
    2
  );
}

export async function createProfile({ name, engine = 'vengine', version = '1.1.0', description = '' }) {
  const safe = String(name || '').trim();
  if (!safe || safe.length > 40) throw httpErr(400, 'invalid profile name');
  if (!['vengine', 'minecraft'].includes(engine)) throw httpErr(400, 'unknown engine');
  const dir = await profilesDir();
  const base = slug(safe);
  let id = base;
  let guard = 0;
  while (await fileExists(path.join(dir, id))) {
    if (guard++ > 50) throw httpErr(409, 'could not allocate profile id');
    id = `${base}-${uid().slice(0, 4)}`;
  }
  const s = await getSettings();
  const root = path.join(dir, id);
  const pp = envPaths(root);
  for (const d of ENV_DIRS(pp)) await ensureDir(d);
  const now = Date.now();
  const env = {
    id,
    name: safe,
    engine,
    version: String(version || (engine === 'vengine' ? '1.1.0' : 'latest')),
    description: String(description || '').slice(0, 200),
    isolated: true,
    createdAt: now,
    lastLaunch: null,
    launchCount: 0
  };
  const config = {
    memoryMax: s.defaultMemoryMax,
    memoryMin: s.defaultMemoryMin,
    javaPath: s.javaPath || '',
    extraJvmArgs: ''
  };
  await writeJson(pp.envJson, env);
  await writeJson(pp.configJson, config);
  await fsp.writeFile(pp.options, defaultOptionsFile(engine));
  return { id, ...env, config };
}

export async function updateProfile(id, patch = {}) {
  const p = await getProfile(id);
  const rawEnv = await readJson(p.envPaths.envJson, {});
  const envPatch = {};
  if (patch.name !== undefined) {
    const safe = String(patch.name).trim();
    if (!safe || safe.length > 40) throw httpErr(400, 'invalid profile name');
    envPatch.name = safe;
  }
  if (patch.version !== undefined) envPatch.version = String(patch.version);
  if (patch.description !== undefined) envPatch.description = String(patch.description).slice(0, 200);
  if (Object.keys(envPatch).length) await writeJson(p.envPaths.envJson, { ...rawEnv, ...envPatch });

  const cfgPatch = {};
  for (const k of ['memoryMax', 'memoryMin', 'javaPath', 'extraJvmArgs']) {
    if (patch[k] !== undefined) cfgPatch[k] = patch[k];
  }
  if (Object.keys(cfgPatch).length) await writeJson(p.envPaths.configJson, { ...p.config, ...cfgPatch });
  return getProfile(id);
}

export async function deleteProfile(id) {
  assertSafeId(id);
  const dir = await profilesDir();
  await fsp.rm(path.join(dir, id), { recursive: true, force: true });
}

export async function touchLaunch(id) {
  const p = await getProfile(id);
  const rawEnv = await readJson(p.envPaths.envJson, {});
  await writeJson(p.envPaths.envJson, {
    ...rawEnv,
    lastLaunch: Date.now(),
    launchCount: (rawEnv.launchCount || 0) + 1
  });
}
