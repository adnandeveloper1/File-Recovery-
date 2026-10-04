import http from 'node:http';
import { GoogleAuth } from 'google-auth-library';
import { verifyPurchase } from './verify.mjs';

const packageName = process.env.ANDROID_PACKAGE_NAME;
const productIds = new Set((process.env.PREMIUM_PRODUCT_IDS ?? '').split(',').map(s => s.trim()).filter(Boolean));
if (!/^[a-zA-Z]\w*(\.\w+)+$/.test(packageName ?? '') || productIds.size === 0) throw new Error('Set ANDROID_PACKAGE_NAME and PREMIUM_PRODUCT_IDS.');
const auth = new GoogleAuth({ scopes: ['https://www.googleapis.com/auth/androidpublisher'] });
const base = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' + encodeURIComponent(packageName);
const play = {
  async get(token) {
    const client = await auth.getClient();
    const { data } = await client.request({ url: base + '/purchases/subscriptionsv2/tokens/' + encodeURIComponent(token), timeout: 10000 });
    return data;
  },
  async acknowledge(token, product) {
    const client = await auth.getClient();
    await client.request({ method: 'POST', url: base + '/purchases/subscriptions/' + encodeURIComponent(product) + '/tokens/' + encodeURIComponent(token) + ':acknowledge', data: {}, timeout: 10000 });
  },
};
let inFlight = 0;
const server = http.createServer(async (req, res) => {
  const send = (status, value) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(JSON.stringify(value)); };
  if (req.url === '/health' && req.method === 'GET') return send(200, { status: 'ok' });
  if (req.url !== '/verify' || req.method !== 'POST') return send(404, { error: 'Not found' });
  if (!req.headers['content-type']?.startsWith('application/json')) return send(415, { error: 'JSON required' });
  if (inFlight >= 16) return send(429, { error: 'Try again later' });
  inFlight++;
  try {
    let size = 0;
    const chunks = [];
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 16384) { send(413, { error: 'Request too large' }); req.destroy(); return; }
      chunks.push(chunk);
    }
    let body;
    try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
    catch { return send(400, { error: 'Invalid JSON' }); }
    const result = await verifyPurchase({ token: body?.purchaseToken, play, productIds });
    send(200, result);
  } catch (error) {
    const upstream = error?.response?.status;
    if (upstream === 400 || upstream === 404 || upstream === 410) send(200, { verified: false, acknowledged: false, productId: '', expiresAtMillis: 0 });
    else send(error.status === 400 ? 400 : 503, { error: error.status === 400 ? 'Invalid request' : 'Verification temporarily unavailable' });
    // Never log purchase tokens, Google response payloads, or credential data.
  } finally { inFlight--; }
});
server.requestTimeout = 15000;
server.headersTimeout = 10000;
server.listen(Number(process.env.PORT ?? 8080), '0.0.0.0');
