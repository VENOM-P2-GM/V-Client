import { Router } from 'express';
import { listAccounts, addOfflineAccount, removeAccount, startDeviceFlow, pollDeviceFlow } from '../core/accounts.js';
import { namehashUuid, httpErr } from '../core/util.js';

const h = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

const r = Router();

r.get('/', h(async (req, res) => {
  res.json(await listAccounts());
}));

r.get('/preview', h(async (req, res) => {
  const name = String(req.query.name || '').trim();
  if (!/^[A-Za-z0-9_]{3,24}$/.test(name)) throw httpErr(400, 'username must be 3-24 chars (A-Z, 0-9, _)');
  res.json({ name, uuid: namehashUuid(name) });
}));

r.post('/', h(async (req, res) => {
  const acc = await addOfflineAccount(req.body?.name);
  res.status(201).json(acc);
}));

r.delete('/:id', h(async (req, res) => {
  const acc = await removeAccount(req.params.id);
  res.json({ ok: true, removed: acc.name });
}));

r.post('/microsoft/device', h(async (req, res) => {
  res.status(201).json(await startDeviceFlow());
}));

r.get('/microsoft/poll/:flowId', h(async (req, res) => {
  res.json(await pollDeviceFlow(req.params.flowId));
}));

export default r;
