// ==========================================
// ⭐ LEVEL SYSTEM
// Slash commands: /rank /leaderboard /levelrole (add | remove | list)
// XP per message (15-25, once every 60 seconds per user). Data in MongoDB ("levels", "levelroles").
// The helper functions below are also used by the web dashboard.
// ==========================================
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, MessageFlags
} = require('discord.js');
const { UserError } = require('../lib/errors');

const XP_MIN = 15;
const XP_MAX = 25;
const COOLDOWN_MS = 60 * 1000;
const cooldowns = new Map(); // "guildId:userId" -> timestamp

// XP needed to get from level `l` to `l + 1`
const xpNeeded = (l) => 5 * l * l + 50 * l + 100;

// Total XP -> level and progress
function levelFromXp(totalXp) {
    let level = 0;
    let rest = totalXp;
    while (rest >= xpNeeded(level)) {
        rest -= xpNeeded(level);
        level++;
    }
    return { level, current: rest, needed: xpNeeded(level) };
}

const commands = [
    new SlashCommandBuilder().setName('rank').setDescription('Show level and XP')
        .setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Whose rank? (default: you)')),

    new SlashCommandBuilder().setName('leaderboard').setDescription('Show the top 10 of this server')
        .setDMPermission(false),

    new SlashCommandBuilder().setName('levelrole').setDescription('Manage role rewards for levels')
        .setDefaultMemberPermissions(PermissionFlagsBits.ManageRoles).setDMPermission(false)
        .addSubcommand(s => s.setName('add').setDescription('Give a role at a certain level')
            .addIntegerOption(o => o.setName('level').setDescription('From which level?').setMinValue(1).setMaxValue(200).setRequired(true))
            .addRoleOption(o => o.setName('role').setDescription('Which role?').setRequired(true)))
        .addSubcommand(s => s.setName('remove').setDescription('Remove a role reward')
            .addIntegerOption(o => o.setName('level').setDescription('Which level?').setMinValue(1).setMaxValue(200).setRequired(true)))
        .addSubcommand(s => s.setName('list').setDescription('Show all role rewards')),
];

const NAMES = new Set(commands.map(c => c.name));

async function init(db) {
    await db.collection('levels').createIndex({ guildId: 1, userId: 1 }, { unique: true });
    await db.collection('levels').createIndex({ guildId: 1, xp: -1 });
    await db.collection('levelroles').createIndex({ guildId: 1, level: 1 }, { unique: true });
}

function requireDb(db) {
    if (!db) throw new UserError('The database is not connected (check MONGODB_URI in Render).');
    return db;
}

function progressBar(current, needed, size = 12) {
    const filled = Math.max(0, Math.min(size, Math.round((current / needed) * size)));
    return '█'.repeat(filled) + '░'.repeat(size - filled);
}

// ---------- shared helpers (slash commands + dashboard) ----------
async function addLevelRole(guild, db, level, roleId) {
    requireDb(db);
    if (!Number.isInteger(level) || level < 1 || level > 200) throw new UserError('Level must be a whole number between 1 and 200.');
    const role = guild.roles.cache.get(roleId);
    if (!role) throw new UserError('Role not found.');
    if (role.managed || role.id === guild.id) throw new UserError('This role cannot be assigned.');
    if (role.position >= guild.members.me.roles.highest.position) {
        throw new UserError('That role is above my highest role. Move my role higher in the server settings.');
    }
    await db.collection('levelroles').updateOne(
        { guildId: guild.id, level }, { $set: { guildId: guild.id, level, roleId } }, { upsert: true }
    );
    return role;
}

async function removeLevelRole(db, guildId, level) {
    requireDb(db);
    const res = await db.collection('levelroles').deleteOne({ guildId, level });
    if (!res.deletedCount) throw new UserError(`There is no reward for level ${level}.`);
}

async function listLevelRoles(db, guildId) {
    requireDb(db);
    return db.collection('levelroles').find({ guildId }).sort({ level: 1 }).toArray();
}

async function leaderboard(db, guildId, limit = 10) {
    requireDb(db);
    const top = await db.collection('levels').find({ guildId }).sort({ xp: -1 }).limit(limit).toArray();
    return top.map(d => ({ userId: d.userId, xp: d.xp, level: levelFromXp(d.xp).level }));
}

// ---------- XP for every message ----------
async function onMessage(message, ctx) {
    if (!message.guild || message.author.bot || message.webhookId || message.system) return;
    const db = ctx.getDb();
    if (!db) return;

    const key = `${message.guild.id}:${message.author.id}`;
    const now = Date.now();
    if (now - (cooldowns.get(key) || 0) < COOLDOWN_MS) return;
    cooldowns.set(key, now);

    const gain = Math.floor(Math.random() * (XP_MAX - XP_MIN + 1)) + XP_MIN;
    const doc = await db.collection('levels').findOneAndUpdate(
        { guildId: message.guild.id, userId: message.author.id },
        { $inc: { xp: gain } },
        { upsert: true, returnDocument: 'after' }
    );
    if (!doc) return;

    const before = levelFromXp(doc.xp - gain).level;
    const after = levelFromXp(doc.xp).level;
    if (after <= before) return;

    // Level up!
    const embed = new EmbedBuilder()
        .setColor(require('../lib/brand').COLORS.primary)
        .setTitle('Level Up! 🎉')
        .setDescription(`${message.author} is now **level ${after}**!`);
    message.channel.send({ embeds: [embed] }).catch(() => {});

    // Role rewards
    const rewards = await db.collection('levelroles')
        .find({ guildId: message.guild.id, level: { $lte: after } }).toArray();
    if (rewards.length && message.member) {
        for (const r of rewards) {
            if (!message.member.roles.cache.has(r.roleId)) {
                await message.member.roles.add(r.roleId, `Reached level ${r.level}`).catch(() => {});
            }
        }
    }
}

// ---------- slash commands ----------
async function handle(interaction, ctx) {
    if (!interaction.isChatInputCommand() || !NAMES.has(interaction.commandName)) return false;
    const fail = (text) => interaction.reply({ content: `❌ ${text}`, flags: MessageFlags.Ephemeral });
    if (!interaction.guild) { await fail('This command only works on servers.'); return true; }

    const db = ctx.getDb();
    const guildId = interaction.guild.id;

    try {
        requireDb(db);

        if (interaction.commandName === 'rank') {
            const user = interaction.options.getUser('user') || interaction.user;
            if (user.bot) { await fail('Bots do not have a level.'); return true; }

            const doc = await db.collection('levels').findOne({ guildId, userId: user.id });
            const xp = doc?.xp || 0;
            const { level, current, needed } = levelFromXp(xp);
            const position = (await db.collection('levels').countDocuments({ guildId, xp: { $gt: xp } })) + 1;

            const embed = new EmbedBuilder()
                .setColor(require('../lib/brand').COLORS.primary)
                .setAuthor({ name: user.username, iconURL: user.displayAvatarURL() })
                .setTitle(`Level ${level}`)
                .setDescription(`${progressBar(current, needed)}\n**${current} / ${needed} XP** to level ${level + 1}`)
                .addFields(
                    { name: 'Rank', value: doc ? `#${position}` : '-', inline: true },
                    { name: 'Total XP', value: `${xp}`, inline: true }
                );
            await interaction.reply({ embeds: [embed] });
            return true;
        }

        if (interaction.commandName === 'leaderboard') {
            const top = await leaderboard(db, guildId, 10);
            const medals = ['🥇', '🥈', '🥉'];
            const lines = top.map((d, i) =>
                `${medals[i] || `**${i + 1}.**`} <@${d.userId}> — Level **${d.level}** (${d.xp} XP)`);
            const embed = new EmbedBuilder()
                .setColor(require('../lib/brand').COLORS.primary)
                .setTitle(`Top 10 – ${interaction.guild.name}`)
                .setDescription(lines.length ? lines.join('\n') : 'Nobody has collected XP yet. Start chatting!');
            await interaction.reply({ embeds: [embed], allowedMentions: { parse: [] } });
            return true;
        }

        if (interaction.commandName === 'levelrole') {
            const sub = interaction.options.getSubcommand();

            if (sub === 'add') {
                const level = interaction.options.getInteger('level');
                const role = await addLevelRole(interaction.guild, db, level, interaction.options.getRole('role').id);
                await interaction.reply({ content: `✅ Members reaching **level ${level}** will get ${role}.`, flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
                return true;
            }

            if (sub === 'remove') {
                const level = interaction.options.getInteger('level');
                await removeLevelRole(db, guildId, level);
                await interaction.reply({ content: `✅ Reward for level ${level} removed.`, flags: MessageFlags.Ephemeral });
                return true;
            }

            if (sub === 'list') {
                const rows = await listLevelRoles(db, guildId);
                await interaction.reply({
                    content: rows.length
                        ? rows.map(r => `Level **${r.level}** → <@&${r.roleId}>`).join('\n')
                        : 'No role rewards yet. Create one with `/levelrole add`.',
                    flags: MessageFlags.Ephemeral,
                    allowedMentions: { parse: [] }
                });
                return true;
            }
        }
    } catch (e) {
        if (e instanceof UserError) { await fail(e.message); return true; }
        throw e;
    }
    return false;
}

module.exports = {
    commands, handle, init, onMessage,
    levelFromXp, xpNeeded, addLevelRole, removeLevelRole, listLevelRoles, leaderboard
};
