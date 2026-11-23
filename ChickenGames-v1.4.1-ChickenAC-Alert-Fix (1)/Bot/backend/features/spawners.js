// ==========================================
// SPAWNER MARKET (Chicken Games)
// Commands: /spawner-setup /spawner-list /spawner-price /spawner-cancel
//           /owner-setup /owner-price /owner-prices   (fixed buy price of the owner)
// Flow (member to member): seller posts offer -> buyer clicks "Buy / Trade" -> private trade channel
//   -> trade in-game -> BOTH click "Confirm" -> offer is sold and counts for the trader ranks.
// Flow (sell to owner): member clicks "Sell to owner" -> price is the fixed team price for that spawner type
//   -> private channel with the owner -> same confirm flow.
// Data lives in MongoDB ("spawner_offers"). The bot cannot see in-game trades, it only organises them.
// ==========================================
const crypto = require('crypto');
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle,
    ModalBuilder, TextInputBuilder, TextInputStyle, ChannelType, MessageFlags
} = require('discord.js');
const { UserError } = require('../lib/errors');
const { getSettings, updateSettings, sendLog } = require('../lib/settings');
const { FOOTER, COLORS, formatMoney: fmt } = require('../lib/brand');
const ranks = require('./ranks');

const COLOR = COLORS.primary;
const MAX_OPEN_PER_USER = 10;
const TRADE_CATEGORY = 'SPAWNER TRADES';
const SPAWNER_TYPES = [
    'Skeleton', 'Zombie', 'Spider', 'Cave Spider', 'Creeper', 'Blaze', 'Enderman', 'Iron Golem',
    'Pig', 'Cow', 'Sheep', 'Chicken', 'Magma Cube', 'Slime', 'Witch', 'Zombified Piglin', 'Wither Skeleton'
];

const BTN_SELL = 'spawner_sell';
const BTN_BROWSE = 'spawner_browse';
const BTN_OWNER = 'spawner_owner';
const BTN_BUY = 'spawner_buy:';
const BTN_CONFIRM = 'spawner_confirm:';
const BTN_ABORT = 'spawner_abort:';
const MODAL_SELL = 'spawner_modal';
const MODAL_OWNER = 'spawner_owner_modal';

const commands = [
    new SlashCommandBuilder().setName('spawner-setup').setDescription('Post the spawner market panel in this channel')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator).setDMPermission(false)
        .addChannelOption(o => o.setName('offers_channel').setDescription('Where offers are posted (default: this channel)')
            .addChannelTypes(ChannelType.GuildText))
        .addRoleOption(o => o.setName('staff_role').setDescription('Staff role that can see every trade channel')),

    new SlashCommandBuilder().setName('spawner-list').setDescription('Show open spawner offers')
        .setDMPermission(false)
        .addStringOption(o => o.setName('type').setDescription('Only this spawner type, e.g. Skeleton')),

    new SlashCommandBuilder().setName('spawner-price').setDescription('Change the price of an open offer (seller or server manager)')
        .setDMPermission(false)
        .addStringOption(o => o.setName('offer').setDescription('Offer ID (shown on the offer, e.g. a1b2c3)').setRequired(true))
        .addStringOption(o => o.setName('price').setDescription('New price per spawner, e.g. 500K, 2.5M, 1B').setRequired(true)),

    new SlashCommandBuilder().setName('owner-setup').setDescription('Set who buys spawners at the fixed team price')
        .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild).setDMPermission(false)
        .addUserOption(o => o.setName('owner').setDescription('The owner who buys the spawners').setRequired(true)),

    new SlashCommandBuilder().setName('owner-price').setDescription('Set or remove the fixed owner buy price (team only)')
        .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild).setDMPermission(false)
        .addSubcommand(c => c.setName('set').setDescription('Set the fixed price for a spawner type')
            .addStringOption(o => o.setName('type').setDescription('Spawner type, e.g. Skeleton').setRequired(true))
            .addStringOption(o => o.setName('price').setDescription('Price per spawner, e.g. 500K, 2.5M').setRequired(true)))
        .addSubcommand(c => c.setName('remove').setDescription('Stop buying a spawner type')
            .addStringOption(o => o.setName('type').setDescription('Spawner type').setRequired(true))),

    new SlashCommandBuilder().setName('owner-prices').setDescription('Show the fixed prices the owner pays')
        .setDMPermission(false),

    new SlashCommandBuilder().setName('spawner-cancel').setDescription('Cancel one of your open offers')
        .setDMPermission(false)
        .addStringOption(o => o.setName('offer').setDescription('Offer ID (shown on the offer, e.g. a1b2c3)').setRequired(true)),
];

const NAMES = new Set(commands.map(c => c.name));

async function init(db) {
    const col = db.collection('spawner_offers');
    await col.createIndex({ guildId: 1, offerId: 1 }, { unique: true });
    await col.createIndex({ guildId: 1, status: 1, createdAt: -1 });
}

// ---------- helpers ----------
function requireDb(db) {
    if (!db) throw new UserError('The database is not connected (check MONGODB_URI in Render).');
    return db;
}

// "2.5M", "500k", "1,5m", "1.000.000" -> number
function parsePrice(input) {
    let s = String(input || '').trim().toLowerCase().replace(/\s+/g, '').replace(/\$/g, '');
    s = s.replace(/[.,](?=\d{3}(\D|$))/g, '').replace(',', '.');
    const m = s.match(/^(\d+(?:\.\d+)?)([kmb])?$/);
    if (!m) throw new UserError('Price is not valid. Use numbers like 500K, 2.5M or 1B.');
    const mult = { k: 1e3, m: 1e6, b: 1e9 }[m[2]] || 1;
    const value = Math.round(Number(m[1]) * mult);
    if (!(value >= 1) || value > 1e12) throw new UserError('Price must be between 1 and 1T.');
    return value;
}

function normalizeType(input) {
    const cleaned = String(input || '').replace(/spawners?/ig, '').trim().replace(/\s+/g, ' ');
    if (!/^[a-zA-Z0-9 _-]{2,40}$/.test(cleaned)) {
        throw new UserError('Please enter a valid mob type, e.g. Skeleton or Iron Golem.');
    }
    const known = SPAWNER_TYPES.find(t => t.toLowerCase() === cleaned.toLowerCase());
    return known || cleaned.replace(/\b\w/g, c => c.toUpperCase());
}

const cleanId = (v) => String(v || '').trim().replace(/^#/, '').toLowerCase();
const offerUrl = (o) => `https://discord.com/channels/${o.guildId}/${o.channelId}/${o.messageId}`;

const STATUS = {
    open: ['Available', COLORS.success],
    pending: ['Trade in progress', COLORS.warning],
    completed: ['Sold', COLORS.neutral],
    cancelled: ['Cancelled', COLORS.danger],
};

function offerEmbed(o) {
    const [label, color] = STATUS[o.status] || STATUS.open;
    const embed = new EmbedBuilder()
        .setTitle(`${o.amount}x ${o.type} Spawner`)
        .setColor(color)
        .addFields(
            { name: 'Seller', value: `<@${o.sellerId}>\nIGN: \`${o.ign}\``, inline: true },
            { name: 'Price each', value: fmt(o.price), inline: true },
            { name: 'Total', value: fmt(o.price * o.amount), inline: true },
            { name: 'Status', value: label, inline: true },
            { name: 'Offer ID', value: `\`${o.offerId}\``, inline: true }
        )
        .setFooter({ text: `${FOOTER} • Spawner Market` })
        .setTimestamp(o.createdAt);
    if (o.note) embed.addFields({ name: 'Note', value: o.note });
    if (o.status === 'pending' && o.buyerId) embed.addFields({ name: 'Buyer', value: `<@${o.buyerId}>`, inline: true });
    return embed;
}

const buyRow = (offerId) => new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(BTN_BUY + offerId).setLabel('Buy / Trade').setStyle(ButtonStyle.Success)
);

const tradeRow = (offerId) => new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(BTN_CONFIRM + offerId).setLabel('Confirm: trade done').setStyle(ButtonStyle.Success),
    new ButtonBuilder().setCustomId(BTN_ABORT + offerId).setLabel('Cancel trade').setStyle(ButtonStyle.Danger)
);

async function refreshOfferMessage(guild, offer) {
    try {
        if (!offer.messageId || !offer.channelId) return; // owner sales have no public message
        const ch = guild.channels.cache.get(offer.channelId);
        if (!ch) return;
        const msg = await ch.messages.fetch(offer.messageId);
        await msg.edit({
            embeds: [offerEmbed(offer)],
            components: offer.status === 'open' ? [buyRow(offer.offerId)] : [],
            allowedMentions: { parse: [] }
        });
    } catch { /* message was deleted - nothing to update */ }
}

// ---------- panel ----------
async function sendSpawnerPanel(channel) {
    const embed = new EmbedBuilder()
        .setTitle('Spawner Market')
        .setColor(COLOR)
        .setDescription('Buy and sell spawners safely. Every trade runs in its own private channel.')
        .addFields(
            { name: '1 · Sell', value: 'Click **Sell a spawner**, fill in the form and your offer goes live.' },
            { name: '2 · Buy', value: 'Click **Buy / Trade** on an offer. A private channel with the seller opens.' },
            { name: '3 · Trade', value: 'Do the trade in-game. Once you received everything, **both** sides click **Confirm**.' },
            { name: 'Sell to the owner', value: 'Want a quick sale? Click **Sell to owner**. The owner pays a fixed price set by the team (`/owner-prices`).' },
            { name: 'Good to know', value: 'The bot cannot see in-game trades. Only confirm after you got your items or money, and never trade outside the trade channel. Completed trades raise your trader rank (`/trader-profile`).' }
        )
        .setFooter({ text: FOOTER });
    const row = new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId(BTN_SELL).setLabel('Sell a spawner').setStyle(ButtonStyle.Primary),
        new ButtonBuilder().setCustomId(BTN_OWNER).setLabel('Sell to owner').setStyle(ButtonStyle.Success),
        new ButtonBuilder().setCustomId(BTN_BROWSE).setLabel('Browse offers').setStyle(ButtonStyle.Secondary)
    );
    await channel.send({ embeds: [embed], components: [row] });
}

// ---------- offers ----------
async function createOffer(guild, db, user, input, fallbackChannel) {
    requireDb(db);
    const col = db.collection('spawner_offers');

    const type = normalizeType(input.type);
    const amount = Number(String(input.amount || '').trim());
    if (!Number.isInteger(amount) || amount < 1 || amount > 100000) throw new UserError('Amount must be a whole number between 1 and 100000.');
    const price = parsePrice(input.price);
    const ign = String(input.ign || '').trim();
    if (!/^\.?[A-Za-z0-9_ ]{2,20}$/.test(ign)) throw new UserError('Please enter your exact Minecraft name (IGN).');
    const note = String(input.note || '').trim().slice(0, 300);

    const open = await col.countDocuments({ guildId: guild.id, sellerId: user.id, status: { $in: ['open', 'pending'] } });
    if (open >= MAX_OPEN_PER_USER) throw new UserError(`You already have ${MAX_OPEN_PER_USER} active offers. Cancel one first.`);

    const s = await getSettings(db, guild.id);
    let channel = s.spawnerChannelId ? guild.channels.cache.get(s.spawnerChannelId) : null;
    if (!channel) channel = fallbackChannel;
    if (!channel || !channel.isTextBased()) throw new UserError('No channel for offers is set up. An admin must run /spawner-setup.');
    const perms = channel.permissionsFor(guild.members.me);
    if (!perms || !perms.has([PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.EmbedLinks])) {
        throw new UserError(`I am not allowed to post in ${channel}.`);
    }

    const offer = {
        guildId: guild.id, offerId: crypto.randomBytes(3).toString('hex'),
        sellerId: user.id, sellerTag: user.tag, type, amount, price, ign, note,
        status: 'open', channelId: channel.id, messageId: null, createdAt: new Date()
    };
    const msg = await channel.send({ embeds: [offerEmbed(offer)], components: [buyRow(offer.offerId)], allowedMentions: { parse: [] } });
    offer.messageId = msg.id;
    try {
        await col.insertOne(offer);
    } catch (e) {
        await msg.delete().catch(() => {});
        throw e;
    }
    return offer;
}

async function listOffers(db, guildId, { type, statuses = ['open'], limit = 10 } = {}) {
    requireDb(db);
    const filter = { guildId, status: { $in: statuses } };
    if (type) filter.type = normalizeType(type);
    return db.collection('spawner_offers').find(filter).sort({ createdAt: -1 }).limit(limit).toArray();
}

async function cancelOffer(guild, db, offerId, { userId, force = false } = {}) {
    requireDb(db);
    const col = db.collection('spawner_offers');
    const id = cleanId(offerId);
    const offer = await col.findOne({ guildId: guild.id, offerId: id });
    if (!offer || !['open', 'pending'].includes(offer.status)) throw new UserError('Offer not found or already finished.');
    if (!force && offer.sellerId !== userId) throw new UserError('Only the seller can cancel this offer.');
    if (!force && offer.status === 'pending') throw new UserError('A trade is running. Cancel it inside the trade channel first.');

    const res = await col.findOneAndUpdate(
        { guildId: guild.id, offerId: id, status: offer.status },
        { $set: { status: 'cancelled' } },
        { returnDocument: 'after' }
    );
    if (!res) throw new UserError('This offer just changed. Please try again.');
    if (offer.tradeChannelId) guild.channels.cache.get(offer.tradeChannelId)?.delete().catch(() => {});
    await refreshOfferMessage(guild, res);
    return res;
}

// Change the price of an open offer. Sellers can change their own, managers (force) any.
async function setPrice(guild, db, offerId, priceInput, { userId, force = false, actorTag = 'Unknown' } = {}) {
    requireDb(db);
    const col = db.collection('spawner_offers');
    const id = cleanId(offerId);
    const price = parsePrice(priceInput);
    const offer = await col.findOne({ guildId: guild.id, offerId: id });
    if (!offer || !['open', 'pending'].includes(offer.status)) throw new UserError('Offer not found or already finished.');
    if (offer.ownerSale) throw new UserError('Owner sales use the fixed team price and cannot be changed here.');
    if (!force && offer.sellerId !== userId) throw new UserError('Only the seller or a server manager can change this price.');
    if (offer.status === 'pending') throw new UserError('A trade is running for this offer. Cancel the trade first, then change the price.');
    if (offer.price === price) throw new UserError('The offer already has this price.');

    const res = await col.findOneAndUpdate(
        { guildId: guild.id, offerId: id, status: 'open' },
        { $set: { price, priceChangedAt: new Date(), priceChangedBy: actorTag } },
        { returnDocument: 'after' }
    );
    if (!res) throw new UserError('This offer just changed. Please try again.');
    await refreshOfferMessage(guild, res);
    await sendLog(guild, db, new EmbedBuilder()
        .setTitle('Spawner price changed')
        .setColor(COLORS.warning)
        .setDescription(`Offer **#${res.offerId}** (${res.amount}x ${res.type}) — seller <@${res.sellerId}>`)
        .addFields(
            { name: 'Old price', value: `${fmt(offer.price)} each`, inline: true },
            { name: 'New price', value: `${fmt(res.price)} each`, inline: true },
            { name: 'Changed by', value: actorTag, inline: true }
        )
        .setFooter({ text: FOOTER })
        .setTimestamp());
    return { offer: res, oldPrice: offer.price };
}

// ---------- owner fixed prices ----------
const priceKey = (type) => `ownerPrices.${type}`;

async function setOwnerPrice(guild, db, typeInput, priceInput, actorTag) {
    requireDb(db);
    const type = normalizeType(typeInput);
    const price = parsePrice(priceInput);
    const before = (await getSettings(db, guild.id)).ownerPrices?.[type];
    await updateSettings(db, guild.id, { [priceKey(type)]: price });
    await sendLog(guild, db, new EmbedBuilder()
        .setTitle('Owner buy price changed')
        .setColor(COLORS.warning)
        .addFields(
            { name: 'Spawner', value: type, inline: true },
            { name: 'Old price', value: before ? `${fmt(before)} each` : 'not set', inline: true },
            { name: 'New price', value: `${fmt(price)} each`, inline: true },
            { name: 'Changed by', value: actorTag, inline: true }
        )
        .setFooter({ text: FOOTER })
        .setTimestamp());
    return { type, price, before };
}

async function removeOwnerPrice(guild, db, typeInput, actorTag) {
    requireDb(db);
    const type = normalizeType(typeInput);
    const s = await getSettings(db, guild.id);
    if (!s.ownerPrices?.[type]) throw new UserError(`The owner does not buy ${type} spawners right now.`);
    await updateSettings(db, guild.id, {}, [priceKey(type)]);
    await sendLog(guild, db, new EmbedBuilder()
        .setTitle('Owner buy price removed')
        .setColor(COLORS.danger)
        .addFields({ name: 'Spawner', value: type, inline: true }, { name: 'Removed by', value: actorTag, inline: true })
        .setFooter({ text: FOOTER })
        .setTimestamp());
    return type;
}

function ownerPricesEmbed(settings) {
    const entries = Object.entries(settings.ownerPrices || {}).sort((a, b) => a[0].localeCompare(b[0]));
    const embed = new EmbedBuilder()
        .setTitle('Owner buy prices')
        .setColor(COLOR)
        .setFooter({ text: `${FOOTER} • fixed prices, set by the team` });
    if (!settings.ownerUserId) {
        return embed.setDescription('The owner has not been set up yet. A manager must run `/owner-setup`.');
    }
    return embed.setDescription(
        entries.length
            ? `<@${settings.ownerUserId}> buys these spawners at a fixed price:\n\n` +
              entries.map(([t, p]) => `**${t}** — ${fmt(p)} each`).join('\n')
            : 'No prices set yet. A manager can add them with `/owner-price set`.'
    );
}

function offersText(list, empty) {
    if (!list.length) return empty;
    return list.map(o =>
        `**[#${o.offerId}](${offerUrl(o)})** — ${o.amount}x **${o.type}** — ${fmt(o.price)} each (${fmt(o.price * o.amount)} total) — <@${o.sellerId}>`
    ).join('\n');
}

// ---------- trade flow ----------
async function findOrCreateTradeCategory(guild) {
    let parent = guild.channels.cache.find(c => c.type === ChannelType.GuildCategory && c.name.toLowerCase() === TRADE_CATEGORY.toLowerCase());
    if (!parent) parent = await guild.channels.create({ name: TRADE_CATEGORY, type: ChannelType.GuildCategory });
    return parent;
}

async function revertToOpen(db, guildId, offerId) {
    return db.collection('spawner_offers').findOneAndUpdate(
        { guildId, offerId, status: 'pending' },
        { $set: { status: 'open', confirmed: [] }, $unset: { buyerId: '', buyerTag: '', tradeChannelId: '' } },
        { returnDocument: 'after' }
    );
}

// Creates the private trade channel (seller + buyer/owner + optional staff role) and posts the trade card.
async function openTradeChannel(guild, db, offer) {
    const s = await getSettings(db, guild.id);
    const staffRole = s.spawnerStaffRoleId ? guild.roles.cache.get(s.spawnerStaffRoleId) : null;
    const parent = await findOrCreateTradeCategory(guild);
    const access = [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory];
    const overwrites = [
        { id: guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
        { id: guild.members.me.id, allow: [...access, PermissionFlagsBits.ManageChannels] },
        { id: offer.sellerId, allow: access },
        { id: offer.buyerId, allow: access }
    ];
    if (staffRole) overwrites.push({ id: staffRole.id, allow: access });

    const channel = await guild.channels.create({
        name: `spawner-${offer.offerId}`,
        type: ChannelType.GuildText,
        parent: parent.id,
        topic: `Spawner trade ${offer.offerId}`,
        permissionOverwrites: overwrites
    });
    await db.collection('spawner_offers').updateOne({ guildId: guild.id, offerId: offer.offerId }, { $set: { tradeChannelId: channel.id } });
    offer.tradeChannelId = channel.id;

    const total = offer.price * offer.amount;
    const intro = offer.ownerSale
        ? `<@${offer.sellerId}> sells **${offer.amount}x ${offer.type} Spawner** to the owner <@${offer.buyerId}> at the fixed team price.`
        : `<@${offer.sellerId}> sells **${offer.amount}x ${offer.type} Spawner** to <@${offer.buyerId}>.`;
    const embed = new EmbedBuilder()
        .setTitle(`Spawner trade #${offer.offerId}`)
        .setColor(COLOR)
        .setDescription(intro)
        .addFields(
            { name: 'Price each', value: fmt(offer.price), inline: true },
            { name: 'Total', value: fmt(total), inline: true },
            { name: 'Seller IGN', value: `\`${offer.ign}\``, inline: true },
            { name: 'How it works', value: '1. Agree on the details here and do the trade in-game.\n2. Only when you **received everything**, click **Confirm**.\n3. When both of you confirmed, the trade is marked as completed.' },
            { name: 'Stay safe', value: 'Never share passwords or leave this channel to trade somewhere else. For big trades ask staff to be a middleman.' }
        )
        .setFooter({ text: FOOTER })
        .setTimestamp();
    await channel.send({
        content: `<@${offer.sellerId}> <@${offer.buyerId}>${staffRole ? ` ${staffRole}` : ''}`,
        embeds: [embed],
        components: [tradeRow(offer.offerId)],
        allowedMentions: { users: [offer.sellerId, offer.buyerId], roles: staffRole ? [staffRole.id] : [] }
    });
    return channel;
}

async function startOwnerSale(interaction, ctx, input) {
    const db = requireDb(ctx.getDb());
    const guild = interaction.guild;
    const col = db.collection('spawner_offers');

    const s = await getSettings(db, guild.id);
    if (!s.ownerUserId) throw new UserError('Selling to the owner is not set up yet. A manager must run `/owner-setup`.');
    if (s.ownerUserId === interaction.user.id) throw new UserError('You are the owner. You cannot sell to yourself.');

    const type = normalizeType(input.type);
    const price = s.ownerPrices?.[type];
    if (!price) {
        const known = Object.keys(s.ownerPrices || {});
        throw new UserError(`The owner does not buy ${type} spawners right now.` + (known.length ? ` Currently bought: ${known.join(', ')}.` : ''));
    }
    const amount = Number(String(input.amount || '').trim());
    if (!Number.isInteger(amount) || amount < 1 || amount > 100000) throw new UserError('Amount must be a whole number between 1 and 100000.');
    const ign = String(input.ign || '').trim();
    if (!/^\.?[A-Za-z0-9_ ]{2,20}$/.test(ign)) throw new UserError('Please enter your exact Minecraft name (IGN).');

    const open = await col.countDocuments({ guildId: guild.id, sellerId: interaction.user.id, status: { $in: ['open', 'pending'] } });
    if (open >= MAX_OPEN_PER_USER) throw new UserError(`You already have ${MAX_OPEN_PER_USER} active offers or trades. Finish one first.`);
    const owner = await guild.members.fetch(s.ownerUserId).catch(() => null);
    if (!owner) throw new UserError('The owner is not on this server. A manager must run `/owner-setup` again.');

    const offer = {
        guildId: guild.id, offerId: crypto.randomBytes(3).toString('hex'),
        sellerId: interaction.user.id, sellerTag: interaction.user.tag, buyerId: owner.id, buyerTag: owner.user.tag,
        type, amount, price, ign, note: '', ownerSale: true, confirmed: [],
        status: 'pending', channelId: null, messageId: null, createdAt: new Date()
    };
    await col.insertOne(offer);
    try {
        return { offer, channel: await openTradeChannel(guild, db, offer) };
    } catch (e) {
        await col.updateOne({ guildId: guild.id, offerId: offer.offerId }, { $set: { status: 'cancelled' } });
        console.error('[SPAWNER]', e);
        throw new UserError('Could not create the trade channel (am I allowed to manage channels?).');
    }
}

async function startTrade(interaction, ctx, offerId) {
    const db = requireDb(ctx.getDb());
    const guild = interaction.guild;
    const col = db.collection('spawner_offers');
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });

    const offer = await col.findOneAndUpdate(
        { guildId: guild.id, offerId, status: 'open', sellerId: { $ne: interaction.user.id } },
        { $set: { status: 'pending', buyerId: interaction.user.id, buyerTag: interaction.user.tag, confirmed: [] } },
        { returnDocument: 'after' }
    );
    if (!offer) {
        const existing = await col.findOne({ guildId: guild.id, offerId });
        if (!existing) throw new UserError('This offer does not exist anymore.');
        if (existing.sellerId === interaction.user.id) throw new UserError('You cannot buy your own offer.');
        throw new UserError('This offer is no longer available.');
    }

    try {
        const seller = await guild.members.fetch(offer.sellerId).catch(() => null);
        if (!seller) {
            const res = await col.findOneAndUpdate({ guildId: guild.id, offerId, status: 'pending' }, { $set: { status: 'cancelled' } }, { returnDocument: 'after' });
            if (res) await refreshOfferMessage(guild, res);
            throw new UserError('The seller left the server. The offer was removed.');
        }

        const channel = await openTradeChannel(guild, db, offer);

        await refreshOfferMessage(guild, offer);
        await interaction.editReply({ content: `Trade channel created: ${channel}` });
    } catch (e) {
        const reopened = await revertToOpen(db, guild.id, offerId);
        if (reopened) await refreshOfferMessage(guild, reopened);
        if (e instanceof UserError) throw e;
        console.error('[SPAWNER]', e);
        throw new UserError('Could not create the trade channel (am I allowed to manage channels?).');
    }
}

async function confirmTrade(interaction, ctx, offerId) {
    const db = requireDb(ctx.getDb());
    const guild = interaction.guild;
    const col = db.collection('spawner_offers');
    const ephemeral = (content) => interaction.reply({ content, flags: MessageFlags.Ephemeral });

    const offer = await col.findOne({ guildId: guild.id, offerId, status: 'pending' });
    if (!offer) { await ephemeral('This trade is no longer active.'); return; }
    if (![offer.sellerId, offer.buyerId].includes(interaction.user.id)) {
        await ephemeral('Only the seller and the buyer can confirm this trade.');
        return;
    }

    const updated = await col.findOneAndUpdate(
        { guildId: guild.id, offerId, status: 'pending' },
        { $addToSet: { confirmed: interaction.user.id } },
        { returnDocument: 'after' }
    );
    if (!updated) { await ephemeral('This trade is no longer active.'); return; }

    if (updated.confirmed.length < 2) {
        await interaction.reply({ content: `${interaction.user} confirmed. Waiting for the other person...`, allowedMentions: { parse: [] } });
        return;
    }

    const done = await col.findOneAndUpdate(
        { guildId: guild.id, offerId, status: 'pending' },
        { $set: { status: 'completed', completedAt: new Date() } },
        { returnDocument: 'after' }
    );
    if (!done) { await ephemeral('This trade was already completed.'); return; }

    await interaction.reply({ embeds: [new EmbedBuilder().setTitle('Trade completed').setColor(COLORS.success).setDescription('Both sides confirmed. This channel will be deleted in 30 seconds.').setFooter({ text: FOOTER })] });
    await refreshOfferMessage(guild, done);
    await sendLog(guild, db, new EmbedBuilder()
        .setTitle(done.ownerSale ? 'Spawner sold to owner' : 'Spawner trade completed')
        .setColor(COLORS.success)
        .setDescription(`**${done.amount}x ${done.type}** — ${fmt(done.price)} each (${fmt(done.price * done.amount)} total)`)
        .addFields(
            { name: 'Seller', value: `<@${done.sellerId}> (\`${done.ign}\`)`, inline: true },
            { name: done.ownerSale ? 'Owner' : 'Buyer', value: `<@${done.buyerId}>`, inline: true },
            { name: 'Offer ID', value: `\`${done.offerId}\``, inline: true }
        )
        .setFooter({ text: FOOTER })
        .setTimestamp());

    try {
        await ranks.announce(guild, done.tradeChannelId, await ranks.recordTrade(guild, db, done));
    } catch (e) {
        console.error('[RANKS]', e.message);
    }
    setTimeout(() => interaction.channel.delete().catch(() => {}), 30 * 1000);
}

async function abortTrade(interaction, ctx, offerId) {
    const db = requireDb(ctx.getDb());
    const guild = interaction.guild;
    const col = db.collection('spawner_offers');
    const ephemeral = (content) => interaction.reply({ content, flags: MessageFlags.Ephemeral });

    const offer = await col.findOne({ guildId: guild.id, offerId, status: 'pending' });
    if (!offer) { await ephemeral('This trade is no longer active.'); return; }
    const isParty = [offer.sellerId, offer.buyerId].includes(interaction.user.id);
    const isStaff = interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels);
    if (!isParty && !isStaff) { await ephemeral('Only the seller, the buyer or staff can cancel this trade.'); return; }

    if (offer.ownerSale) {
        const cancelled = await col.findOneAndUpdate({ guildId: guild.id, offerId, status: 'pending' }, { $set: { status: 'cancelled' } }, { returnDocument: 'after' });
        if (!cancelled) { await ephemeral('This trade is no longer active.'); return; }
        await interaction.reply(`Trade cancelled by ${interaction.user}. This channel will be deleted in 5 seconds.`);
        setTimeout(() => interaction.channel.delete().catch(() => {}), 5000);
        return;
    }

    const reopened = await revertToOpen(db, guild.id, offerId);
    if (!reopened) { await ephemeral('This trade is no longer active.'); return; }

    await interaction.reply(`Trade cancelled by ${interaction.user}. The offer is available again. This channel will be deleted in 5 seconds.`);
    await refreshOfferMessage(guild, reopened);
    setTimeout(() => interaction.channel.delete().catch(() => {}), 5000);
}

// ---------- interaction handler ----------
function sellModal() {
    const field = (id, label, style, required, extra = {}) => new ActionRowBuilder().addComponents(
        Object.entries(extra).reduce((b, [k, v]) => b[k](v), new TextInputBuilder()
            .setCustomId(id).setLabel(label).setStyle(style).setRequired(required))
    );
    return new ModalBuilder().setCustomId(MODAL_SELL).setTitle('Sell a spawner').addComponents(
        field('type', 'Spawner type', TextInputStyle.Short, true, { setPlaceholder: 'e.g. Skeleton, Iron Golem', setMaxLength: 40 }),
        field('amount', 'Amount', TextInputStyle.Short, true, { setPlaceholder: 'e.g. 5', setMaxLength: 6 }),
        field('price', 'Price per spawner', TextInputStyle.Short, true, { setPlaceholder: 'e.g. 500K, 2.5M, 1B', setMaxLength: 20 }),
        field('ign', 'Your Minecraft name (IGN)', TextInputStyle.Short, true, { setPlaceholder: 'Exact in-game name', setMaxLength: 21 }),
        field('note', 'Note (optional)', TextInputStyle.Paragraph, false, { setMaxLength: 300 })
    );
}

function ownerModal() {
    const field = (id, label, placeholder, max) => new ActionRowBuilder().addComponents(
        new TextInputBuilder().setCustomId(id).setLabel(label).setStyle(TextInputStyle.Short).setRequired(true)
            .setPlaceholder(placeholder).setMaxLength(max)
    );
    return new ModalBuilder().setCustomId(MODAL_OWNER).setTitle('Sell to the owner').addComponents(
        field('type', 'Spawner type', 'e.g. Skeleton (see /owner-prices)', 40),
        field('amount', 'Amount', 'e.g. 5', 6),
        field('ign', 'Your Minecraft name (IGN)', 'Exact in-game name', 21)
    );
}

async function respond(interaction, content) {
    const payload = { content, allowedMentions: { parse: [] } };
    if (interaction.deferred) return interaction.editReply(payload);
    if (interaction.replied) return interaction.followUp({ ...payload, flags: MessageFlags.Ephemeral });
    return interaction.reply({ ...payload, flags: MessageFlags.Ephemeral });
}

async function handle(interaction, ctx) {
    const isMine =
        (interaction.isChatInputCommand() && NAMES.has(interaction.commandName)) ||
        (interaction.isButton() && interaction.customId.startsWith('spawner_')) ||
        (interaction.isModalSubmit() && [MODAL_SELL, MODAL_OWNER].includes(interaction.customId));
    if (!isMine) return false;

    if (!interaction.guild) {
        await respond(interaction, 'This only works on servers.');
        return true;
    }
    const guild = interaction.guild;

    try {
        // ----- slash commands -----
        if (interaction.isChatInputCommand()) {
            const db = requireDb(ctx.getDb());

            if (interaction.commandName === 'spawner-setup') {
                const offersChannel = interaction.options.getChannel('offers_channel') || interaction.channel;
                const staffRole = interaction.options.getRole('staff_role');
                const set = { spawnerChannelId: offersChannel.id };
                const unset = [];
                if (staffRole) set.spawnerStaffRoleId = staffRole.id; else unset.push('spawnerStaffRoleId');
                await updateSettings(db, guild.id, set, unset);
                await interaction.reply({
                    content: `Spawner market posted. Offers go to ${offersChannel}${staffRole ? `, staff role: ${staffRole}` : ''}.`,
                    flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] }
                });
                await sendSpawnerPanel(interaction.channel);
                return true;
            }

            if (interaction.commandName === 'spawner-list') {
                const list = await listOffers(db, guild.id, { type: interaction.options.getString('type') || undefined });
                const embed = new EmbedBuilder().setTitle('Open spawner offers').setColor(COLOR)
                    .setDescription(offersText(list, 'No open offers right now.'))
                    .setFooter({ text: list.length ? `${FOOTER} • 10 newest offers` : FOOTER });
                await interaction.reply({ embeds: [embed], flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
                return true;
            }

            if (interaction.commandName === 'spawner-price') {
                const force = interaction.memberPermissions.has(PermissionFlagsBits.ManageGuild);
                const { offer, oldPrice } = await setPrice(guild, db, interaction.options.getString('offer'), interaction.options.getString('price'),
                    { userId: interaction.user.id, force, actorTag: interaction.user.tag });
                await interaction.reply({
                    content: `Offer **#${offer.offerId}**: ${fmt(oldPrice)} → **${fmt(offer.price)}** each (${fmt(offer.price * offer.amount)} total).`,
                    flags: MessageFlags.Ephemeral
                });
                return true;
            }

            if (interaction.commandName === 'owner-setup') {
                const owner = interaction.options.getUser('owner');
                if (owner.bot) throw new UserError('The owner must be a real member.');
                await updateSettings(db, guild.id, { ownerUserId: owner.id });
                await interaction.reply({ content: `${owner} is now the owner who buys spawners. Set prices with \`/owner-price set\`.`, flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
                return true;
            }

            if (interaction.commandName === 'owner-price') {
                // double check on top of the command permission: only managers or the owner
                const s = await getSettings(db, guild.id);
                if (!interaction.memberPermissions.has(PermissionFlagsBits.ManageGuild) && s.ownerUserId !== interaction.user.id) {
                    throw new UserError('Only the team can change these prices.');
                }
                if (interaction.options.getSubcommand() === 'set') {
                    const r = await setOwnerPrice(guild, db, interaction.options.getString('type'), interaction.options.getString('price'), interaction.user.tag);
                    await interaction.reply({ content: `The owner now pays **${fmt(r.price)}** each for **${r.type}** spawners.${r.before ? ` (before: ${fmt(r.before)})` : ''}`, flags: MessageFlags.Ephemeral });
                } else {
                    const type = await removeOwnerPrice(guild, db, interaction.options.getString('type'), interaction.user.tag);
                    await interaction.reply({ content: `The owner no longer buys **${type}** spawners.`, flags: MessageFlags.Ephemeral });
                }
                return true;
            }

            if (interaction.commandName === 'owner-prices') {
                await interaction.reply({ embeds: [ownerPricesEmbed(await getSettings(db, guild.id))], flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
                return true;
            }

            if (interaction.commandName === 'spawner-cancel') {
                const force = interaction.memberPermissions.has(PermissionFlagsBits.ManageGuild);
                const o = await cancelOffer(guild, db, interaction.options.getString('offer'), { userId: interaction.user.id, force });
                await interaction.reply({ content: `Offer **#${o.offerId}** cancelled.`, flags: MessageFlags.Ephemeral });
                return true;
            }
        }

        // ----- panel buttons -----
        if (interaction.isButton()) {
            const id = interaction.customId;
            if (id === BTN_SELL) {
                requireDb(ctx.getDb());
                await interaction.showModal(sellModal());
                return true;
            }
            if (id === BTN_OWNER) {
                requireDb(ctx.getDb());
                await interaction.showModal(ownerModal());
                return true;
            }
            if (id === BTN_BROWSE) {
                const list = await listOffers(requireDb(ctx.getDb()), guild.id);
                const embed = new EmbedBuilder().setTitle('Open spawner offers').setColor(COLOR)
                    .setDescription(offersText(list, 'No open offers right now.')).setFooter({ text: FOOTER });
                await interaction.reply({ embeds: [embed], flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
                return true;
            }
            if (id.startsWith(BTN_BUY)) { await startTrade(interaction, ctx, id.slice(BTN_BUY.length)); return true; }
            if (id.startsWith(BTN_CONFIRM)) { await confirmTrade(interaction, ctx, id.slice(BTN_CONFIRM.length)); return true; }
            if (id.startsWith(BTN_ABORT)) { await abortTrade(interaction, ctx, id.slice(BTN_ABORT.length)); return true; }
            return false;
        }

        // ----- sell form -----
        if (interaction.isModalSubmit()) {
            await interaction.deferReply({ flags: MessageFlags.Ephemeral });
            const f = interaction.fields;
            if (interaction.customId === MODAL_OWNER) {
                const { channel } = await startOwnerSale(interaction, ctx, {
                    type: f.getTextInputValue('type'), amount: f.getTextInputValue('amount'), ign: f.getTextInputValue('ign')
                });
                await interaction.editReply({ content: `Trade channel with the owner created: ${channel}` });
                return true;
            }
            const offer = await createOffer(guild, requireDb(ctx.getDb()), interaction.user, {
                type: f.getTextInputValue('type'),
                amount: f.getTextInputValue('amount'),
                price: f.getTextInputValue('price'),
                ign: f.getTextInputValue('ign'),
                note: f.getTextInputValue('note')
            }, interaction.channel);
            await interaction.editReply({ content: `Your offer **#${offer.offerId}** is online: ${offerUrl(offer)}` });
            return true;
        }
    } catch (e) {
        if (e instanceof UserError) { await respond(interaction, e.message); return true; }
        throw e;
    }
    return false;
}

module.exports = {
    commands, handle, init,
    sendSpawnerPanel, createOffer, listOffers, cancelOffer, setPrice, setOwnerPrice, removeOwnerPrice
};
