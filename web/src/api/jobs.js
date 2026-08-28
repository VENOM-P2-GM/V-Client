import { Router } from 'express';
import { startJob, listJobs, getJob, publicJob, cancelJob, killJob } from '../core/pipeline.js';
import { httpErr } from '../core/util.js';

const h = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const r = Router();

r.post('/', h(async (req, res) => {
  const b = req.body || {};
  if (!b.profileId || !b.accountId) throw httpErr(400, 'profileId and accountId are required');
  const job = await startJob({
    profileId: b.profileId,
    accountId: b.accountId,
    engine: b.engine,
    version: b.version,
    memoryMax: Number(b.memoryMax) || undefined,
    memoryMin: Number(b.memoryMin) || undefined
  });
  res.status(202).json(publicJob(job));
}));

r.get('/', h(async (req, res) => {
  res.json(listJobs());
}));

r.get('/:id', h(async (req, res) => {
  const j = getJob(req.params.id);
  if (!j) throw httpErr(404, 'job not found');
  res.json(publicJob(j));
}));

r.post('/:id/cancel', h(async (req, res) => {
  const ok = cancelJob(req.params.id);
  if (!ok) throw httpErr(409, 'job is not cancellable right now');
  res.json({ ok: true });
}));

r.post('/:id/kill', h(async (req, res) => {
  const ok = killJob(req.params.id);
  if (!ok) throw httpErr(409, 'job has no running process');
  res.json({ ok: true });
}));

export default r;
