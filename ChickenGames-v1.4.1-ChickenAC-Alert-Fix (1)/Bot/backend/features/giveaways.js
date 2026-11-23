// ==========================================
// GIVEAWAYS
// /giveaway start | end | reroll | cancel | list     (needs "Manage Server")
// Members join with a button (click again to leave). The bot ends giveaways by itself when the time is up,
// even after a restart (end times are stored in MongoDB).
// ==========================================
const crypto = require('crypto');
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle,
    ChannelType, MessageFlags
} = require('discord.js');
const { UserError } = require('../lib/errors');
const { COLORS, FOOTER } = require('../lib/brand');

const BTN_ENTER = 'giveaway_enter:';
const CHECK_MS = 15 * 1000;
const MAX_ACTIVE = 10;
const MAX_DURATION_MS = 30 * 24 * 3600 * 1000;

const commands = [
    new SlashCommandBuilder().setName('giveaway').setDescription('Manage giveaways')
        .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild).setDMPermission(false)
        .addSubcommand(c => c.setName('start').setDescription('Start a giveaway')
            .addStringOption(o => o.setName('prize').setDescription('What can be won, e.g. 50M or Skeleton Spawner').setRequired(true).setMaxLength(100))
            .addStringOption(o => o.setName('duration').setDescription('How long: 30m, 2h, 1d, 1w (max 30d)').setRequired(true))
            .addIntegerOption(o => o.setName('winners').setDescription('Number of winners (default 1)').setMinValue(1).setMaxValue(20))
            .addChannelOption(o => o.setName('channel').setDescription('Where to post it (default: this channel)')
                .addChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement))
            .addRoleOption(o => o.setName('required_role').setDescription('Only members with this role can enter'))
            .addRoleOption(o => o.setName('ping_role').setDescription('Role to ping when the giveaway starts'))
            .addStringOption(o => o.setName('note').setDescription('Extra info, e.g. how to claim').setMaxLength(300)))
        .addSubcommand(c => c.setName('end').setDescription('End a giveaway now and draw the winners')
            .addStringOption(o => o.setName('id').setDescription('Giveaway ID').setRequired(true)))
        .addSubcommand(c => c.setName('reroll').setDescription('Draw new winners for an ended giveaway')
            .addStringOption(o => o.setName('id').setDescription('Giveaway ID').setRequired(true))
            .addIntegerOption(o => o.setName('count').setDescription('How many new winners (default 1)').setMinValue(1).setMaxValue(20)))
        .addSubcommand(c => c.setName('cancel').setDescription('Cancel a running giveaway without winners')
            .addStringOption(o => o.setName('id').setDescription('Giveaway ID').setRequired(true)))
        .addSubcommand(c => c.setName('list').setDescription('Show running giveaways'))
];

async function init(db) {
    const col = db.collection('giveaways');
    await col.createIndex({ guildId: 1, giveawayId: 1 }, { unique: true });
    await col.createIndex({ status: 1, endsAt: 1 });
}

// ---------- helpers ----------
function requireDb(db) {
    if (!db) throw new UserError('The database is not connected (check MONGODB_URI in Render).');
    return db;
}

function parseDuration(input) {
    const m = String(input || '').trim().toLowerCase().replace(/\s+/g, '').match(/^(\d+)(m|h|d|w)$/);
    if (!m) throw new UserError('Duration is not valid. Use e.g. 30m, 2h, 1d or 1w.');
    const ms = Number(m[1]) * { m: 60e3, h: 3600e3, d: 86400e3, w: 604800e3 }[m[2]];
    if (ms < 60e3) throw new UserError('The giveaway must run at least 1 minute.');
    if (ms > MAX_DURATION_MS) throw new UserError('The giveaway can run 30 days at most.');
    return ms;
}

const cleanId = (v) => String(v || '').trim().replace(/^#/, '').toLowerCase();
const ts = (d) => Math.floor(new Date(d).getTime() / 1000);
const giveawayUrl = (g) => `https://discord.com/channels/${g.guildId}/${g.channelId}/${g.messageId}`;

function shuffle(arr) {
    const a = [...arr];
    for (let i = a.length - 1; i > 0; i--) {
        const j = crypto.randomInt(i + 1);
        [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
}

function giveawayEmbed(g) {
    const embed = new EmbedBuilder().setTitle(g.prize).setFooter({ text: `${FOOTER} • Giveaway • ID ${g.giveawayId}` });
    const base = [
        { name: 'Hosted by', value: `<@${g.hostId}>`, inline: true },
        { name: 'Winners', value: String(g.winnersCount), inline: true },
        { name: 'Entries', value: String(g.entries.length), inline: true }
    ];
    if (g.requiredRoleId) base.push({ name: 'Requirement', value: `Role <@&${g.requiredRoleId}>`, inline: true });

    if (g.status === 'active') {
        embed.setColor(COLORS.primary)
            .setDescription(`Click **Enter** to join.\nEnds <t:${ts(g.endsAt)}:R> (<t:${ts(g.endsAt)}:f>)${g.note ? `\n\n${g.note}` : ''}`)
            .addFields(base);
    } else if (g.status === 'ended') {
        embed.setColor(COLORS.neutral)
            .setDescription(`Ended <t:${ts(g.endedAt)}:R>${g.note ? `\n\n${g.note}` : ''}`)
            .addFields(base, { name: 'Winners', value: g.winners.length ? g.winners.map(id => `<@${id}>`).join(', ') : 'No valid entries' });
        embed.data.fields = embed.data.fields.filter((f, i, arr) => !(f.name === 'Winners' && f.inline && arr.some(x => x.name === 'Winners' && !x.inline)));
    } else {
        embed.setColor(COLORS.danger).setDescription('This giveaway was cancelled.').addFields(base);
    }
    return embed;
}

const enterRow = (id, disabled = false) => new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(BTN_ENTER + id).setLabel('Enter').setStyle(ButtonStyle.Success).setDisabled(disabled)
);

async function fetchMessage(guild, g) {
    const ch = guild.channels.cache.get(g.channelId);
    if (!ch) return null;
    return ch.messages.fetch(g.messageId).catch(() => null);
}

// Picks winners among entries who are still on the server and still meet the requirement.
async function pickWinners(guild, g, count, exclude = []) {
    const pool = [];
    for (const id of shuffle(g.entries.filter(u => !exclude.includes(u)))) {
        if (pool.length >= count) break;
        const member = await guild.members.fetch(id).catch(() => null);
        if (!member || member.user.bot) continue;
        if (g.requiredRoleId && !member.roles.cache.has(g.requiredRoleId)) continue;
        pool.push(id);
    }
    return pool;
}

// ---------- ending ----------
async function finish(guild, db, giveawayId) {
    const col = db.collection('giveaways');
    // atomic: only one caller can switch active -> ended
    const g = await col.findOneAndUpdate(
        { guildId: guild.id, giveawayId, status: 'active' },
        { $set: { status: 'ended', endedAt: new Date() } },
        { returnDocument: 'after' }
    );
    if (!g) return null;
    const winners = await pickWinners(guild, g, g.winnersCount);
    g.winners = winners;
    await col.updateOne({ guildId: guild.id, giveawayId }, { $set: { winners } });

    const msg = await fetchMessage(guild, g);
    if (msg) await msg.edit({ embeds: [giveawayEmbed(g)], components: [enterRow(g.giveawayId, true)], allowedMentions: { parse: [] } }).catch(() => {});

    const ch = guild.channels.cache.get(g.channelId);
    if (ch) {
        const text = winners.length
            ? `Congratulations ${winners.map(id => `<@${id}>`).join(', ')}! You won **${g.prize}**.\nOpen a ticket or contact <@${g.hostId}> to claim your prize.`
            : `Nobody won **${g.prize}**: there were no valid entries.`;
        await ch.send({
            content: text,
            components: [new ActionRowBuilder().addComponents(new ButtonBuilder().setLabel('View giveaway').setStyle(ButtonStyle.Link).setURL(giveawayUrl(g)))],
            allowedMentions: { users: winners }
        }).catch(() => {});
    }
    return g;
}

let timer = null;
async function checkDue(client, getDb) {
    const db = getDb();
    if (!db) return;
    const due = await db.collection('giveaways').find({ status: 'active', endsAt: { $lte: new Date() } }).limit(20).toArray();
    for (const g of due) {
        try {
            const guild = client.guilds.cache.get(g.guildId);
            if (guild) await finish(guild, db, g.giveawayId);
        } catch (e) {
            console.error('[GIVEAWAY] Could not end', g.giveawayId, e.message);
        }
    }
}
function start(client, getDb) {
    if (timer) return;
    timer = setInterval(() => checkDue(client, getDb).catch(e => console.error('[GIVEAWAY]', e.message)), CHECK_MS);
    console.log('[GIVEAWAY] Checking for finished giveaways every 15s.');
}

// ---------- interactions ----------
async function enter(interaction, ctx, id) {
    const db = requireDb(ctx.getDb());
    const col = db.collection('giveaways');
    const guild = interaction.guild;
    const g = await col.findOne({ guildId: guild.id, giveawayId: id });
    if (!g || g.status !== 'active' || new Date(g.endsAt) <= new Date()) throw new UserError('This giveaway has ended.');
    if (g.requiredRoleId && !interaction.member.roles.cache.has(g.requiredRoleId)) {
        throw new UserError(`You need the role <@&${g.requiredRoleId}> to enter this giveaway.`);
    }

    const joined = !g.entries.includes(interaction.user.id);
    const updated = await col.findOneAndUpdate(
        { guildId: guild.id, giveawayId: id, status: 'active' },
        joined ? { $addToSet: { entries: interaction.user.id } } : { $pull: { entries: interaction.user.id } },
        { returnDocument: 'after' }
    );
    if (!updated) throw new UserError('This giveaway has ended.');
    await interaction.update({ embeds: [giveawayEmbed(updated)], components: [enterRow(id)], allowedMentions: { parse: [] } });
    await interaction.followUp({
        content: joined ? `You entered the giveaway for **${updated.prize}**. Good luck!` : 'You left the giveaway. Click **Enter** again to rejoin.',
        flags: MessageFlags.Ephemeral
    });
}

async function startGiveaway(interaction, db) {
    const guild = interaction.guild;
    const o = interaction.options;
    const col = db.collection('giveaways');

    const ms = parseDuration(o.getString('duration'));
    const channel = o.getChannel('channel') || interaction.channel;
    const perms = channel.permissionsFor(guild.members.me);
    if (!perms?.has([PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.EmbedLinks])) {
        throw new UserError(`I am not allowed to post in ${channel}.`);
    }
    const active = await col.countDocuments({ guildId: guild.id, status: 'active' });
    if (active >= MAX_ACTIVE) throw new UserError(`There are already ${MAX_ACTIVE} running giveaways. End one first.`);

    const pingRole = o.getRole('ping_role');
    const g = {
        guildId: guild.id, giveawayId: crypto.randomBytes(3).toString('hex'), channelId: channel.id, messageId: null,
        prize: o.getString('prize').trim(), hostId: interaction.user.id, winnersCount: o.getInteger('winners') || 1,
        requiredRoleId: o.getRole('required_role')?.id || null, note: (o.getString('note') || '').trim(),
        endsAt: new Date(Date.now() + ms), status: 'active', entries: [], winners: [], createdAt: new Date()
    };
    const msg = await channel.send({
        content: pingRole ? `${pingRole}` : undefined,
        embeds: [giveawayEmbed(g)], components: [enterRow(g.giveawayId)],
        allowedMentions: { roles: pingRole ? [pingRole.id] : [] }
    });
    g.messageId = msg.id;
    try {
        await col.insertOne(g);
    } catch (e) {
        await msg.delete().catch(() => {});
        throw e;
    }
    await interaction.reply({ content: `Giveaway **${g.giveawayId}** started in ${channel}: ${giveawayUrl(g)}`, flags: MessageFlags.Ephemeral });
}

async function handle(interaction, ctx) {
    const isMine =
        (interaction.isChatInputCommand() && interaction.commandName === 'giveaway') ||
        (interaction.isButton() && interaction.customId.startsWith('giveaway_'));
    if (!isMine) return false;

    const reply = (content) => {
        const p = { content, flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } };
        return interaction.deferred || interaction.replied ? interaction.followUp(p) : interaction.reply(p);
    };
    if (!interaction.guild) { await reply('This only works on servers.'); return true; }
    const guild = interaction.guild;

    try {
        if (interaction.isButton()) {
            await enter(interaction, ctx, interaction.customId.slice(BTN_ENTER.length));
            return true;
        }

        const db = requireDb(ctx.getDb());
        const col = db.collection('giveaways');
        const sub = interaction.options.getSubcommand();

        if (sub === 'start') { await startGiveaway(interaction, db); return true; }

        if (sub === 'list') {
            const list = await col.find({ guildId: guild.id, status: 'active' }).sort({ endsAt: 1 }).toArray();
            const embed = new EmbedBuilder().setTitle('Running giveaways').setColor(COLORS.primary).setFooter({ text: FOOTER })
                .setDescription(list.length
                    ? list.map(g => `**[${g.giveawayId}](${giveawayUrl(g)})** — ${g.prize} — ${g.entries.length} entries — ends <t:${ts(g.endsAt)}:R>`).join('\n')
                    : 'No running giveaways.');
            await interaction.reply({ embeds: [embed], flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
            return true;
        }

        const id = cleanId(interaction.options.getString('id'));

        if (sub === 'end') {
            await interaction.deferReply({ flags: MessageFlags.Ephemeral });
            const g = await finish(guild, db, id);
            if (!g) throw new UserError('No running giveaway with this ID.');
            await interaction.editReply({ content: `Giveaway **${id}** ended.` });
            return true;
        }

        if (sub === 'cancel') {
            const g = await col.findOneAndUpdate({ guildId: guild.id, giveawayId: id, status: 'active' }, { $set: { status: 'cancelled' } }, { returnDocument: 'after' });
            if (!g) throw new UserError('No running giveaway with this ID.');
            const msg = await fetchMessage(guild, g);
            if (msg) await msg.edit({ embeds: [giveawayEmbed(g)], components: [enterRow(id, true)], allowedMentions: { parse: [] } }).catch(() => {});
            await reply(`Giveaway **${id}** cancelled.`);
            return true;
        }

        if (sub === 'reroll') {
            await interaction.deferReply({ flags: MessageFlags.Ephemeral });
            const g = await col.findOne({ guildId: guild.id, giveawayId: id, status: 'ended' });
            if (!g) throw new UserError('No ended giveaway with this ID.');
            const winners = await pickWinners(guild, g, interaction.options.getInteger('count') || 1, g.winners);
            if (!winners.length) throw new UserError('There is nobody left to draw.');
            await col.updateOne({ guildId: guild.id, giveawayId: id }, { $addToSet: { winners: { $each: winners } } });
            await guild.channels.cache.get(g.channelId)?.send({
                content: `New winner${winners.length > 1 ? 's' : ''} for **${g.prize}**: ${winners.map(w => `<@${w}>`).join(', ')}! Open a ticket or contact <@${g.hostId}> to claim.`,
                allowedMentions: { users: winners }
            });
            await interaction.editReply({ content: `Rerolled giveaway **${id}**.` });
            return true;
        }
    } catch (e) {
        if (!(e instanceof UserError)) throw e;
        if (interaction.isButton()) await interaction.reply({ content: e.message, flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } }).catch(() => {});
        else if (interaction.deferred) await interaction.editReply({ content: e.message });
        else await reply(e.message);
        return true;
    }
    return false;
}

module.exports = { commands, handle, init, start, parseDuration };
