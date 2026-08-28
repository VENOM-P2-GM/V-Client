import { readJson, writeJson } from './util.js';
import { resolvePaths } from './paths.js';

export const SETTINGS_DEFAULTS = {
  dataRoot: '',
  defaultMemoryMax: 4096,
  defaultMemoryMin: 1024,
  javaPath: '',
  assetMode: 'shared',
  language: 'ar',
  theme: 'venom',
  msClientId: ''
};

let cache = null;

export async function getSettings() {
  if (cache) return cache;
  const P = resolvePaths({ dataRoot: '' });
  cache = { ...SETTINGS_DEFAULTS, ...(await readJson(P.settingsFile)) };
  return cache;
}

export async function saveSettings(patch) {
  const s = { ...(await getSettings()), ...patch };
  cache = s;
  const P = resolvePaths(s);
  await writeJson(P.settingsFile, s);
  return s;
}
