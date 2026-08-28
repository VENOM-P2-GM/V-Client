import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { readJson, writeJson, ensureDir, fileExists, httpErr } from './util.js';
import { resolvePaths, envPaths, ENV_DIRS, APP_ROOT } from './paths.js';
import { getSettings } from './settings.js';
import { getProfile, touchLaunch } from './profiles.js';
import { listAccounts } from './accounts.js';
import { download, isSatisfied, pool } from './downloader.js';
import { unzip } from './zip.js';
import { getManifest, getVersionJson, libDownload, libNatives, isClientLib, assetUrl, osName } from './mojang.js';
import { findJava } from './java.js';
import { buildLaunchCommand } from './command.js';

const ACTIVE = ['queued', 'preparing', 'launching', 'running'];

const jobs = new Map();
let broadcast = null;
export function setBroadcast(fn) {
  broadcast = fn;
}

export function listJobs() {
  return [...jobs.values()].map(publicJob).sort((a, b) => (b.startedAt || 0) - (a.startedAt || 0));
}

export function getJob(id) {
  return jobs.get(id) || null;
}

export function publicJob(j) {
  return {
    id: j.id,
    profileId: j.profileId,
    accountId: j.accountId,
    engine: j.engine,
    version: j.version,
    status: j.status,
    stage: j.stage,
    stageMsg: j.stageMsg,
    progress: j.progress,
    pid: j.pid || null,
    startedAt: j.startedAt,
    finishedAt: j.finishedAt,
    error: j.error || null,
    report: j.report || null,
    logTail: j.lines.slice(-200)
  };
}

function emit(job, msg) {
  try {
    broadcast?.(job.id, msg);
  } catch {
    /* no subscribers */
  }
}

export function jobLog(job, line, stream = 'out') {
  const ts = new Date().toISOString();
  job.lines.push({ ts, line, stream });
  if (job.lines.length > 3000) job.lines.splice(0, job.lines.length - 3000);
  emit(job, { type: 'log', ts, line, stream });
}

function progress(job, stage, pct, msg = '') {
  job.stage = stage;
  job.progress = Math.max(0, Math.min(100, Math.round(pct)));
  job.stageMsg = msg;
  emit(job, { type: 'progress', stage, pct: job.progress, msg });
}

function setState(job, status) {
  job.status = status;
  if (status === 'running' && !job.startedAt) job.startedAt = Date.now();
  if (['stopped', 'error', 'cancelled', 'dryrun'].includes(status) && !job.finishedAt) job.finishedAt = Date.now();
  emit(job, { type: 'state', status });
}

function finish(job, { status = 'stopped', error = null, report = null } = {}) {
  if (error) job.error = error;
  if (report) job.report = report;
  setState(job, status);
  emit(job, { type: 'done', status: job.status, error: job.error, report: job.report });
}

export async function startJob({ profileId, accountId, engine, version, memoryMax, memoryMin }) {
  if (!profileId || !accountId) throw httpErr(400, 'profileId and accountId are required');
  const prof = await getProfile(profileId);
  const acc = (await listAccounts()).find((a) => a.id === accountId || a.uuid === accountId);
  if (!acc) throw httpErr(404, 'account not found');
  for (const j of jobs.values()) {
    if (j.profileId === profileId && ACTIVE.includes(j.status)) {
      throw httpErr(409, 'this profile already has an active session');
    }
  }
  const eng = engine || prof.engine;
  if (!['vengine', 'minecraft'].includes(eng)) throw httpErr(400, 'unknown engine');
  const job = {
    id: 'job-' + crypto.randomBytes(5).toString('hex'),
    profileId: prof.id,
    accountId: acc.id,
    engine: eng,
    version: String(version || prof.version || (eng === 'vengine' ? '1.1.0' : 'latest')),
    memoryMax,
    memoryMin,
    status: 'queued',
    stage: 'queued',
    stageMsg: '',
    progress: 0,
    lines: [],
    startedAt: Date.now(),
    finishedAt: null,
    error: null,
    report: null,
    pid: null,
    child: null,
    cancelled: false,
    signal: new AbortController()
  };
  jobs.set(job.id, job);
  runJob(job, { prof, acc }).catch((e) => {
    if (job.cancelled) finish(job, { status: 'cancelled' });
    else {
      job.error = e.message || String(e);
      jobLog(job, '[error] ' + job.error, 'err');
      finish(job, { status: 'error' });
    }
  });
  return job;
}

export function cancelJob(id) {
  const j = jobs.get(id);
  if (!j || !ACTIVE.includes(j.status)) return false;
  j.cancelled = true;
  try {
    j.signal.abort();
  } catch {}
  if (j.child) {
    try {
      j.child.kill('SIGTERM');
    } catch {}
  }
  return true;
}

export function killJob(id) {
  const j = jobs.get(id);
  if (!j || !j.child) return false;
  try {
    j.child.kill('SIGTERM');
  } catch {}
  setTimeout(() => {
    if (j.child) {
      try {
        j.child.kill('SIGKILL');
      } catch {}
    }
  }, 5000);
  return true;
}

async function runJob(job, { prof, acc }) {
  const settings = await getSettings();
  const P = resolvePaths(settings);
  const pp = prof.envPaths;
  try {
    progress(job, 'validate', 3, 'checking profile and account');
    for (const d of ENV_DIRS(pp)) await ensureDir(d);
    await touchLaunch(prof.id);
    if (job.engine === 'vengine') await runVEngine(job, { pp, prof, acc, settings });
    else await runMinecraft(job, { pp, prof, acc, settings, P });
  } catch (e) {
    if (job.cancelled || job.signal.aborted) {
      finish(job, { status: 'cancelled' });
      return;
    }
    job.error = e.message || String(e);
    jobLog(job, '[error] ' + job.error, 'err');
    finish(job, { status: 'error' });
  }
}

/* ---------------- built-in demo engine (works fully offline) ---------------- */

async function runVEngine(job, { pp, prof, acc, settings }) {
  jobLog(job, `[V Client] profile "${prof.name}" → isolated environment: ${pp.root}`);

  progress(job, 'environment', 10, 'preparing isolated environment');
  const binDir = path.join(pp.game, 'bin');
  await ensureDir(binDir);
  await fs.copyFile(path.join(APP_ROOT, 'engine', 'vengine.js'), path.join(binDir, 'vengine.js'));
  await writeJson(path.join(pp.game, 'engine-manifest.json'), {
    engine: 'vengine',
    version: job.version,
    source: 'v-client',
    installedAt: Date.now(),
    isolated: true
  });
  await fs.writeFile(
    path.join(pp.game, 'options.json'),
    JSON.stringify({ fpsLimit: 240, renderDistance: 12, difficulty: 'normal', gamemode: 'survival' }, null, 2)
  );
  progress(job, 'install', 25, 'engine installed into the environment');

  progress(job, 'session', 40, 'writing isolated session');
  await writeJson(pp.session, {
    name: acc.name,
    uuid: acc.uuid,
    type: acc.type,
    accessToken: acc.accessToken || '0',
    userType: acc.type === 'microsoft' ? 'msa' : 'mojang',
    properties: acc.properties || {},
    createdAt: Date.now()
  });

  progress(job, 'launch', 70, 'spawning isolated process');
  const cmd = [path.join(binDir, 'vengine.js')];
  const env = {
    ...process.env,
    VCLIENT_PROFILE_DIR: pp.root,
    VCLIENT_JOB_ID: job.id,
    VCLIENT_ENGINE_VERSION: job.version,
    VCLIENT_LAUNCHER: 'V Client 1.0.0'
  };
  jobLog(job, `[V Client] spawn: "${process.execPath} ${cmd[0]}" (cwd=${pp.game})`);
  const child = spawn(process.execPath, cmd, { cwd: pp.game, env, stdio: ['ignore', 'pipe', 'pipe'] });
  attachChild(job, child, pp);
  await fs.appendFile(pp.gameLog, `\n=== ${new Date().toISOString()} launch vengine ${job.version} (job ${job.id}) ===\n`);
  setState(job, 'running');
  progress(job, 'running', 100, 'running');
}

/* ---------------- Minecraft Java pipeline ---------------- */

function shellQuote(s) {
  return /[ \t"']/.test(s) ? `"${s.replace(/"/g, '\\"')}"` : s;
}

async function runMinecraft(job, { pp, prof, acc, settings, P }) {
  const sig = job.signal.signal;
  jobLog(job, `[V Client] profile "${prof.name}" → isolated environment: ${pp.root}`);
  progress(job, 'environment', 6, 'preparing isolated environment');

  progress(job, 'manifest', 10, 'fetching version manifest');
  const { manifest, source } = await getManifest({ signal: sig });
  jobLog(job, `[V Client] version manifest (${source}): ${manifest.versions.length} versions available`);

  let versionId = job.version;
  if (versionId === 'latest' || versionId === '') versionId = manifest.latest?.release || manifest.versions[0]?.id;
  job.version = versionId;

  progress(job, 'version', 14, `downloading ${versionId} version JSON`);
  const { version: vj } = await getVersionJson(versionId, { signal: sig });
  const verDir = path.join(pp.versions, versionId);
  await ensureDir(verDir);
  await writeJson(path.join(verDir, `${versionId}.json`), vj);
  jobLog(job, `[V Client] version JSON ready: ${versionId} (mainClass=${vj.mainClass})`);

  const libs = (vj.libraries || []).filter((l) => isClientLib(l));
  const libTasks = [];
  for (const l of libs) {
    const info = libDownload(l);
    if (info?.url) libTasks.push({ lib: l, info });
    const nat = libNatives(l);
    if (nat?.url) libTasks.push({ lib: l, info: nat, isNative: true });
  }
  progress(job, 'libraries', 18, `downloading ${libTasks.length} libraries`);
  let libDone = 0;
  await pool(
    libTasks,
    6,
    async ({ lib, info }) => {
      const dest = path.join(pp.libraries, info.rel);
      if (await isSatisfied(dest, info)) {
        libDone++;
        return;
      }
      try {
        await download({ url: info.url, dest, size: info.size, sha1: info.sha1, signal: sig, label: lib.name });
        jobLog(job, `[lib] ${lib.name}`);
      } finally {
        libDone++;
        progress(job, 'libraries', 18 + 27 * (libDone / Math.max(1, libTasks.length)), `${libDone}/${libTasks.length} libraries`);
      }
    }
  );

  progress(job, 'client', 46, 'downloading client jar');
  const clientJar = path.join(verDir, `${versionId}.jar`);
  const cd = vj.downloads?.client;
  if (!cd) throw new Error('this version has no client download');
  if (await isSatisfied(clientJar, cd, { verify: true })) {
    jobLog(job, '[client] jar already present (verified)');
  } else {
    await download({
      url: cd.url,
      dest: clientJar,
      size: cd.size,
      sha1: cd.sha1,
      signal: sig,
      label: 'client.jar',
      onProgress: (p) => progress(job, 'client', 46 + 14 * p, `client jar ${(p * 100).toFixed(0)}%`)
    });
  }

  progress(job, 'assets', 62, 'fetching asset index');
  const ai = vj.assetIndex;
  const aiFile = path.join(pp.assetIndexes, `${ai.id}.json`);
  let aiJson = await readJson(aiFile);
  if (!aiJson?.objects) {
    await download({ url: ai.url, dest: aiFile, size: ai.size, sha1: ai.sha1, signal: sig, label: 'asset index' });
    aiJson = JSON.parse(await fs.readFile(aiFile, 'utf8'));
  }
  const objects = Object.values(aiJson.objects);
  let objDone = 0;
  progress(job, 'assets', 64, `${objects.length} assets to check`);
  const sharedCache = path.join(P.assetsCache, 'objects');
  await pool(
    objects,
    10,
    async (meta) => {
      const hash = String(meta.hash || meta);
      const rel = `${hash.slice(0, 2)}/${hash}`;
      const dest = path.join(pp.assetsObjects, rel);
      try {
        if (await fileExists(dest)) return;
        const shared = path.join(sharedCache, rel);
        if (settings.assetMode !== 'isolated' && (await fileExists(shared))) {
          await ensureDir(path.dirname(dest));
          await fs.copyFile(shared, dest);
        } else {
          await download({ url: assetUrl(hash), dest: shared, size: meta.size || 0, signal: sig, label: 'asset' });
          await ensureDir(path.dirname(dest));
          await fs.copyFile(shared, dest);
        }
      } catch (e) {
        if (job.signal.aborted) throw e;
        jobLog(job, `[asset] skipped ${hash.slice(0, 8)}… (${e.message})`, 'err');
      } finally {
        objDone++;
        if (objDone % 25 === 0 || objDone === objects.length) {
          progress(job, 'assets', 64 + 26 * (objDone / Math.max(1, objects.length)), `${objDone}/${objects.length} assets`);
        }
      }
    }
  );

  progress(job, 'natives', 91, 'extracting natives');
  const nativesDir = path.join(verDir, `${versionId}-natives-${osName}`);
  let nativeCount = 0;
  for (const l of libs) {
    const nat = libNatives(l);
    if (!nat?.url) continue;
    const jar = path.join(pp.libraries, nat.rel);
    if (!(await fileExists(jar))) continue;
    await ensureDir(nativesDir);
    await unzip(jar, nativesDir, { exclude: l.extract?.exclude || ['META-INF/'] });
    nativeCount++;
  }
  jobLog(job, `[natives] extracted from ${nativeCount} archive(s) → ${nativesDir}`);

  progress(job, 'session', 95, 'writing isolated session');
  await writeJson(pp.session, {
    name: acc.name,
    uuid: acc.uuid,
    type: acc.type,
    accessToken: acc.accessToken || '0',
    userType: acc.type === 'microsoft' ? 'msa' : 'mojang',
    properties: acc.properties || {},
    createdAt: Date.now()
  });

  progress(job, 'java', 97, 'detecting Java runtime');
  const requiredMajor = vj.javaVersion?.majorVersion || null;
  const javaRes = await findJava(prof.config.javaPath || settings.javaPath || '');
  const java = (requiredMajor && javaRes.list.find((j) => j.major === requiredMajor)) || javaRes.best || null;
  if (java) jobLog(job, `[java] using ${java.path} (major ${java.major})`);
  else jobLog(job, '[java] no runtime found on this machine', 'err');
  if (java && requiredMajor && java.major < requiredMajor) {
    jobLog(job, `[java] WARNING: ${versionId} wants Java ${requiredMajor}, found ${java.major} — launch may fail`, 'err');
  }

  const libPaths = libs.map((l) => path.join(pp.libraries, libDownload(l)?.rel || '')).filter(Boolean);
  const memMax = job.memoryMax || prof.config.memoryMax || settings.defaultMemoryMax;
  const memMin = job.memoryMin || prof.config.memoryMin || settings.defaultMemoryMin;
  const { javaArgs } = buildLaunchCommand({
    version: vj,
    gameDir: pp.game,
    assetsRoot: pp.assets,
    assetsIndexName: ai.id,
    nativesDir,
    libraryDir: pp.libraries,
    libraries: libPaths,
    clientJar,
    memMax,
    memMin,
    session: {
      name: acc.name,
      uuid: acc.uuid,
      type: acc.type,
      accessToken: acc.accessToken || '0',
      properties: acc.properties || {}
    },
    extraJvmArgs: String(prof.config.extraJvmArgs || '').split(/\s+/).filter(Boolean)
  });
  const fullCmd = [java ? java.path : '<java>', ...javaArgs];
  await writeJson(path.join(pp.reports, 'last-launch.json'), {
    engine: 'minecraft',
    version: versionId,
    cmd: fullCmd,
    cwd: pp.game,
    java: java || null,
    createdAt: Date.now()
  });
  await fs.writeFile(path.join(pp.reports, 'last-launch.txt'), fullCmd.map(shellQuote).join(' ') + '\n');
  jobLog(job, '[V Client] full launch command saved → reports/last-launch.txt');

  if (!java) {
    finish(job, {
      status: 'dryrun',
      error: 'JAVA_NOT_FOUND',
      report: { dryRun: true, cmdFile: path.join(pp.reports, 'last-launch.txt') }
    });
    jobLog(job, '[V Client] Java runtime not found — install a JDK (or set its path in Settings). The launch command is ready in the profile reports.', 'err');
    return;
  }

  progress(job, 'launch', 100, 'launching game process');
  const child = spawn(java.path, javaArgs, { cwd: pp.game, stdio: ['ignore', 'pipe', 'pipe'], env: { ...process.env } });
  attachChild(job, child, pp);
  await fs.appendFile(pp.gameLog, `\n=== ${new Date().toISOString()} launch minecraft ${versionId} (job ${job.id}) ===\n$ ${fullCmd.map(shellQuote).join(' ')}\n`);
  setState(job, 'running');
  jobLog(job, `[V Client] spawned PID ${child.pid} (cwd=${pp.game})`);
}

function attachChild(job, child, pp) {
  job.child = child;
  job.pid = child.pid;
  for (const [streamName, kind] of [['stdout', 'out'], ['stderr', 'err']]) {
    let buf = '';
    child[streamName].on('data', (d) => {
      buf += d.toString();
      let i;
      while ((i = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, i).replace(/\r$/, '');
        buf = buf.slice(i + 1);
        if (!line.trim()) continue;
        jobLog(job, line, kind);
        fs.appendFile(pp.gameLog, line + '\n').catch(() => {});
      }
    });
  }
  child.on('error', (e) => {
    jobLog(job, '[spawn] ' + e.message, 'err');
    if (!job.child) finish(job, { status: 'error', error: e.message });
  });
  child.on('exit', (code, sig) => {
    job.child = null;
    jobLog(job, `[V Client] process exited (code=${code ?? 'n/a'}${sig ? ', signal=' + sig : ''})`);
    const report = { exitCode: code, signal: sig, durationMs: Date.now() - (job.startedAt || Date.now()) };
    if (job.cancelled) finish(job, { status: 'cancelled', report });
    else finish(job, { status: 'stopped', report, error: code ? `process exited with code ${code}` : null });
  });
  job.signal.signal.addEventListener('abort', () => {
    try {
      child.kill('SIGTERM');
    } catch {}
  });
}
