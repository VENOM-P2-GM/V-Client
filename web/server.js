import express from 'express';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';
import api from './src/api/index.js';
import { setBroadcast, getJob, publicJob } from './src/core/pipeline.js';
import { ensureSeeded } from './src/core/seed.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3001);

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '2mb' }));
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  next();
});

app.use('/api', api);

const dist = path.join(__dirname, 'client', 'dist');
if (fs.existsSync(dist)) {
  app.use(express.static(dist));
  app.get(/^(?!\/api).*/, (req, res) => res.sendFile(path.join(dist, 'index.html')));
} else {
  app.get('/', (req, res) =>
    res.json({ ok: true, app: 'v-client', hint: 'client dist not built — run "npm run build" or use "npm run dev"' })
  );
}

// central error handler
app.use((err, req, res, next) => {
  const status = err.status || err.statusCode || 500;
  if (status >= 500) console.error('[api]', err);
  res.status(status).json({ error: err.message || 'internal error' });
});

const server = http.createServer(app);

/* ---------------- WebSocket: live job logs/progress ---------------- */
const wss = new WebSocketServer({ server, path: '/ws' });
const subs = new Map(); // jobId -> Set<ws>

wss.on('connection', (ws, req) => {
  ws.isAlive = true;
  ws.on('pong', () => (ws.isAlive = true));
  const url = new URL(req.url, 'http://localhost');
  const jobId = url.searchParams.get('job');
  if (jobId) {
    if (!subs.has(jobId)) subs.set(jobId, new Set());
    subs.get(jobId).add(ws);
    const j = getJob(jobId);
    if (j) ws.send(JSON.stringify({ type: 'hello', job: publicJob(j), tail: j.lines.slice(-300) }));
    else ws.send(JSON.stringify({ type: 'error', error: 'unknown job' }));
  }
  const cleanup = () => subs.get(jobId)?.delete(ws);
  ws.on('close', cleanup);
  ws.on('error', cleanup);
});

setBroadcast((jobId, msg) => {
  const set = subs.get(jobId);
  if (!set || set.size === 0) return;
  const data = JSON.stringify(msg);
  for (const ws of set) if (ws.readyState === 1) ws.send(data);
});

const ping = setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.isAlive) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30000);
wss.on('close', () => clearInterval(ping));

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[v-client] API + UI on http://0.0.0.0:${PORT}`);
  ensureSeeded();
});
