#!/usr/bin/env node
/*
 * V-Engine — the built-in demo game engine of V Client.
 * Runs strictly inside an isolated profile environment: it only reads/writes
 * the profile directory handed over via VCLIENT_PROFILE_DIR.
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = process.env.VCLIENT_PROFILE_DIR;
if (!ROOT) {
  console.error('[V-Engine] VCLIENT_PROFILE_DIR is not set — refusing to run outside an isolated environment');
  process.exit(2);
}

const readJ = (p, fb) => {
  try {
    return JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch {
    return fb;
  }
};

const env = readJ(path.join(ROOT, 'env.json'), {});
const session = readJ(path.join(ROOT, 'auth', 'session.json'), {
  name: 'unknown',
  uuid: '00000000-0000-0000-0000-000000000000'
});
const options = readJ(path.join(ROOT, 'game', 'options.json'), {});
const manifest = readJ(path.join(ROOT, 'game', 'engine-manifest.json'), {});

const W = 58;
console.log('');
console.log('┌' + '─'.repeat(W) + '┐');
console.log(`│  V-ENGINE  v${manifest.version || '1.0.0'}  ·  V Client isolated runtime`.padEnd(W) + '│');
console.log('└' + '─'.repeat(W) + '┘');
console.log(`[V-Engine] profile : ${env.name || 'default'}  (${env.id || ''})`);
console.log(`[V-Engine] env root: ${ROOT}`);
console.log(`[V-Engine] session : ${session.name} [${session.uuid}] (${session.type || 'offline'})`);
console.log(
  `[V-Engine] options : fps=${options.fpsLimit ?? 240} renderDistance=${options.renderDistance ?? 12} mode=${options.gamemode ?? 'survival'}`
);

let mods = [];
try {
  mods = fs
    .readdirSync(path.join(ROOT, 'mods'))
    .filter((f) => f.endsWith('.jar') && !f.endsWith('.disabled'))
    .sort();
} catch {}
if (mods.length) {
  console.log(`[MOD]     ${mods.length} mod(s) loaded from the isolated mods/ directory`);
  for (const m of mods) console.log(`[MOD]     loaded: ${m}`);
} else {
  console.log('[MOD]     no mods in this environment');
}

let seed = 1337;
const rnd = () => {
  seed = (seed * 1103515245 + 12345) & 0x7fffffff;
  return seed / 0x7fffffff;
};
const pick = (a) => a[Math.floor(rnd() * a.length)];

const state = { tick: 0, chunks: 0, mobs: 2, fps: options.fpsLimit ?? 240, weather: 'clear' };
const MOBS = ['creeper', 'zombie', 'skeleton', 'spider', 'enderman', 'slime'];
const SAVED = path.join(ROOT, 'saves', 'world.dat');

function save() {
  const data = {
    tick: state.tick,
    chunks: state.chunks,
    mobs: state.mobs,
    players: [{ name: session.name, uuid: session.uuid, pos: [8 + (state.tick % 64), 64, 8] }],
    savedAt: new Date().toISOString()
  };
  try {
    fs.mkdirSync(path.dirname(SAVED), { recursive: true });
    fs.writeFileSync(SAVED, JSON.stringify(data, null, 2));
    console.log(`[Save]    world saved → saves/world.dat (${state.tick} ticks)`);
  } catch (e) {
    console.log('[Save]    failed: ' + e.message);
  }
}

console.log('[World]   generating Overworld (seed 1337) ... done');
state.chunks = 48;
console.log('[World]   48 chunks loaded, spawn located');

const timer = setInterval(() => {
  state.tick++;
  state.fps = Math.max(60, Math.min(260, (options.fpsLimit ?? 240) + Math.floor(rnd() * 13) - 6));
  if (state.tick % 40 === 0) {
    const r = rnd();
    if (r < 0.35 && state.mobs < 12) {
      const m = pick(MOBS);
      state.mobs++;
      console.log(`[World]   ${m} spawned at (${Math.floor(rnd() * 128)}, 64, ${Math.floor(rnd() * 128)})`);
    } else if (r < 0.5 && state.mobs > 1) {
      state.mobs--;
      console.log('[World]   a mob despawned');
    } else if (r < 0.65) {
      state.chunks += 1;
      console.log(`[World]   chunk ${state.chunks} streamed in`);
    } else if (r < 0.7) {
      console.log(`[Player]  ${session.name} placed oak_log x1`);
    } else if (r < 0.75) {
      state.weather = state.weather === 'clear' ? 'rain' : 'clear';
      console.log(`[World]   weather → ${state.weather}`);
    }
  }
  if (state.tick % 100 === 0) {
    console.log(`[perf]    fps=${state.fps} tick=${state.tick} mobs=${state.mobs} chunks=${state.chunks} weather=${state.weather}`);
  }
  if (state.tick % 200 === 0) save();
}, 50);

let stopping = false;
function stop(sig) {
  if (stopping) return;
  stopping = true;
  clearInterval(timer);
  console.log('');
  console.log(`[V-Engine] ${sig} received — shutting down gracefully`);
  save();
  console.log('[V-Engine] session closed. the environment stays isolated and intact.');
  process.exit(0);
}
process.on('SIGTERM', () => stop('SIGTERM'));
process.on('SIGINT', () => stop('SIGINT'));
