const http = require('http');
const net = require('net');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = 3000;
const ROOT = __dirname;
const HMAC_SECRET = 'NETCON-VIPER-XRAY-LICENSE-V1';
const ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

// In-memory 1-Key-to-1-Account claim registry
const claimedKeys = new Map();

function normalizeAccount(raw) {
  return String(raw || '').trim().toLowerCase();
}

function sanitizeBlock(raw, fallback = 'FULL') {
  const cleaned = String(raw || fallback)
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
  return (cleaned + 'XXXX').slice(0, 4);
}

function hmacToFourChars(payload) {
  const digest = crypto
    .createHmac('sha256', HMAC_SECRET)
    .update(payload, 'utf8')
    .digest();
  let out = '';
  for (let i = 0; i < 4; i++) {
    out += ALPHABET[digest[i] % ALPHABET.length];
  }
  return out;
}

function generateKeyForAccount(accountEmail, plan = 'FULL', year = '2026') {
  const acct = normalizeAccount(accountEmail || 'arxdevmm@gmail.com');
  const b1 = hmacToFourChars(`ACCT:${acct}`);
  const b2 = sanitizeBlock(plan, 'FULL');
  const b3 = sanitizeBlock(year, '2026');
  const sig = hmacToFourChars(`${acct}|${b1}-${b2}-${b3}`);
  return `NETCON-${b1}-${b2}-${b3}-${sig}`;
}

function verifyKeyForAccount(accountEmail, rawKey) {
  const acct = normalizeAccount(accountEmail);
  if (acct.length < 3 || acct.includes(' ')) return { valid: false, reason: 'Enter a valid account email.' };
  const normKey = String(rawKey || '').trim().toUpperCase().replace(/\s+/g, '-');
  const parts = normKey.split('-');
  if (parts.length !== 5 || parts[0] !== 'NETCON' || !parts.slice(1).every(p => /^[A-Z0-9]{4}$/.test(p))) {
    return { valid: false, reason: 'Invalid license key format. Expected NETCON-XXXX-XXXX-XXXX-XXXX.' };
  }
  const expectedAcctBlock = hmacToFourChars(`ACCT:${acct}`);
  if (parts[1] !== expectedAcctBlock) {
    return {
      valid: false,
      reason: `License key is issued for a different account (expected account tag ${expectedAcctBlock} for ${acct}). One key works for one account only.`,
    };
  }
  const expectedSig = hmacToFourChars(`${acct}|${parts[1]}-${parts[2]}-${parts[3]}`);
  if (parts[4] !== expectedSig) {
    return { valid: false, reason: `Invalid cryptographic signature for account ${acct}.` };
  }
  const owner = claimedKeys.get(normKey);
  if (owner && owner !== acct) {
    return {
      valid: false,
      reason: `License key is already bound to account ${owner}. One key works for one account only.`,
    };
  }
  claimedKeys.set(normKey, acct);
  return { valid: true, key: normKey, account: acct };
}

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.cjs': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.apk': 'application/vnd.android.package-archive',
};

function measureTcpLatency(host, port, timeoutMs = 2500) {
  return new Promise((resolve) => {
    const start = Date.now();
    const socket = new net.Socket();
    let settled = false;

    const finish = (ms) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(ms);
    };

    socket.setTimeout(timeoutMs);
    socket.once('connect', () => {
      finish(Math.max(1, Date.now() - start));
    });
    socket.once('timeout', () => finish(null));
    socket.once('error', () => finish(null));

    try {
      socket.connect(port, host);
    } catch {
      finish(null);
    }
  });
}

const server = http.createServer(async (req, res) => {
  try {
    const reqUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    const pathname = decodeURIComponent(reqUrl.pathname);

    if (pathname === '/api/ping') {
      const host = (reqUrl.searchParams.get('host') || '1.1.1.1').trim();
      const port = Math.min(65535, Math.max(1, Number(reqUrl.searchParams.get('port')) || 443));
      const ms = await measureTcpLatency(host, port, 2500);
      res.writeHead(200, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
      });
      res.end(JSON.stringify({ host, port, latencyMs: ms }));
      return;
    }

    if (pathname === '/api/license/issue') {
      const account = reqUrl.searchParams.get('account') || 'arxdevmm@gmail.com';
      const key = generateKeyForAccount(account, 'FULL', '2026');
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify({ account: normalizeAccount(account), key }));
      return;
    }

    if (pathname === '/api/license/activate') {
      const account = reqUrl.searchParams.get('account') || '';
      const key = reqUrl.searchParams.get('key') || '';
      const result = verifyKeyForAccount(account, key);
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify(result));
      return;
    }

    const candidates = [];
    if (pathname === '/' || pathname === '/index.html') {
      candidates.push(path.join(ROOT, 'index.html'));
    } else {
      candidates.push(path.join(ROOT, pathname));
      candidates.push(path.join(ROOT, 'public', pathname));
    }

    for (const candidate of candidates) {
      const resolved = path.resolve(candidate);
      if (!resolved.startsWith(ROOT + path.sep) && resolved !== path.join(ROOT, 'index.html')) {
        continue;
      }
      if (fs.existsSync(resolved) && fs.statSync(resolved).isFile()) {
        const ext = path.extname(resolved).toLowerCase();
        const contentType = MIME_TYPES[ext] || 'application/octet-stream';
        res.writeHead(200, {
          'Content-Type': contentType,
          'Cache-Control': ext === '.html' ? 'no-cache, no-store, must-revalidate' : 'public, max-age=3600',
        });
        fs.createReadStream(resolved).pipe(res);
        return;
      }
    }

    // SPA fallback to index.html
    const indexPath = path.join(ROOT, 'index.html');
    if (fs.existsSync(indexPath)) {
      res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': 'no-cache, no-store, must-revalidate',
      });
      fs.createReadStream(indexPath).pipe(res);
      return;
    }

    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Not Found');
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Internal Server Error');
  }
});

server.listen(PORT, '0.0.0.0', () => {
  try {
    fs.writeFileSync(path.join(ROOT, '.dev.pid'), String(process.pid), 'utf8');
  } catch {}
  console.log(`Netcon preview server listening on http://0.0.0.0:${PORT}`);
});
