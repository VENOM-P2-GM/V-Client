import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

export function httpErr(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}

export async function readJson(p, fallback = null) {
  try {
    return JSON.parse(await fsp.readFile(p, 'utf8'));
  } catch {
    return fallback;
  }
}

export async function writeJson(p, data) {
  await fsp.mkdir(path.dirname(p), { recursive: true });
  await fsp.writeFile(p, JSON.stringify(data, null, 2));
}

export async function ensureDir(p) {
  await fsp.mkdir(p, { recursive: true });
  return p;
}

export async function fileExists(p) {
  try {
    await fsp.access(p);
    return true;
  } catch {
    return false;
  }
}

export function uid(prefix = '') {
  return prefix + crypto.randomBytes(6).toString('hex');
}

export function slug(s) {
  return (
    String(s)
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40) || 'profile'
  );
}

export async function du(p) {
  let total = 0;
  const stack = [p];
  while (stack.length) {
    const cur = stack.pop();
    let st;
    try {
      st = await fsp.stat(cur);
    } catch {
      continue;
    }
    if (st.isDirectory()) {
      let entries = [];
      try {
        entries = await fsp.readdir(cur);
      } catch {
        continue;
      }
      for (const e of entries) stack.push(path.join(cur, e));
    } else if (st.isFile()) {
      total += st.size;
    }
  }
  return total;
}

export function humanSize(n) {
  if (!Number.isFinite(n)) return '0 B';
  if (n < 1024) return `${n} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let i = -1;
  do {
    n /= 1024;
    i++;
  } while (n >= 1024 && i < units.length - 1);
  return `${n.toFixed(n >= 100 ? 0 : 1)} ${units[i]}`;
}

/** Offline (multiMC-style) name-hash v3 UUID. */
export function namehashUuid(name) {
  const h = crypto.createHash('sha1').update(String(name)).digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const hex = h.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
