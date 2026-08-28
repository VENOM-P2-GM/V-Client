import path from 'node:path';
import { evalRules } from './mojang.js';

const SEP = process.platform === 'win32' ? ';' : ':';

function subst(s, map) {
  return String(s).replace(/\$\{(\w+)\}/g, (m, k) => (map[k] !== undefined ? String(map[k]) : ''));
}

export function buildSessionMap(session, version) {
  const props = [
    `uuid=${session.uuid}`,
    `username=${session.name}`,
    `type=${session.type === 'microsoft' ? 'msa' : 'offline'}`
  ];
  for (const [k, v] of Object.entries(session.properties || {})) props.push(`${k}=${v}`);
  return {
    auth_player_name: session.name,
    version_name: version?.id || 'v-client',
    game_directory: '',
    assets_root: '',
    assets_index_name: '',
    auth_uuid: session.uuid,
    auth_access_token: session.accessToken || '0',
    auth_session: session.accessToken || '0',
    user_type: session.type === 'microsoft' ? 'msa' : 'mojang',
    version_type: version?.type || 'release',
    user_properties: props.join(':'),
    clientid: '',
    xuid: '',
    authentication_server: 'https://authserver.mojang.com',
    sessionid: '',
    authorization_token: '',
    launcher_name: 'V Client',
    launcher_version: '1.0.0'
  };
}

/**
 * Build the full JVM argv from a Mojang version JSON.
 * Handles both modern (arguments.jvm/game) and legacy (minecraftArguments) formats.
 */
export function buildLaunchCommand({
  version,
  gameDir,
  assetsRoot,
  assetsIndexName,
  nativesDir,
  libraryDir,
  libraries,
  clientJar,
  memMax,
  memMin,
  session,
  extraJvmArgs = []
}) {
  const map = buildSessionMap(session, version);
  map.game_directory = gameDir;
  map.assets_root = assetsRoot;
  map.assets_index_name = assetsIndexName;
  map.loggerFile = path.join(gameDir, 'logs', 'latest.log');
  const classpath = [libraries, clientJar].filter(Boolean).join(SEP);
  map.classpath = classpath;
  map.natives_directory = nativesDir;
  map.library_directory = libraryDir;
  map.max_mem = `${memMax}M`;
  map.min_mem = `${memMin}M`;

  let jvmArgs = [];
  if (Array.isArray(version.arguments?.jvm)) {
    jvmArgs = version.arguments.jvm
      .filter((it) => (typeof it === 'string' ? true : evalRules(it.rules)))
      .map((it) => subst(typeof it === 'string' ? it : it.value, map))
      .filter((a) => a !== '');
  } else {
    // legacy (pre-1.13)
    jvmArgs = [`-Djava.library.path=${nativesDir}`, '-Dminecraft.launcher.brand=v-client', '-cp', classpath];
  }
  if (!jvmArgs.some((a) => String(a).startsWith('-Xmx'))) {
    jvmArgs = [`-Xmx${memMax}M`, `-Xms${memMin}M`, ...jvmArgs];
  }
  for (const a of extraJvmArgs) jvmArgs.push(a);

  const gameRaw = Array.isArray(version.arguments?.game)
    ? version.arguments.game
    : String(version.minecraftArguments || '')
        .split(' ')
        .filter(Boolean);
  const gameArgs = gameRaw
    .map((g) => subst(g, map))
    .filter((a) => a !== '');

  if (!version.mainClass) throw new Error('version JSON has no mainClass');
  return { javaArgs: [...jvmArgs, version.mainClass, ...gameArgs], map };
}
