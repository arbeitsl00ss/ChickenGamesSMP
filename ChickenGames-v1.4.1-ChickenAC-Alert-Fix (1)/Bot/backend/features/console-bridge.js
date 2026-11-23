// Receives the Minecraft console from the ChickenSMPCore plugin and posts it into a Discord channel.
// Render environment variables:
//   MC_CONSOLE_SECRET      long random password (same value as "secret" in the plugin's config.yml)
//   MC_CONSOLE_CHANNEL_ID  ID of the (staff-only!) text channel that should show the console
const crypto = require('crypto');
const express = require('express');

const MAX_BODY_TEXT = 8000;   // reject absurdly large requests
const PIECE = 1800;           // Discord limit is 2000 incl. the code block
const fails = new Map();      // ip -> { count, resetAt }  (wrong-password limiter)

function safeEqual(a, b) {
    const ha = crypto.createHash('sha256').update(String(a)).digest();
    const hb = crypto.createHash('sha256').update(String(b)).digest();
    return crypto.timingSafeEqual(ha, hb);
}

function split(text) {
    const pieces = [];
    let current = '';
    for (const line of text.split('\n')) {
        let l = line;
        while (l.length > PIECE) { // a single very long line
            if (current) { pieces.push(current); current = ''; }
            pieces.push(l.slice(0, PIECE));
            l = l.slice(PIECE);
        }
        if (current.length + l.length + 1 > PIECE) { pieces.push(current); current = ''; }
        current += (current ? '\n' : '') + l;
    }
    if (current.trim()) pieces.push(current);
    return pieces;
}

module.exports = function createConsoleBridge(client) {
    const router = express.Router();
    let chain = Promise.resolve(); // keeps the messages in order

    async function post(text) {
        const channel = await client.channels.fetch(process.env.MC_CONSOLE_CHANNEL_ID);
        if (!channel || !channel.isTextBased()) throw new Error('MC_CONSOLE_CHANNEL_ID is not a text channel');
        for (const piece of split(text)) {
            await channel.send({
                content: '```\n' + piece.replace(/```/g, "'''") + '\n```',
                allowedMentions: { parse: [] } // chat like "@everyone" in the console must never ping
            });
        }
    }

    router.post('/', (req, res) => {
        const secret = process.env.MC_CONSOLE_SECRET;
        if (!secret || !process.env.MC_CONSOLE_CHANNEL_ID) {
            return res.status(503).json({ error: 'Console bridge is not configured.' });
        }

        const now = Date.now();
        const ip = req.ip || 'unknown';
        let f = fails.get(ip);
        if (f && f.resetAt < now) f = null;
        if (f && f.count >= 20) return res.status(429).json({ error: 'Too many wrong attempts.' });

        const header = req.headers.authorization || '';
        const token = header.startsWith('Bearer ') ? header.slice(7) : '';
        if (!safeEqual(token, secret)) {
            if (!f) f = { count: 0, resetAt: now + 15 * 60 * 1000 };
            f.count++;
            fails.set(ip, f);
            return res.status(401).json({ error: 'Unauthorized.' });
        }

        const text = req.body && req.body.content;
        if (typeof text !== 'string' || !text.trim() || text.length > MAX_BODY_TEXT) {
            return res.status(400).json({ error: 'Invalid content.' });
        }
        if (!client.isReady()) return res.status(503).json({ error: 'Bot is not connected to Discord yet.' });

        const job = chain.then(() => post(text));
        chain = job.catch(() => {});
        job.then(() => res.json({ ok: true }))
           .catch((e) => {
               console.error('[MC-CONSOLE]', e.message);
               res.status(502).json({ error: 'Could not post to Discord.' });
           });
    });

    setInterval(() => {
        const now = Date.now();
        for (const [k, v] of fails) if (v.resetAt < now) fails.delete(k);
    }, 10 * 60 * 1000).unref();

    return router;
};
