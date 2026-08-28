import { spawn } from 'node:child_process';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { fileExists } from './util.js';

const IS_WIN = process.platform === 'win32';

export function parseJavaMajor(out) {
  const m = String(out).match(/version "([^"]+)"/);
  if (!m) return null;
  const v = m[1];
  if (v.startsWith('1.')) return parseInt(v.split('.')[1], 10) || null;
  return parseInt(v.split('.')[0], 10) || null;
}

function probe(bin) {
  return new Promise((resolve) => {
    try {
      const p = spawn(bin, ['-version'], { stdio: ['ignore', 'pipe', 'pipe'] });
      let out = '';
      const t = setTimeout(() => {
        try {
          p.kill();
        } catch {}
      }, 6000);
      p.stdout.on('data', (d) => (out += d));
      p.stderr.on('data', (d) => (out += d));
      p.on('error', () => {
        clearTimeout(t);
        resolve(null);
      });
      p.on('close', (code) => {
        clearTimeout(t);
        resolve({ code, out });
      });
    } catch {
      resolve(null);
    }
  });
}

/** Search common locations + PATH for Java runtimes. */
export async function findJava(hintPath = '') {
  const candidates = [];
  const push = (p) => {
    if (p && !candidates.includes(p)) candidates.push(p);
  };
  if (hintPath) push(String(hintPath).trim());
  if (process.env.JAVA_HOME) push(path.join(process.env.JAVA_HOME, 'bin', IS_WIN ? 'java.exe' : 'java'));
  push('java');

  if (!IS_WIN) {
    const dirs = ['/usr/lib/jvm', '/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home', '/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home'];
    for (const d of dirs) {
      let entries = [];
      try {
        entries = await fsp.readdir(d);
      } catch {
        continue;
      }
      for (const e of entries.sort().reverse()) push(path.join(d, e, 'bin', 'java'));
    }
  } else {
    for (const base of ['C:\\Program Files\\Java', 'C:\\Program Files\\Eclipse Adoptium', 'C:\\Program Files\\BellSoft']) {
      let entries = [];
      try {
        entries = await fsp.readdir(base);
      } catch {
        continue;
      }
      for (const e of entries.sort().reverse()) push(path.join(base, e, 'bin', 'java.exe'));
    }
  }

  const found = [];
  for (const c of candidates) {
    if (c !== 'java') {
      if (!(await fileExists(c))) continue;
    }
    const r = await probe(c);
    if (r && r.code === 0) {
      const major = parseJavaMajor(r.out);
      if (major) {
        found.push({
          path: c,
          major,
          line: String(r.out).split('\n').find((l) => l.includes('version')) || ''
        });
      }
    }
  }
  found.sort((a, b) => b.major - a.major);
  return { list: found, best: found[0] || null };
}
