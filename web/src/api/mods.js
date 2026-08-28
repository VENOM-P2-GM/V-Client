import { Router } from 'express';
import fsp from 'node:fs/promises';
import path from 'node:path';
import multer from 'multer';
import { getProfile } from '../core/profiles.js';
import { httpErr } from '../core/util.js';

const h = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const r = Router({ mergeParams: true });
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 512 * 1024 * 1024 } });

function safeName(name) {
  const base = path.basename(String(name || '')).replace(/[\\/:*?"<>|]/g, '_').trim();
  return (base || 'mod.jar').slice(0, 180);
}

async function modsDir(id) {
  const p = await getProfile(id);
  return p.envPaths.mods;
}

r.get('/', h(async (req, res) => {
  const dir = await modsDir(req.params.id);
  const files = await fsp.readdir(dir).catch(() => []);
  const mods = [];
  for (const f of files.sort()) {
    let st;
    try {
      st = await fsp.stat(path.join(dir, f));
    } catch {
      continue;
    }
    if (!st.isFile()) continue;
    const disabled = f.endsWith('.disabled');
    mods.push({ name: f.replace(/\.disabled$/, ''), disabled, sizeBytes: st.size });
  }
  res.json(mods);
}));

r.post('/upload', upload.single('file'), h(async (req, res) => {
  if (!req.file) throw httpErr(400, 'no file uploaded');
  const dir = await modsDir(req.params.id);
  let name = safeName(req.file.originalname);
  const target = path.join(dir, name + (req.body?.disabled === '1' ? '.disabled' : ''));
  let guard = 0;
  while (await (await fsp.stat(target).then(() => true).catch(() => false))) {
    guard++;
    const dot = name.lastIndexOf('.');
    name = dot > 0 ? `${name.slice(0, dot)}-${guard}${name.slice(dot)}` : `${name}-${guard}`;
    if (guard > 50) throw httpErr(409, 'too many conflicts');
  }
  await fsp.writeFile(target, req.file.buffer);
  res.status(201).json({ name: path.basename(target).replace(/\.disabled$/, ''), disabled: target.endsWith('.disabled'), sizeBytes: req.file.size });
}));

r.patch('/:name', h(async (req, res) => {
  const dir = await modsDir(req.params.id);
  const name = safeName(req.params.name).replace(/\.disabled$/, '');
  const enabled = Boolean(req.body?.enabled);
  const from = path.join(dir, enabled ? name + '.disabled' : name);
  const to = path.join(dir, enabled ? name : name + '.disabled');
  try {
    await fsp.access(from);
  } catch {
    throw httpErr(404, 'mod not found');
  }
  await fsp.rename(from, to);
  res.json({ name, enabled });
}));

r.delete('/:name', h(async (req, res) => {
  const dir = await modsDir(req.params.id);
  const name = safeName(req.params.name).replace(/\.disabled$/, '');
  for (const candidate of [name, name + '.disabled']) {
    try {
      await fsp.unlink(path.join(dir, candidate));
      return res.json({ ok: true });
    } catch {
      /* try next */
    }
  }
  throw httpErr(404, 'mod not found');
}));

export default r;
