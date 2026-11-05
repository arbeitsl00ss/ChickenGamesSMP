// ==========================================
// 🌐 DASHBOARD API  (everything here needs a valid login token)
// ==========================================
const express = require('express');
const { ChannelType, EmbedBuilder, PermissionFlagsBits } = require('discord.js');
const { ObjectId } = require('mongodb');
const { UserError } = require('../lib/errors');
const settingsLib = require('../lib/settings');
const moderation = require('../features/moderation');
const levels = require('../features/levels');
const tickets = require('../features/tickets');
const verify = require('../features/verify');
const spawners = require('../features/spawners');

const SNOWFLAKE = /^\d{17,20}$/;
const TEXT_TYPES = [ChannelType.GuildText, ChannelType.GuildAnnouncement];

module.exports = function createRouter({ client, getDb, requireAuth }) {
    const router = express.Router();
    router.use(requireAuth);

    const wrap = (fn) => async (req, res) => {
        try {
            await fn(req, res);
        } catch (e) {
            if (e instanceof UserError) return res.status(400).json({ error: e.message });
            console.error('[API]', e);
            res.status(500).json({ error: `Discord/server error: ${e.message}` });
        }
    };

    const needId = (value, label = 'User ID') => {
        const v = String(value || '').trim();
        if (!SNOWFLAKE.test(v)) throw new UserError(`${label} is not valid.`);
        return v;
    };
    const needDb = () => {
        const db = getDb();
        if (!db) throw new UserError('The database is not connected (check MONGODB_URI in Render).');
        return db;
    };
    const actorFor = (req) => ({ tag: `${req.admin} (Dashboard)` });
    const textChannel = (guild, id, label = 'Channel') => {
        const ch = guild.channels.cache.get(needId(id, label));
        if (!ch || !TEXT_TYPES.includes(ch.type)) throw new UserError(`${label} was not found or is not a text channel.`);
        const perms = ch.permissionsFor(guild.members.me);
        if (!perms || !perms.has([PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.EmbedLinks])) {
            throw new UserError(`I am not allowed to send messages in #${ch.name}.`);
        }
        return ch;
    };
    const userTag = async (id) => {
        const cached = client.users.cache.get(id);
        if (cached) return cached.tag;
        const u = await client.users.fetch(id).catch(() => null);
        return u ? u.tag : null;
    };

    router.param('guildId', (req, res, next, id) => {
        const guild = client.guilds.cache.get(id);
        if (!guild) return res.status(404).json({ error: 'The bot is not on this server.' });
        req.guild = guild;
        next();
    });

    // ---------- general ----------
    router.get('/me', (req, res) => res.json({ admin: req.admin }));

    router.get('/guilds', (req, res) => {
        res.json({
            ready: client.isReady(),
            guilds: [...client.guilds.cache.values()].map((g) => ({
                id: g.id, name: g.name, icon: g.iconURL({ size: 64 }), members: g.memberCount
            }))
        });
    });

    router.get('/guilds/:guildId/overview', wrap(async (req, res) => {
        const guild = req.guild;
        const db = getDb();
        const me = guild.members.me;
        const channels = [...guild.channels.cache.values()]
            .filter((c) => TEXT_TYPES.includes(c.type))
            .sort((a, b) => a.rawPosition - b.rawPosition)
            .map((c) => ({ id: c.id, name: c.name }));
        const roles = [...guild.roles.cache.values()]
            .filter((r) => r.id !== guild.id)
            .sort((a, b) => b.position - a.position)
            .map((r) => ({
                id: r.id, name: r.name, color: r.hexColor,
                assignable: !r.managed && r.position < me.roles.highest.position
            }));
        const s = await settingsLib.getSettings(db, guild.id);
        res.json({
            id: guild.id,
            name: guild.name,
            icon: guild.iconURL({ size: 128 }),
            memberCount: guild.memberCount,
            channels,
            roles,
            dbConnected: !!db,
            settings: {
                logChannelId: s.logChannelId || '',
                welcomeEnabled: s.welcomeEnabled !== false,
                welcomeChannelId: s.welcomeChannelId || ''
            }
        });
    }));

    router.get('/guilds/:guildId/members', wrap(async (req, res) => {
        const q = String(req.query.q || '').trim().slice(0, 50);
        if (!q) return res.json({ members: [] });
        const found = await req.guild.members.search({ query: q, limit: 10 });
        res.json({
            members: [...found.values()].map((m) => ({
                id: m.id,
                tag: m.user.tag,
                displayName: m.displayName,
                bot: m.user.bot,
                avatar: m.user.displayAvatarURL({ size: 32 })
            }))
        });
    }));

    // ---------- moderation ----------
    router.post('/guilds/:guildId/mod/:action', wrap(async (req, res) => {
        const { action } = req.params;
        if (!Object.hasOwn(moderation.actions, action)) throw new UserError('Unknown action.');
        const userId = needId(req.body.userId);
        const reason = String(req.body.reason || '').trim().slice(0, 400) || undefined;
        const embed = await moderation.actions[action](req.guild, getDb(), actorFor(req), {
            userId, reason, minutes: req.body.minutes
        });
        await settingsLib.sendLog(req.guild, getDb(), embed);
        res.json({ ok: true, message: embed.data.title });
    }));

    router.get('/guilds/:guildId/bans', wrap(async (req, res) => {
        const bans = await req.guild.bans.fetch({ limit: 100 });
        res.json({
            bans: [...bans.values()].map((b) => ({
                userId: b.user.id, tag: b.user.tag, reason: b.reason || ''
            }))
        });
    }));

    router.get('/guilds/:guildId/warnings', wrap(async (req, res) => {
        const db = needDb();
        const filter = { guildId: req.guild.id };
        if (req.query.userId) filter.userId = needId(req.query.userId);
        const rows = await db.collection('warns').find(filter).sort({ createdAt: -1 }).limit(req.query.userId ? 50 : 25).toArray();
        const ids = [...new Set(rows.map((r) => r.userId))];
        const names = {};
        await Promise.all(ids.map(async (id) => { names[id] = await userTag(id); }));
        res.json({
            warnings: rows.map((r) => ({
                id: String(r._id),
                userId: r.userId,
                userTag: names[r.userId] || null,
                moderator: r.modTag || (r.modId ? `<@${r.modId}>` : 'unknown'),
                reason: r.reason,
                createdAt: r.createdAt
            }))
        });
    }));

    router.delete('/guilds/:guildId/warnings/:warnId', wrap(async (req, res) => {
        const db = needDb();
        if (!ObjectId.isValid(req.params.warnId)) throw new UserError('Invalid warning ID.');
        const r = await db.collection('warns').deleteOne({ _id: new ObjectId(req.params.warnId), guildId: req.guild.id });
        if (!r.deletedCount) throw new UserError('Warning not found.');
        res.json({ ok: true });
    }));

    // ---------- levels ----------
    router.get('/guilds/:guildId/levels', wrap(async (req, res) => {
        const db = needDb();
        const top = await levels.leaderboard(db, req.guild.id, 10);
        const board = await Promise.all(top.map(async (d) => ({ ...d, name: await userTag(d.userId) })));
        const rows = await levels.listLevelRoles(db, req.guild.id);
        res.json({
            leaderboard: board,
            roles: rows.map((r) => ({
                level: r.level,
                roleId: r.roleId,
                roleName: req.guild.roles.cache.get(r.roleId)?.name || '(deleted role)'
            }))
        });
    }));

    router.post('/guilds/:guildId/levelroles', wrap(async (req, res) => {
        const db = needDb();
        const level = Number(req.body.level);
        const role = await levels.addLevelRole(req.guild, db, level, needId(req.body.roleId, 'Role'));
        res.json({ ok: true, message: `Level ${level} now gives the role "${role.name}".` });
    }));

    router.delete('/guilds/:guildId/levelroles/:level', wrap(async (req, res) => {
        const db = needDb();
        await levels.removeLevelRole(db, req.guild.id, Number(req.params.level));
        res.json({ ok: true });
    }));

    // ---------- settings ----------
    router.post('/guilds/:guildId/settings', wrap(async (req, res) => {
        const db = needDb();
        const set = {};
        const unset = [];
        const channelSetting = (key, value, label) => {
            if (!value) { unset.push(key); return; }
            set[key] = textChannel(req.guild, value, label).id;
        };
        channelSetting('logChannelId', req.body.logChannelId, 'Log channel');
        channelSetting('welcomeChannelId', req.body.welcomeChannelId, 'Welcome channel');
        set.welcomeEnabled = req.body.welcomeEnabled !== false && req.body.welcomeEnabled !== 'false';
        await settingsLib.updateSettings(db, req.guild.id, set, unset);
        res.json({ ok: true, message: 'Settings saved.' });
    }));

    // ---------- messages ----------
    router.post('/guilds/:guildId/announce', wrap(async (req, res) => {
        const channel = textChannel(req.guild, req.body.channelId);
        const message = String(req.body.message || '').trim();
        const title = String(req.body.title || '').trim().slice(0, 250);
        if (!message) throw new UserError('Please write a message.');
        if (message.length > 4000) throw new UserError('The message is too long (max 4000 characters).');
        const colorInput = String(req.body.color || '#f08cbc').trim();
        const color = /^#?[0-9a-fA-F]{6}$/.test(colorInput) ? (colorInput.startsWith('#') ? colorInput : `#${colorInput}`) : '#f08cbc';

        const embed = new EmbedBuilder().setDescription(message).setColor(color)
            .setFooter({ text: 'Chicken Games' }).setTimestamp();
        if (title) embed.setTitle(title);
        await channel.send({ embeds: [embed], allowedMentions: { parse: [] } });
        res.json({ ok: true, message: `Announcement sent to #${channel.name}.` });
    }));

    router.post('/guilds/:guildId/ticket-panel', wrap(async (req, res) => {
        const channel = textChannel(req.guild, req.body.channelId);
        let staffRoleId;
        if (req.body.staffRoleId) {
            staffRoleId = needId(req.body.staffRoleId, 'Staff role');
            if (!req.guild.roles.cache.has(staffRoleId)) throw new UserError('Staff role not found.');
        }
        const categories = tickets.parseCategories(req.body.categories);
        await tickets.sendTicketPanel(channel, { categories, staffRoleId });
        res.json({ ok: true, message: `Ticket panel with ${categories.length} categories posted in #${channel.name}.` });
    }));

    router.post('/guilds/:guildId/verify-panel', wrap(async (req, res) => {
        const channel = textChannel(req.guild, req.body.channelId);
        const role = verify.checkRole(req.guild, needId(req.body.roleId, 'Role'));
        const rules = String(req.body.rules || '').trim().slice(0, 1500) || undefined;
        const title = String(req.body.title || '').trim().slice(0, 100) || undefined;
        await verify.sendVerifyPanel(channel, role, rules, title);
        res.json({ ok: true, message: `Verification panel posted in #${channel.name}.` });
    }));

    // ---------- spawner market ----------
    router.get('/guilds/:guildId/spawners', wrap(async (req, res) => {
        const db = needDb();
        const rows = await spawners.listOffers(db, req.guild.id, { statuses: ['open', 'pending'], limit: 50 });
        const s = await settingsLib.getSettings(db, req.guild.id);
        res.json({
            settings: { spawnerChannelId: s.spawnerChannelId || '', spawnerStaffRoleId: s.spawnerStaffRoleId || '' },
            offers: rows.map((o) => ({
                offerId: o.offerId, type: o.type, amount: o.amount, price: o.price,
                seller: o.sellerTag, ign: o.ign, status: o.status, buyer: o.buyerTag || null, createdAt: o.createdAt
            }))
        });
    }));

    router.post('/guilds/:guildId/spawner-panel', wrap(async (req, res) => {
        const db = needDb();
        const panelChannel = textChannel(req.guild, req.body.channelId, 'Panel channel');
        const set = {};
        const unset = [];
        if (req.body.offersChannelId) set.spawnerChannelId = textChannel(req.guild, req.body.offersChannelId, 'Offers channel').id;
        else unset.push('spawnerChannelId');
        if (req.body.staffRoleId) {
            const roleId = needId(req.body.staffRoleId, 'Staff role');
            if (!req.guild.roles.cache.has(roleId)) throw new UserError('Staff role not found.');
            set.spawnerStaffRoleId = roleId;
        } else unset.push('spawnerStaffRoleId');
        await settingsLib.updateSettings(db, req.guild.id, set, unset);
        await spawners.sendSpawnerPanel(panelChannel);
        res.json({ ok: true, message: `Spawner market panel posted in #${panelChannel.name}.` });
    }));

    router.delete('/guilds/:guildId/spawners/:offerId', wrap(async (req, res) => {
        const db = needDb();
        await spawners.cancelOffer(req.guild, db, req.params.offerId, { force: true });
        res.json({ ok: true });
    }));

    return router;
};
