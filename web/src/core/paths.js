import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const APP_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export function defaultDataRoot() {
  return path.join(os.homedir(), '.vclient');
}

export function resolvePaths(settings = {}) {
  const root = settings.dataRoot || defaultDataRoot();
  return {
    root,
    profilesDir: path.join(root, 'profiles'),
    sharedDir: path.join(root, 'shared'),
    assetsCache: path.join(root, 'shared', 'assets'),
    versionCacheFile: path.join(root, 'shared', 'version-cache.json'),
    accountsFile: path.join(root, 'accounts.json'),
    settingsFile: path.join(root, 'settings.json')
  };
}

export function profileRoot(profilesDir, id) {
  return path.join(profilesDir, id);
}

/** Every path inside an isolated profile environment. */
export function envPaths(root) {
  return {
    root,
    envJson: path.join(root, 'env.json'),
    configJson: path.join(root, 'config.json'),
    game: path.join(root, 'game'),
    versions: path.join(root, 'game', 'versions'),
    libraries: path.join(root, 'game', 'libraries'),
    assets: path.join(root, 'game', 'assets'),
    assetIndexes: path.join(root, 'game', 'assets', 'indexes'),
    assetsObjects: path.join(root, 'game', 'assets', 'objects'),
    saves: path.join(root, 'saves'),
    mods: path.join(root, 'mods'),
    resourcepacks: path.join(root, 'resourcepacks'),
    shaderpacks: path.join(root, 'shaderpacks'),
    config: path.join(root, 'config'),
    options: path.join(root, 'options.txt'),
    auth: path.join(root, 'auth'),
    session: path.join(root, 'auth', 'session.json'),
    logs: path.join(root, 'logs'),
    gameLog: path.join(root, 'logs', 'game.log'),
    reports: path.join(root, 'reports')
  };
}

export function ENV_DIRS(pp) {
  return [
    pp.game,
    pp.versions,
    pp.libraries,
    pp.assets,
    pp.assetIndexes,
    pp.assetsObjects,
    pp.saves,
    pp.mods,
    pp.resourcepacks,
    pp.shaderpacks,
    pp.config,
    pp.auth,
    pp.logs,
    pp.reports
  ];
}
