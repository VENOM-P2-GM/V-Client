import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { ensureDir } from './util.js';

export async function sha1File(p) {
  const h = crypto.createHash('sha1');
  const s = fs.createReadStream(p);
  for await (const chunk of s) h.update(chunk);
  return h.digest('hex');
}

/** A file satisfies a download spec when it exists and matches known size/sha1. */
export async function isSatisfied(dest, info = {}, { verify = false } = {}) {
  let st;
  try {
    st = await fsp.stat(dest);
  } catch {
    return false;
  }
  if (!st.isFile()) return false;
  if (info.size && st.size !== info.size) return false;
  if (info.sha1 && verify) return (await sha1File(dest)) === info.sha1;
  return true;
}

function abortError() {
  const e = new Error('aborted');
  e.name = 'AbortError';
  return e;
}

/**
 * Stream a file with resume support, retries, sha1 verification and progress.
 * Keeps a `.part` temp file so interrupted downloads can resume.
 */
export async function download({ url, dest, size = 0, sha1 = null, onProgress, signal, retries = 2, label = '' }) {
  await ensureDir(path.dirname(dest));
  const tmp = dest + '.part';

  for (let attempt = 0; ; attempt++) {
    if (signal?.aborted) throw abortError();
    let resume = 0;
    try {
      const st = await fsp.stat(tmp);
      resume = st.size;
      if (size && resume >= size) resume = 0;
    } catch {
      resume = 0;
    }

    const headers = resume ? { Range: `bytes=${resume}-` } : {};
    try {
      const res = await fetch(url, { headers, signal, redirect: 'follow' });
      if (!res.ok && res.status !== 206) throw new Error(`HTTP ${res.status} ${label || url}`);
      if (res.status === 200 && resume > 0) resume = 0; // server ignored Range
      const total = size || Number(res.headers.get('content-length')) || 0;
      const ws = fs.createWriteStream(tmp, { flags: resume > 0 ? 'a' : 'w' });
      const reader = res.body.getReader();
      let got = resume;
      try {
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          const buf = Buffer.from(value);
          ws.write(buf);
          got += buf.length;
          if (total && onProgress) onProgress(Math.min(1, got / total), got, total);
        }
      } catch (e) {
        ws.destroy();
        throw e;
      }
      await new Promise((resolve, reject) => {
        ws.on('finish', resolve);
        ws.on('error', reject);
        ws.end();
      });

      if (sha1) {
        const actual = await sha1File(tmp);
        if (actual !== sha1) {
          await fsp.unlink(tmp).catch(() => {});
          throw new Error(`sha1 mismatch for ${label || dest}`);
        }
      }
      await fsp.rename(tmp, dest);
      return dest;
    } catch (e) {
      if (signal?.aborted) throw abortError();
      const retriable =
        e.code === 'ECONNRESET' ||
        e.code === 'ETIMEDOUT' ||
        e.code === 'EAI_AGAIN' ||
        e.code === 'ECONNREFUSED' ||
        e.code === 'ENOTFOUND' ||
        e.code === 'UND_ERR_CONNECT_TIMEOUT' ||
        e.name === 'TimeoutError' ||
        /HTTP 5\d\d/.test(e.message);
      if (attempt >= retries || !retriable) throw e;
      await new Promise((r) => setTimeout(r, 900 * (attempt + 1)));
    }
  }
}

/** Run fn over items with limited concurrency; fail fast on first error. */
export async function pool(items, limit, fn) {
  let i = 0;
  let firstErr = null;
  const workers = Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, async () => {
    while (i < items.length && !firstErr) {
      const idx = i++;
      try {
        await fn(items[idx], idx);
      } catch (e) {
        firstErr = e;
      }
    }
  });
  await Promise.all(workers);
  if (firstErr) throw firstErr;
}
