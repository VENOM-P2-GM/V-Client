import fsp from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';

/**
 * Minimal dependency-free ZIP extractor (stored + deflated entries).
 * Used for Minecraft natives archives.
 */
export async function unzip(src, dest, { exclude = [] } = {}) {
  const buf = await fsp.readFile(src);

  let eocd = -1;
  const minStart = Math.max(0, buf.length - 22 - 65536);
  for (let i = buf.length - 22; i >= minStart; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error('not a zip file');

  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entries = [];
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(off) !== 0x02014b50) break;
    const method = buf.readUInt16LE(off + 10);
    const csize = buf.readUInt32LE(off + 20);
    const nameLen = buf.readUInt16LE(off + 28);
    const extraLen = buf.readUInt16LE(off + 30);
    const commentLen = buf.readUInt16LE(off + 32);
    const lOff = buf.readUInt32LE(off + 42);
    const name = buf.toString('utf8', off + 46, off + 46 + nameLen);
    entries.push({ name, method, csize, lOff });
    off += 46 + nameLen + extraLen + commentLen;
  }

  const destAbs = path.resolve(dest);
  for (const e of entries) {
    if (e.name.endsWith('/')) continue;
    if (exclude.some((x) => e.name === x || e.name.startsWith(x))) continue;
    const l = e.lOff;
    if (l + 30 > buf.length || buf.readUInt32LE(l) !== 0x04034b50) continue;
    const nl = buf.readUInt16LE(l + 26);
    const el = buf.readUInt16LE(l + 28);
    const start = l + 30 + nl + el;
    if (start + e.csize > buf.length) continue;

    let data;
    if (e.method === 0) data = buf.subarray(start, start + e.csize);
    else if (e.method === 8) data = zlib.inflateRawSync(buf.subarray(start, start + e.csize));
    else continue;

    const out = path.resolve(path.join(dest, e.name));
    if (!out.startsWith(destAbs)) continue; // zip-slip guard
    await fsp.mkdir(path.dirname(out), { recursive: true });
    await fsp.writeFile(out, data);
  }
}
