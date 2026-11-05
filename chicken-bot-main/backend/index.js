if (process.env.NODE_ENV !== 'production') {
    require('dotenv').config();
}

const {
    Client, GatewayIntentBits, Events, REST, Routes, EmbedBuilder, MessageFlags, ChannelType, ActivityType
} = require('discord.js');
const express = require('express');
const path = require('path');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const { MongoClient } = require('mongodb');

const { signToken, requireAuth, loginLimiter } = require('./lib/auth');
const settingsLib = require('./lib/settings');
const createApiRouter = require('./api/routes');
const levels = require('./features/levels');
const live = require('./features/live');
const giveaways = require('./features/giveaways');
const { COLORS, FOOTER } = require('./lib/brand');
const features = [
    require('./features/moderation'),
    levels,
    require('./features/tickets'),
    require('./features/verify'),
    require('./features/ranks'),
    require('./features/spawners'),
    live,
    giveaways,
    require('./features/utility')
];

// ==========================================
// 🤖 DISCORD CLIENT
// ==========================================
const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages, // level system (XP per message)
        GatewayIntentBits.GuildMembers   // privileged: enable "Server Members Intent" in the Developer Portal
    ]
});

// ==========================================
// 🍃 MONGODB ATLAS (optional - needed for warnings, levels, settings, admin accounts)
// ==========================================
let usersCol = null;
let database = null;
const featureCtx = { getDb: () => database };

async function initMongo() {
    if (!process.env.MONGODB_URI) {
        console.log('[DB] MONGODB_URI not set - login uses ADMIN_USER/ADMIN_PASSWORD directly, warnings/levels/settings are disabled.');
        return;
    }
    try {
        const mongo = new MongoClient(process.env.MONGODB_URI, { serverSelectionTimeoutMS: 10000 });
        await mongo.connect();
        database = mongo.db('barbar_354');
        usersCol = database.collection('users');
        await usersCol.createIndex({ username: 1 }, { unique: true });
        console.log('[DB] MongoDB connected ✅');

        await settingsLib.init(database).catch((e) => console.error('[DB] Settings init failed:', e.message));
        for (const f of features) {
            try { if (f.init) await f.init(database); }
            catch (e) { console.error('[DB] Module init failed:', e.message); }
        }

        // Create/update the admin from the Render environment variables
        if (process.env.ADMIN_USER && process.env.ADMIN_PASSWORD) {
            const password_hash = await bcrypt.hash(process.env.ADMIN_PASSWORD, 10);
            await usersCol.updateOne(
                { username: process.env.ADMIN_USER },
                { $set: { username: process.env.ADMIN_USER, password_hash } },
                { upsert: true }
            );
            console.log(`[DB] Admin "${process.env.ADMIN_USER}" created/updated.`);
        }
    } catch (err) {
        usersCol = null;
        database = null;
        console.error('[DB] ❌ MongoDB connection failed:', err.message);
    }
}
initMongo();

// ==========================================
// 🌐 WEB SERVER
// ==========================================
const app = express();
app.set('trust proxy', 1); // Render sits behind a proxy (needed for the login rate limit)
app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'same-origin');
    next();
});
app.use(express.json({ limit: '100kb' }));
app.use(express.static(path.join(__dirname, '../dashboard')));

// Public status (used by the Render health check)
app.get('/api/status', (req, res) => {
    res.json({
        status: 'Backend online and ready! 🚀',
        bot: client.isReady() ? `online as ${client.user.tag}` : 'NOT connected (see Render logs)',
        env: {
            DISCORD_TOKEN: !!process.env.DISCORD_TOKEN,
            ADMIN_USER: !!process.env.ADMIN_USER,
            ADMIN_PASSWORD: !!process.env.ADMIN_PASSWORD,
            MONGODB_URI: !!process.env.MONGODB_URI,
            TWITCH: !!(process.env.TWITCH_CLIENT_ID && process.env.TWITCH_CLIENT_SECRET),
            YOUTUBE_API_KEY: !!process.env.YOUTUBE_API_KEY
        },
        mongodb: database ? 'connected' : 'not connected'
    });
});

// Login -> returns a signed token the dashboard sends with every request
app.post('/api/auth/login', loginLimiter, async (req, res) => {
    const { username, password } = req.body || {};
    if (!username || !password) {
        return res.status(400).json({ error: 'Please fill in all fields!' });
    }

    try {
        let admin = null;

        if (usersCol) {
            // Variant 1: MongoDB
            const user = await usersCol.findOne({ username: String(username) });
            if (user && await bcrypt.compare(String(password), user.password_hash)) admin = user.username;
        } else {
            // Variant 2: no database, compare with the Render variables
            if (!process.env.ADMIN_USER || !process.env.ADMIN_PASSWORD) {
                return res.status(500).json({ error: 'ADMIN_USER / ADMIN_PASSWORD are missing in Render (Environment)!' });
            }
            const safeEqual = (x, y) => {
                const hx = crypto.createHash('sha256').update(String(x)).digest();
                const hy = crypto.createHash('sha256').update(String(y)).digest();
                return crypto.timingSafeEqual(hx, hy);
            };
            if (safeEqual(username, process.env.ADMIN_USER) && safeEqual(password, process.env.ADMIN_PASSWORD)) {
                admin = String(username);
            }
        }

        if (!admin) return res.status(401).json({ error: 'Wrong username or password!' });
        return res.status(200).json({ message: 'Logged in! 🔓', admin, token: signToken(admin) });
    } catch (err) {
        console.error('[AUTH]', err);
        return res.status(500).json({ error: 'Login error: ' + err.message });
    }
});

// Minecraft console from the ChickenSMPCore plugin (own password, no dashboard login)
app.use('/api/minecraft/console', require('./features/console-bridge')(client));

// Everything else under /api needs the token
app.use('/api', createApiRouter({ client, getDb: () => database, requireAuth }));
app.use('/api', (req, res) => res.status(404).json({ error: 'Not found.' }));

// The dashboard (single page)
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, '../dashboard/index.html'));
});

// ==========================================
// ⏰ KEEP-ALIVE (stops Render's free plan from putting the bot to sleep)
// Render sets RENDER_EXTERNAL_URL automatically. The bot calls its own public
// address every 10 minutes, which counts as traffic and keeps the service awake.
// ==========================================
const keepAliveBase = process.env.KEEPALIVE_URL || process.env.RENDER_EXTERNAL_URL;
if (keepAliveBase) {
    const target = keepAliveBase.replace(/\/$/, '') + '/api/status';
    setInterval(() => {
        fetch(target, { signal: AbortSignal.timeout(15000) })
            .catch((e) => console.error('[KEEPALIVE] Ping failed:', e.message));
    }, 10 * 60 * 1000);
    console.log(`[KEEPALIVE] Pinging ${target} every 10 minutes.`);
}

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`[SYSTEM] Web server running on port ${PORT}`));

// ==========================================
// 🤖 SLASH COMMANDS
// ==========================================
const commandsData = [];
for (const f of features) {
    for (const cmd of f.commands) commandsData.push(cmd.toJSON());
}

client.once(Events.ClientReady, async () => {
    console.log(`[BOT] Online as ${client.user.tag}!`);
    live.start(client, () => database);
    giveaways.start(client, () => database);
    client.user.setPresence({
        status: 'online',
        activities: [{ name: 'DonutSMP | barbar_354', type: ActivityType.Watching }]
    });

    if (process.env.DISCORD_TOKEN) {
        const rest = new REST({ version: '10' }).setToken(process.env.DISCORD_TOKEN);
        try {
            await rest.put(Routes.applicationCommands(client.user.id), { body: commandsData });
            console.log(`[BOT] ${commandsData.length} barbar_354 commands registered!`);
        } catch (error) {
            console.error('[BOT] Failed to register commands:', error);
        }
    }
});

// Welcome message (can be switched off / redirected in the dashboard)
client.on(Events.GuildMemberAdd, async (member) => {
    try {
        const s = await settingsLib.getSettings(database, member.guild.id);
        if (s.welcomeEnabled === false) return;

        let channel = s.welcomeChannelId ? member.guild.channels.cache.get(s.welcomeChannelId) : null;
        if (!channel) {
            channel = member.guild.channels.cache.find(
                (ch) => ch.type === ChannelType.GuildText && (ch.name.includes('welcome') || ch.name.includes('willkommen'))
            );
        }
        if (!channel) return;

        const embed = new EmbedBuilder()
            .setTitle(`Welcome to ${member.guild.name}`)
            .setDescription(`Hello ${member}, welcome to **${member.guild.name}**!\nYou are member **#${member.guild.memberCount}**. Please have a look at the rules!`)
            .setColor(COLORS.primary)
            .setThumbnail(member.user.displayAvatarURL())
            .setFooter({ text: FOOTER })
            .setTimestamp();
        await channel.send({ embeds: [embed] });
    } catch (e) {
        console.error('[WELCOME]', e.message);
    }
});

// XP for the level system
client.on(Events.MessageCreate, (message) => {
    levels.onMessage(message, featureCtx).catch((e) => console.error('[LEVEL] Error:', e.message));
});

// Commands & buttons
client.on(Events.InteractionCreate, async (interaction) => {
    for (const f of features) {
        try {
            if (await f.handle(interaction, featureCtx)) return;
        } catch (e) {
            console.error('[BOT] Error in module:', e);
            const msg = { content: '❌ Something went wrong (am I missing permissions?).', flags: MessageFlags.Ephemeral };
            if (interaction.deferred || interaction.replied) interaction.followUp(msg).catch(() => {});
            else interaction.reply(msg).catch(() => {});
            return;
        }
    }
});

// ==========================================
// 🔌 BOT LOGIN
// ==========================================
process.on('unhandledRejection', (err) => console.error('[ERROR] Unhandled rejection:', err));
process.on('uncaughtException', (err) => console.error('[ERROR] Uncaught exception:', err));
client.on('error', (err) => console.error('[BOT] Client error:', err));

if (!process.env.DISCORD_TOKEN) {
    console.error('[BOT] ❌ DISCORD_TOKEN is missing! Add it in Render under Environment.');
} else {
    client.login(process.env.DISCORD_TOKEN)
        .then(() => console.log('[BOT] Login sent to Discord...'))
        .catch((err) => console.error('[BOT] ❌ Discord login failed:', err.message));
}
