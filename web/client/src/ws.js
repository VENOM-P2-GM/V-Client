export function jobSocket(jobId, handlers) {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const ws = new WebSocket(`${proto}://${window.location.host}/ws?job=${encodeURIComponent(jobId)}`);
  ws.onmessage = (e) => {
    try {
      handlers.onMsg?.(JSON.parse(e.data));
    } catch {}
  };
  ws.onopen = () => handlers.onOpen?.();
  ws.onclose = () => handlers.onClose?.();
  ws.onerror = () => handlers.onError?.();
  return ws;
}
