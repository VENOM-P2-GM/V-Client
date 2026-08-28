async function req(path, opts = {}) {
  const isForm = opts.body instanceof FormData;
  const res = await fetch('/api' + path, {
    method: opts.method || 'GET',
    headers: isForm ? {} : { 'Content-Type': 'application/json' },
    body: isForm ? opts.body : opts.body !== undefined ? JSON.stringify(opts.body) : undefined
  });
  let data = {};
  try {
    data = await res.json();
  } catch {}
  if (!res.ok) throw new Error(data.error || `${res.status}`);
  return data;
}

export const api = {
  health: () => req('/system/health'),
  engines: () => req('/system/engines'),
  minecraftVersions: () => req('/system/minecraft/versions'),
  java: () => req('/system/java'),
  settings: {
    get: () => req('/system/settings'),
    put: (b) => req('/system/settings', { method: 'PUT', body: b })
  },
  profiles: {
    list: () => req('/profiles'),
    create: (b) => req('/profiles', { method: 'POST', body: b }),
    get: (id) => req(`/profiles/${id}`),
    update: (id, b) => req(`/profiles/${id}`, { method: 'PATCH', body: b }),
    del: (id) => req(`/profiles/${id}`, { method: 'DELETE' })
  },
  mods: {
    list: (id) => req(`/profiles/${id}/mods`),
    upload: (id, file) => {
      const fd = new FormData();
      fd.append('file', file);
      return req(`/profiles/${id}/mods/upload`, { method: 'POST', body: fd });
    },
    toggle: (id, name, enabled) => req(`/profiles/${id}/mods/${encodeURIComponent(name)}`, { method: 'PATCH', body: { enabled } }),
    del: (id, name) => req(`/profiles/${id}/mods/${encodeURIComponent(name)}`, { method: 'DELETE' })
  },
  accounts: {
    list: () => req('/accounts'),
    preview: (name) => req(`/accounts/preview?name=${encodeURIComponent(name)}`),
    add: (name) => req('/accounts', { method: 'POST', body: { name } }),
    del: (id) => req(`/accounts/${id}`, { method: 'DELETE' }),
    msDevice: () => req('/accounts/microsoft/device', { method: 'POST', body: {} }),
    msPoll: (flowId) => req(`/accounts/microsoft/poll/${flowId}`)
  },
  jobs: {
    list: () => req('/jobs'),
    start: (b) => req('/jobs', { method: 'POST', body: b }),
    get: (id) => req(`/jobs/${id}`),
    cancel: (id) => req(`/jobs/${id}/cancel`, { method: 'POST', body: {} }),
    kill: (id) => req(`/jobs/${id}/kill`, { method: 'POST', body: {} })
  },
  news: () => req('/news')
};
