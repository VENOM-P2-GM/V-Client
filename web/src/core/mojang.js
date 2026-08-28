import { readJson, writeJson } from './util.js';
import { resolvePaths } from './paths.js';
import { getSettings } from './settings.js';

export const MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
export const ASSET_BASE = 'https://resources.download.minecraft.net/';

export const osName = { win32: 'windows', darwin: 'osx', linux: 'linux' }[process.platform] || 'linux';
export const osArch = { x64: 'x86_64', arm64: 'arm64', ia32: 'x86' }[process.arch] || 'x86_64';

/** Tiny offline fallback so the UI still lists a version without network. */
const SNAPSHOT = {
  latest: { release: '1.8.9', snapshot: '1.8.9' },
  versions: [
    {
      id: '1.8.9',
      type: 'release',
      url: 'https://piston-data.mojang.com/v1/objects/24013a8b3d930e916856684e05ace6d7738f576c/1.8.9.json',
      time: '2015-11-13T00:00:00+00:00',
      releaseTime: '2015-11-13T00:00:00+00:00'
    }
  ],
  _snapshot: true
};

let manifestMemory = null;

export async function getManifest({ signal, force = false } = {}) {
  if (manifestMemory && !force) return { manifest: manifestMemory, source: 'memory' };
  const P = resolvePaths(await getSettings());
  try {
    const res = await fetch(MANIFEST_URL, { signal, cache: 'no-store' });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const manifest = await res.json();
    if (!Array.isArray(manifest.versions) || !manifest.versions.length) throw new Error('bad manifest');
    manifestMemory = manifest;
    await writeJson(P.versionCacheFile, manifest).catch(() => {});
    return { manifest, source: 'online' };
  } catch {
    if (manifestMemory) return { manifest: manifestMemory, source: 'memory' };
    const cached = await readJson(P.versionCacheFile);
    if (cached?.versions?.length) return { manifest: cached, source: 'cache' };
    return { manifest: SNAPSHOT, source: 'snapshot' };
  }
}

export async function getVersionJson(versionId, { signal } = {}) {
  const { manifest } = await getManifest({ signal });
  const entry = manifest.versions.find((v) => v.id === versionId);
  if (!entry) throw new Error(`version "${versionId}" not found in manifest`);
  const res = await fetch(entry.url, { signal });
  if (!res.ok) throw new Error(`version json HTTP ${res.status}`);
  const version = await res.json();
  return { version, entry };
}

/** group:artifact:version[:classifier] → maven path */
export function mavenPath(name) {
  const [g, a, v, c] = String(name).split(':');
  if (!g || !a || !v) return null;
  const file = c ? `${a}-${v}-${c}.jar` : `${a}-${v}.jar`;
  return `${g.replace(/\./g, '/')}/${a}/${v}/${file}`;
}

export function evalRules(rules) {
  if (!Array.isArray(rules) || !rules.length) return true;
  let allow = false;
  let disallow = false;
  for (const r of rules) {
    const osOk =
      !r.os ||
      (r.os.name === osName && (!r.os.arch || r.os.arch === 'all' || r.os.arch === osArch));
    if (osOk) {
      if (r.action === 'allow') allow = true;
      if (r.action === 'disallow') disallow = true;
    }
  }
  return allow && !disallow;
}

export function isClientLib(lib) {
  if (lib.clientreq === false) return false;
  return evalRules(lib.rules);
}

export function libDownload(lib) {
  if (lib.downloads?.artifact?.url) {
    const a = lib.downloads.artifact;
    const rel = a.path || mavenPath(lib.name);
    if (!rel) return null;
    return { url: a.url, sha1: a.sha1 || null, size: a.size || 0, rel };
  }
  if (lib.url) {
    const rel = mavenPath(lib.name);
    if (!rel) return null;
    return { url: lib.url.replace(/\/$/, '') + '/' + rel, sha1: lib.checksums?.[0] || null, size: 0, rel };
  }
  return null;
}

export function libNatives(lib) {
  if (!lib.natives) return null;
  let classifier = null;
  if (typeof lib.natives === 'string') {
    classifier = lib.natives.replace('${arch}', osArch === 'x86_64' ? '64' : osArch);
  } else if (Array.isArray(lib.natives)) {
    classifier = `natives-${osName}`;
  } else if (typeof lib.natives === 'object') {
    classifier = lib.natives[osName] || null;
  }
  if (!classifier) return null;
  if (lib.downloads?.classifiers?.[classifier]?.url) {
    const c = lib.downloads.classifiers[classifier];
    const rel = c.path || mavenPath(`${lib.name}:${classifier}`);
    if (!rel) return null;
    return { url: c.url, sha1: c.sha1 || null, size: c.size || 0, rel };
  }
  if (lib.url) {
    const rel = mavenPath(`${lib.name}:${classifier}`);
    if (!rel) return null;
    return { url: lib.url.replace(/\/$/, '') + '/' + rel, sha1: null, size: 0, rel };
  }
  return null;
}

export function assetUrl(hash) {
  return `${ASSET_BASE}${String(hash).slice(0, 2)}/${String(hash)}`;
}
