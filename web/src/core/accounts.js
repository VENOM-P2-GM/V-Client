import { readJson, writeJson, uid, namehashUuid, httpErr } from './util.js';
import { resolvePaths } from './paths.js';
import { getSettings } from './settings.js';

async function accountsFile() {
  const P = resolvePaths(await getSettings());
  return P.accountsFile;
}

export async function listAccounts() {
  return (await readJson(await accountsFile(), [])) || [];
}

async function saveAccounts(list) {
  await writeJson(await accountsFile(), list);
}

export async function addOfflineAccount(name) {
  const safe = String(name || '').trim();
  if (!/^[A-Za-z0-9_]{3,24}$/.test(safe)) throw httpErr(400, 'username must be 3-24 chars (A-Z, 0-9, _)');
  const uuid = namehashUuid(safe);
  const list = await listAccounts();
  const existing = list.find((a) => a.uuid === uuid);
  if (existing) return existing;
  const acc = { id: uid('off-'), uuid, name: safe, type: 'offline', createdAt: Date.now() };
  list.push(acc);
  await saveAccounts(list);
  return acc;
}

export async function removeAccount(idOrUuid) {
  const list = await listAccounts();
  const i = list.findIndex((a) => a.id === idOrUuid || a.uuid === idOrUuid);
  if (i < 0) throw httpErr(404, 'account not found');
  const [acc] = list.splice(i, 1);
  await saveAccounts(list);
  return acc;
}

/* ---------------- Microsoft (device code flow → XBL → MCP) ---------------- */

const MS_TENANT = 'consumers';
const MS_SCOPE = 'XboxLive.signin offline_access';
const flows = new Map();

export async function startDeviceFlow() {
  const s = await getSettings();
  const clientId = String(s.msClientId || '').trim();
  if (!clientId) throw httpErr(400, 'Microsoft client id missing — set it in Settings first');
  const res = await fetch(`https://login.microsoftonline.com/${MS_TENANT}/oauth2/v2.0/devicecode`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: clientId, scope: MS_SCOPE })
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw httpErr(502, data.error_description || `device code request failed (${res.status})`);
  const flowId = uid('ms-');
  flows.set(flowId, {
    clientId,
    device_code: data.device_code,
    user_code: data.user_code,
    verification_uri: data.verification_uri,
    expires_in: data.expires_in || 900,
    interval: data.interval || 5,
    createdAt: Date.now()
  });
  return {
    flowId,
    user_code: data.user_code,
    verification_uri: data.verification_uri,
    expires_in: data.expires_in || 900,
    interval: data.interval || 5
  };
}

export async function pollDeviceFlow(flowId) {
  const f = flows.get(flowId);
  if (!f) throw httpErr(404, 'unknown flow');
  if (Date.now() - f.createdAt > f.expires_in * 1000) {
    flows.delete(flowId);
    throw httpErr(410, 'device flow expired');
  }
  const res = await fetch(`https://login.microsoftonline.com/${MS_TENANT}/oauth2/v2.0/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
      client_id: f.clientId,
      device_code: f.device_code
    })
  });
  const data = await res.json().catch(() => ({}));
  if (data.error === 'authorization_pending' || data.error === 'slow_down') return { status: 'pending' };
  if (!res.ok) {
    flows.delete(flowId);
    throw httpErr(502, data.error_description || `token exchange failed (${res.status})`);
  }
  flows.delete(flowId);
  return completeMicrosoft(f.clientId, data);
}

async function completeMicrosoft(clientId, msa) {
  const xblRes = await fetch('https://user.authentication.xboxlive.com/user/authenticate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      Properties: { AuthMethod: 'RPS', Signature: msa.access_token },
      RelyingParty: 'rp://api.minecraftservices.com/',
      TokenType: 'JWT'
    })
  });
  const xbl = await xblRes.json().catch(() => ({}));
  const xblToken = xbl.Token;
  const uhs = xbl.DisplayClaims?.xui?.[0]?.uhs;
  if (!xblToken || !uhs) throw httpErr(502, xbl.Message || 'Xbox Live authentication failed');

  const mcpRes = await fetch('https://api.minecraftservices.com/authentication/login_with_xbox', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identityToken: `XBL3.0 x=${uhs};${xblToken}` })
  });
  const mcp = await mcpRes.json().catch(() => ({}));
  if (!mcp.access_token) throw httpErr(502, mcp.errorMessage || mcp.error || 'Minecraft services login failed');

  let profile = { id: '', name: '' };
  try {
    const pr = await fetch('https://api.minecraftservices.com/minecraft/profile', {
      headers: { Authorization: `Bearer ${mcp.access_token}` }
    });
    if (pr.ok) profile = await pr.json();
  } catch {
    /* profile endpoint is optional */
  }

  const acc = {
    id: uid('ms-'),
    uuid: profile.id || namehashUuid(profile.name || 'minecraft-player'),
    name: profile.name || 'Minecraft Player',
    type: 'microsoft',
    accessToken: mcp.access_token,
    refreshToken: mcp.refresh_token || '',
    expiresAt: mcp.expires_in ? Date.now() + mcp.expires_in * 1000 - 60_000 : 0,
    createdAt: Date.now()
  };
  const list = await listAccounts();
  const i = list.findIndex((a) => a.uuid === acc.uuid);
  if (i >= 0) list[i] = { ...list[i], ...acc, id: list[i].id };
  else list.push(acc);
  await saveAccounts(list);
  return { status: 'done', account: acc };
}
