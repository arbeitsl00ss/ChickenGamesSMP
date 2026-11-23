// ==========================================
// 🔨 MODERATION + LOGS
// Slash commands: /ban /unban /kick /timeout /untimeout /warn /warnings /clearwarns /setlogchannel
// The same `actions` are used by the web dashboard (see api/routes.js).
// Warnings and settings are stored in MongoDB ("warns" and "settings").
// ==========================================
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, MessageFlags, ChannelType
} = require('discord.js');
const { UserError } = require('../lib/errors');
const { sendLog, updateSettings } = require('../lib/settings');

const AUTO_TIMEOUT_AT_WARNS = 3;   // automatic timeout once a member reaches this many warnings
const AUTO_TIMEOUT_MINUTES = 60;
const DEFAULT_REASON = 'No reason provided';

const commands = [
    new SlashCommandBuilder().setName('ban').setDescription('Ban a member')
        .setDefaultMemberPermissions(PermissionFlagsBits.BanMembers).setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Who should be banned?').setRequired(true))
        .addStringOption(o => o.setName('reason').setDescription('Reason')),

    new SlashCommandBuilder().setName('unban').setDescription('Remove a ban')
        .setDefaultMemberPermissions(PermissionFlagsBits.BanMembers).setDMPermission(false)
        .addStringOption(o => o.setName('userid').setDescription('Discord user ID').setRequired(true))
        .addStringOption(o => o.setName('reason').setDescription('Reason')),

    new SlashCommandBuilder().setName('kick').setDescription('Kick a member from the server')
        .setDefaultMemberPermissions(PermissionFlagsBits.KickMembers).setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Who should be kicked?').setRequired(true))
        .addStringOption(o => o.setName('reason').setDescription('Reason')),

    new SlashCommandBuilder().setName('timeout').setDescription('Put a member in timeout (mute)')
        .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers).setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Who?').setRequired(true))
        .addIntegerOption(o => o.setName('minutes').setDescription('Duration in minutes (max 40320 = 28 days)')
            .setMinValue(1).setMaxValue(40320).setRequired(true))
        .addStringOption(o => o.setName('reason').setDescription('Reason')),

    new SlashCommandBuilder().setName('untimeout').setDescription('Remove a timeout')
        .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers).setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Who?').setRequired(true)),

    new SlashCommandBuilder().setName('warn').setDescription('Warn a member')
        .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers).setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Who?').setRequired(true))
        .addStringOption(o => o.setName('reason').setDescription('Reason').setRequired(true)),

    new SlashCommandBuilder().setName('warnings').setDescription('Show the warnings of a member')
        .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers).setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Who?').setRequired(true)),

    new SlashCommandBuilder().setName('clearwarns').setDescription('Delete all warnings of a member')
        .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers).setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Who?').setRequired(true)),

    new SlashCommandBuilder().setName('setlogchannel').setDescription('Set the channel for moderation logs')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator).setDMPermission(false)
        .addChannelOption(o => o.setName('channel').setDescription('Log channel')
            .addChannelTypes(ChannelType.GuildText).setRequired(true)),
];

const NAMES = new Set(commands.map(c => c.name));

// ---------- helpers ----------
function requireDb(db) {
    if (!db) throw new UserError('The database is not connected (check MONGODB_URI in Render).');
    return db;
}

function parseMinutes(v) {
    const m = Number(v);
    if (!Number.isInteger(m) || m < 1 || m > 40320) {
        throw new UserError('Minutes must be a whole number between 1 and 40320 (28 days).');
    }
    return m;
}

async function fetchTarget(guild, userId) {
    const user = await guild.client.users.fetch(userId).catch(() => null);
    if (!user) throw new UserError('User not found. Check the ID.');
    const member = await guild.members.fetch(userId).catch(() => null);
    return { user, member };
}

// actor = { tag, id?, member? } - id/member are only known for slash commands
function guard(guild, actor, userId, member) {
    if (userId === guild.ownerId) throw new UserError('The server owner cannot be moderated.');
    if (userId === guild.client.user.id) throw new UserError('I cannot moderate myself.');
    if (actor.id && userId === actor.id) throw new UserError('You cannot moderate yourself.');
    if (member && actor.member && guild.ownerId !== actor.member.id
        && member.roles.highest.position >= actor.member.roles.highest.position) {
        throw new UserError('That member has the same or a higher role than you.');
    }
}

function modEmbed(title, color, fields, actor) {
    return new EmbedBuilder()
        .setTitle(title)
        .setColor(color)
        .addFields(fields)
        .setFooter({ text: `Moderator: ${actor.tag}` })
        .setTimestamp();
}

// ---------- actions (return an embed; the caller replies and writes the log) ----------
const actions = {
    async ban(guild, db, actor, { userId, reason }) {
        reason = reason || DEFAULT_REASON;
        const { user, member } = await fetchTarget(guild, userId);
        guard(guild, actor, userId, member);
        if (member && !member.bannable) throw new UserError('I cannot ban this member (my role is too low).');
        await user.send(`🔨 You were banned from **${guild.name}**.\nReason: ${reason}`).catch(() => {});
        await guild.members.ban(user, { reason: `${actor.tag}: ${reason}` });
        return modEmbed('Member banned 🔨', '#f75a5a', [
            { name: 'User', value: `${user.tag} (${user.id})` },
            { name: 'Reason', value: reason }
        ], actor);
    },

    async unban(guild, db, actor, { userId, reason }) {
        reason = reason || DEFAULT_REASON;
        try {
            await guild.members.unban(userId, `${actor.tag}: ${reason}`);
        } catch {
            throw new UserError('That user is not banned, or the ID is invalid.');
        }
        return modEmbed('Ban removed ✅', '#3ecf8e', [
            { name: 'User ID', value: userId }, { name: 'Reason', value: reason }
        ], actor);
    },

    async kick(guild, db, actor, { userId, reason }) {
        reason = reason || DEFAULT_REASON;
        const { user, member } = await fetchTarget(guild, userId);
        if (!member) throw new UserError('That user is not on this server.');
        guard(guild, actor, userId, member);
        if (!member.kickable) throw new UserError('I cannot kick this member (my role is too low).');
        await user.send(`👢 You were kicked from **${guild.name}**.\nReason: ${reason}`).catch(() => {});
        await member.kick(`${actor.tag}: ${reason}`);
        return modEmbed('Member kicked 👢', '#f7a35a', [
            { name: 'User', value: `${user.tag} (${user.id})` }, { name: 'Reason', value: reason }
        ], actor);
    },

    async timeout(guild, db, actor, { userId, reason, minutes }) {
        reason = reason || DEFAULT_REASON;
        const mins = parseMinutes(minutes);
        const { user, member } = await fetchTarget(guild, userId);
        if (!member) throw new UserError('That user is not on this server.');
        guard(guild, actor, userId, member);
        if (!member.moderatable) throw new UserError('I cannot time out this member (my role is too low).');
        await member.timeout(mins * 60 * 1000, `${actor.tag}: ${reason}`);
        return modEmbed('Timeout applied 🔇', '#f7a35a', [
            { name: 'User', value: `${user.tag} (${user.id})` },
            { name: 'Duration', value: `${mins} minutes` },
            { name: 'Reason', value: reason }
        ], actor);
    },

    async untimeout(guild, db, actor, { userId }) {
        const { user, member } = await fetchTarget(guild, userId);
        if (!member) throw new UserError('That user is not on this server.');
        if (!member.moderatable) throw new UserError('I cannot change the timeout of this member.');
        await member.timeout(null, `${actor.tag}: timeout removed`);
        return modEmbed('Timeout removed 🔊', '#3ecf8e', [
            { name: 'User', value: `${user.tag} (${user.id})` }
        ], actor);
    },

    async warn(guild, db, actor, { userId, reason }) {
        reason = reason || DEFAULT_REASON;
        requireDb(db);
        const { user, member } = await fetchTarget(guild, userId);
        if (!member) throw new UserError('That user is not on this server.');
        if (user.bot) throw new UserError('Bots cannot be warned.');
        guard(guild, actor, userId, member);

        await db.collection('warns').insertOne({
            guildId: guild.id, userId, modId: actor.id || null, modTag: actor.tag, reason, createdAt: new Date()
        });
        const count = await db.collection('warns').countDocuments({ guildId: guild.id, userId });
        await user.send(`⚠️ You were warned on **${guild.name}** (warning ${count}).\nReason: ${reason}`).catch(() => {});

        const fields = [
            { name: 'User', value: `${user.tag} (${user.id})` },
            { name: 'Reason', value: reason },
            { name: 'Total warnings', value: `${count}` }
        ];
        if (count >= AUTO_TIMEOUT_AT_WARNS && member.moderatable) {
            await member.timeout(AUTO_TIMEOUT_MINUTES * 60 * 1000, `Automatic: ${count} warnings`).catch(() => {});
            fields.push({ name: 'Automatic', value: `${AUTO_TIMEOUT_MINUTES} minute timeout (at ${AUTO_TIMEOUT_AT_WARNS} warnings)` });
        }
        return modEmbed('Member warned ⚠️', '#f7d55a', fields, actor);
    },

    async clearwarns(guild, db, actor, { userId }) {
        requireDb(db);
        const user = await guild.client.users.fetch(userId).catch(() => null);
        const res = await db.collection('warns').deleteMany({ guildId: guild.id, userId });
        return modEmbed('Warnings cleared 🧹', '#3ecf8e', [
            { name: 'User', value: user ? `${user.tag} (${user.id})` : userId },
            { name: 'Deleted', value: `${res.deletedCount}` }
        ], actor);
    },
};

async function listWarnings(db, guildId, userId, limit = 10) {
    requireDb(db);
    const filter = { guildId, userId };
    const list = await db.collection('warns').find(filter).sort({ createdAt: -1 }).limit(limit).toArray();
    const total = await db.collection('warns').countDocuments(filter);
    return { list, total };
}

// ---------- slash command handler ----------
async function handle(interaction, ctx) {
    if (!interaction.isChatInputCommand() || !NAMES.has(interaction.commandName)) return false;
    const fail = (text) => interaction.reply({ content: `❌ ${text}`, flags: MessageFlags.Ephemeral });
    if (!interaction.guild) { await fail('This command only works on servers.'); return true; }

    const guild = interaction.guild;
    const db = ctx.getDb();
    const name = interaction.commandName;
    const actor = { tag: interaction.user.tag, id: interaction.user.id, member: interaction.member };
    const reason = interaction.options.getString('reason') || undefined;
    const target = interaction.options.getUser('user');

    try {
        if (name === 'setlogchannel') {
            requireDb(db);
            const channel = interaction.options.getChannel('channel');
            await updateSettings(db, guild.id, { logChannelId: channel.id });
            await interaction.reply({ content: `✅ Moderation logs will now be sent to ${channel}.`, flags: MessageFlags.Ephemeral });
            return true;
        }

        if (name === 'warnings') {
            const { list, total } = await listWarnings(db, guild.id, target.id);
            const embed = new EmbedBuilder()
                .setTitle(`Warnings for ${target.tag}`)
                .setColor('#f7d55a')
                .setDescription(list.length
                    ? list.map((w, i) =>
                        `**${i + 1}.** <t:${Math.floor(w.createdAt.getTime() / 1000)}:d> by ${w.modId ? `<@${w.modId}>` : (w.modTag || 'unknown')}\n${w.reason}`).join('\n\n')
                    : 'No warnings. ✅')
                .setFooter({ text: `Total: ${total}${total > 10 ? ' (showing the latest 10)' : ''}` });
            await interaction.reply({ embeds: [embed], flags: MessageFlags.Ephemeral });
            return true;
        }

        const params = {
            userId: name === 'unban' ? interaction.options.getString('userid').trim() : target?.id,
            reason,
            minutes: interaction.options.getInteger('minutes'),
        };
        const embed = await actions[name](guild, db, actor, params);
        await interaction.reply({ embeds: [embed] });
        await sendLog(guild, db, embed);
        return true;
    } catch (e) {
        if (e instanceof UserError) { await fail(e.message); return true; }
        throw e;
    }
}

module.exports = { commands, handle, actions, listWarnings };
