// Dashboard authentication: signed tokens (survive Render restarts) + login rate limit.
const crypto = require('crypto');

const TOKEN_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours

function getSecret() {
    if (process.env.SESSION_SECRET) return process.env.SESSION_SECRET;
    if (process.env.DISCORD_TOKEN) {
        return crypto.createHash('sha256')
            .update(`barbar_354|${process.env.DISCORD_TOKEN}|${process.env.ADMIN_PASSWORD || ''}`)
            .digest('hex');
    }
    throw new Error('Set SESSION_SECRET (or DISCORD_TOKEN) so login tokens can be signed.');
}

function signToken(username) {
    const body = Buffer.from(JSON.stringify({ u: username, exp: Date.now() + TOKEN_TTL_MS })).toString('base64url');
    const sig = crypto.createHmac('sha256', getSecret()).update(body).digest('base64url');
    return `${body}.${sig}`;
}

function verifyToken(token) {
    if (typeof token !== 'string' || !token.includes('.')) return null;
    const [body, sig] = token.split('.');
    let expected;
    try {
        expected = crypto.createHmac('sha256', getSecret()).update(body).digest('base64url');
    } catch {
        return null;
    }
    const a = Buffer.from(sig || '');
    const b = Buffer.from(expected);
    if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
    try {
        const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
        if (!payload.u || !payload.exp || payload.exp < Date.now()) return null;
        return payload;
    } catch {
        return null;
    }
}

function requireAuth(req, res, next) {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : '';
    const payload = verifyToken(token);
    if (!payload) return res.status(401).json({ error: 'Not logged in or session expired. Please log in again.' });
    req.admin = payload.u;
    next();
}

// Max 10 login attempts per IP per 15 minutes.
const attempts = new Map();
function loginLimiter(req, res, next) {
    const now = Date.now();
    const key = req.ip || 'unknown';
    let entry = attempts.get(key);
    if (!entry || entry.resetAt < now) entry = { count: 0, resetAt: now + 15 * 60 * 1000 };
    entry.count++;
    attempts.set(key, entry);
    if (entry.count > 10) return res.status(429).json({ error: 'Too many login attempts. Try again in a few minutes.' });
    next();
}
setInterval(() => {
    const now = Date.now();
    for (const [k, v] of attempts) if (v.resetAt < now) attempts.delete(k);
}, 10 * 60 * 1000).unref();

module.exports = { signToken, verifyToken, requireAuth, loginLimiter };
