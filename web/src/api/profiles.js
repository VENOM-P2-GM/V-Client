import { Router } from 'express';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { listProfiles, getProfile, createProfile, updateProfile, deleteProfile } from '../core/profiles.js';
import { du } from '../core/util.js';
import { listJobs, publicJob } from '../core/pipeline.js';
import modsRouter from './mods.js';

const h = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const r = Router();

r.get('/', h(async (req, res) => {
  const profs = await listProfiles();
  const jobs = listJobs();
  for (const p of profs) {
    const running = jobs.find((j) => j.profileId === p.id && ['preparing', 'launching', 'running'].includes(j.status));
    p.running = running ? publicJob(running) : null;
    p.sizeBytes = await du(path.join(p.root));
  }
  res.json(profs);
}));

r.post('/', h(async (req, res) => {
  const p = await createProfile(req.body || {});
  res.status(201).json(p);
}));

r.get('/:id', h(async (req, res) => {
  const p = await getProfile(req.params.id);
  const pp = p.envPaths;
  const jobs = listJobs();
  const running = jobs.find((j) => j.profileId === p.id && ['preparing', 'launching', 'running'].includes(j.status));
  const folders = {};
  for (const [k, d] of [
    ['game', pp.game],
    ['saves', pp.saves],
    ['mods', pp.mods],
    ['config', pp.config],
    ['resourcepacks', pp.resourcepacks],
    ['shaderpacks', pp.shaderpacks],
    ['auth', pp.auth],
    ['logs', pp.logs],
    ['reports', pp.reports]
  ]) {
    folders[k] = await du(d).catch(() => 0);
  }
  res.json({
    ...p,
    sizes: { total: await du(p.root), ...folders },
    running: running ? publicJob(running) : null
  });
}));

r.patch('/:id', h(async (req, res) => {
  const p = await updateProfile(req.params.id, req.body || {});
  res.json(p);
}));

r.delete('/:id', h(async (req, res) => {
  const jobs = listJobs();
  const active = jobs.find((j) => j.profileId === req.params.id && ['preparing', 'launching', 'running'].includes(j.status));
  if (active) {
    res.status(409).json({ error: 'stop the running session before deleting this profile' });
    return;
  }
  await deleteProfile(req.params.id);
  res.json({ ok: true });
}));

r.use('/:id/mods', modsRouter);

export default r;
