// ==========================================
// TRADER RANKS
// Every completed spawner trade counts for seller and buyer (not for the owner side of an owner sale).
// When a member reaches a new rank the bot announces it and (if it may) gives a rank role.
// Commands: /trader-profile /trader-top
// ==========================================
const { SlashCommandBuilder, EmbedBuilder, MessageFlags, PermissionFlagsBits } = require('discord.js');
const { COLORS, FOOTER, formatMoney } = require('../lib/brand');

const RANKS = [
    { name: 'Newcomer', min: 0, color: '#8b8b8b' },
    { name: 'Trader', min: 3, color: '#3ecf8e' },
    { name: 'Merchant', min: 10, color: '#5aa9f7' },
    { name: 'Dealer', min: 25, color: '#f7a35a' },
    { name: 'Tycoon', min: 50, color: '#f08cbc' }
];
const ROLE_PREFIX = 'Trader | ';

const commands = [
    new SlashCommandBuilder().setName('trader-profile').setDescription('Show the trader rank of a member')
        .setDMPermission(false)
        .addUserOption(o => o.setName('user').setDescription('Member (default: you)')),
    new SlashCommandBuilder().setName('trader-top').setDescription('Top traders of this server')
        .setDMPermission(false)
];
const NAMES = new Set(commands.map(c => c.name));

async function init(db) {
    await db.collection('trader_stats').createIndex({ guildId: 1, userId: 1 }, { unique: true });
    await db.collection('trader_stats').createIndex({ guildId: 1, trades: -1 });
}

function rankFor(trades) {
    let current = RANKS[0];
    for (const r of RANKS) if (trades >= r.min) current = r;
    return current;
}
function nextRank(trades) {
    return RANKS.find(r => r.min > trades) || null;
}

async function giveRankRole(guild, userId, rank) {
    try {
        if (!guild.members.me.permissions.has(PermissionFlagsBits.ManageRoles)) return;
        const member = await guild.members.fetch(userId).catch(() => null);
        if (!member) return;
        const wanted = ROLE_PREFIX + rank.name;
        let role = guild.roles.cache.find(r => r.name === wanted);
        if (!role && rank.min > 0) role = await guild.roles.create({ name: wanted, color: rank.color, reason: 'Trader rank' });
        if (role && role.position >= guild.members.me.roles.highest.position) return; // bot role must be higher
        const old = member.roles.cache.filter(r => r.name.startsWith(ROLE_PREFIX) && r.name !== wanted);
        if (old.size) await member.roles.remove(old, 'Trader rank change');
        if (role && !member.roles.cache.has(role.id)) await member.roles.add(role, 'Trader rank');
    } catch (e) {
        console.error('[RANKS] Role update failed:', e.message);
    }
}

// Called after a trade was completed. Returns the list of members who ranked up.
async function recordTrade(guild, db, offer) {
    const total = offer.price * offer.amount;
    const col = db.collection('trader_stats');
    const members = [{ id: offer.sellerId, field: 'asSeller' }];
    if (!offer.ownerSale) members.push({ id: offer.buyerId, field: 'asBuyer' });

    const rankups = [];
    for (const m of members) {
        const doc = await col.findOneAndUpdate(
            { guildId: guild.id, userId: m.id },
            { $inc: { trades: 1, volume: total, [m.field]: 1 }, $set: { lastTradeAt: new Date() } },
            { upsert: true, returnDocument: 'after' }
        );
        const before = rankFor(doc.trades - 1);
        const after = rankFor(doc.trades);
        if (after.name !== before.name) {
            rankups.push({ userId: m.id, rank: after, trades: doc.trades });
            await giveRankRole(guild, m.id, after);
        }
    }
    return rankups;
}

async function announce(guild, channelId, rankups) {
    if (!rankups.length) return;
    const channel = guild.channels.cache.get(channelId);
    if (!channel) return;
    for (const r of rankups) {
        const next = nextRank(r.trades);
        await channel.send({
            embeds: [new EmbedBuilder()
                .setTitle('Rank up')
                .setColor(r.rank.color)
                .setDescription(`<@${r.userId}> reached the rank **${r.rank.name}** with **${r.trades}** completed trades.`)
                .addFields({ name: 'Next rank', value: next ? `${next.name} at ${next.min} trades` : 'Highest rank reached' })
                .setFooter({ text: FOOTER })],
            allowedMentions: { users: [r.userId] }
        }).catch(() => {});
    }
}

function progressBar(trades) {
    const cur = rankFor(trades);
    const next = nextRank(trades);
    if (!next) return '`' + '█'.repeat(12) + '` max';
    const pct = (trades - cur.min) / (next.min - cur.min);
    const filled = Math.round(pct * 12);
    return '`' + '█'.repeat(filled) + '░'.repeat(12 - filled) + `\` ${trades}/${next.min}`;
}

async function handle(interaction, ctx) {
    if (!(interaction.isChatInputCommand() && NAMES.has(interaction.commandName))) return false;
    const db = ctx.getDb();
    if (!db) {
        await interaction.reply({ content: 'The database is not connected.', flags: MessageFlags.Ephemeral });
        return true;
    }
    const guild = interaction.guild;
    const col = db.collection('trader_stats');

    if (interaction.commandName === 'trader-profile') {
        const user = interaction.options.getUser('user') || interaction.user;
        const doc = await col.findOne({ guildId: guild.id, userId: user.id });
        const trades = doc?.trades || 0;
        const rank = rankFor(trades);
        const embed = new EmbedBuilder()
            .setTitle(`${user.username} · ${rank.name}`)
            .setColor(rank.color)
            .setThumbnail(user.displayAvatarURL())
            .addFields(
                { name: 'Completed trades', value: String(trades), inline: true },
                { name: 'Volume', value: formatMoney(doc?.volume || 0), inline: true },
                { name: 'Sold / Bought', value: `${doc?.asSeller || 0} / ${doc?.asBuyer || 0}`, inline: true },
                { name: 'Progress', value: progressBar(trades) }
            )
            .setFooter({ text: FOOTER });
        await interaction.reply({ embeds: [embed], flags: MessageFlags.Ephemeral });
        return true;
    }

    const top = await col.find({ guildId: guild.id }).sort({ trades: -1, volume: -1 }).limit(10).toArray();
    const lines = top.map((d, i) => `**${i + 1}.** <@${d.userId}> · ${rankFor(d.trades).name} · ${d.trades} trades · ${formatMoney(d.volume || 0)}`);
    await interaction.reply({
        embeds: [new EmbedBuilder().setTitle('Top traders').setColor(COLORS.primary)
            .setDescription(lines.join('\n') || 'No completed trades yet.').setFooter({ text: FOOTER })],
        flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] }
    });
    return true;
}

module.exports = { commands, handle, init, recordTrade, announce, rankFor, RANKS };
