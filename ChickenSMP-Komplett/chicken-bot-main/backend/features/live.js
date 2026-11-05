// ==========================================
// LIVE ANNOUNCEMENTS
// Watches a Twitch or YouTube channel and posts an announcement (with a ping) when it goes live.
// Commands: /live-setup /live-test /live-disable
// Env (Render): TWITCH_CLIENT_ID + TWITCH_CLIENT_SECRET for Twitch, YOUTUBE_API_KEY for YouTube.
// ==========================================
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle,
    ChannelType, MessageFlags
} = require('discord.js');
const { UserError } = require('../lib/errors');
const { getSettings, updateSettings } = require('../lib/settings');
const { COLORS, FOOTER } = require('../lib/brand');

const POLL_MS = 90 * 1000;

const commands = [
    new SlashCommandBuilder().setName('live-setup').setDescription('Announce when a streamer goes live')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator).setDMPermission(false)
        .addStringOption(o => o.setName('platform').setDescription('Where does he stream?').setRequired(true)
            .addChoices({ name: 'Twitch', value: 'twitch' }, { name: 'YouTube', value: 'youtube' }))
        .addStringOption(o => o.setName('channel_name').setDescription('Twitch login name, or YouTube @handle / channel ID (UC...)').setRequired(true))
        .addChannelOption(o => o.setName('announce_channel').setDescription('Where the announcement is posted').setRequired(true)
            .addChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement))
        .addStringOption(o => o.setName('ping').setDescription('Who gets pinged (default: everyone)')
            .addChoices({ name: '@everyone', value: 'everyone' }, { name: 'A role', value: 'role' }, { name: 'Nobody', value: 'none' }))
        .addRoleOption(o => o.setName('role').setDescription('Role to ping when ping = A role'))
        .addStringOption(o => o.setName('display_name').setDescription('Name shown in the announcement (default: channel name)')),

    new SlashCommandBuilder().setName('live-test').setDescription('Post a test announcement without pinging anyone')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator).setDMPermission(false),

    new SlashCommandBuilder().setName('live-disable').setDescription('Turn the live announcements off')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator).setDMPermission(false)
];
const NAMES = new Set(commands.map(c => c.name));

// ---------- Twitch ----------
let twitchToken = null; // { value, expires }
async function twitchHeaders() {
    const id = process.env.TWITCH_CLIENT_ID, secret = process.env.TWITCH_CLIENT_SECRET;
    if (!id || !secret) throw new UserError('TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET are missing in Render (Environment).');
    if (!twitchToken || twitchToken.expires < Date.now() + 60000) {
        const r = await fetch('https://id.twitch.tv/oauth2/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ client_id: id, client_secret: secret, grant_type: 'client_credentials' }),
            signal: AbortSignal.timeout(15000)
        });
        if (!r.ok) throw new Error(`Twitch token request failed (${r.status})`);
        const j = await r.json();
        twitchToken = { value: j.access_token, expires: Date.now() + j.expires_in * 1000 };
    }
    return { 'Client-Id': id, Authorization: `Bearer ${twitchToken.value}` };
}

async function checkTwitch(login) {
    const r = await fetch(`https://api.twitch.tv/helix/streams?user_login=${encodeURIComponent(login)}`, {
        headers: await twitchHeaders(), signal: AbortSignal.timeout(15000)
    });
    if (r.status === 401) twitchToken = null;
    if (!r.ok) throw new Error(`Twitch API ${r.status}`);
    const s = (await r.json()).data?.[0];
    if (!s || s.type !== 'live') return null;
    return {
        id: s.id,
        title: s.title || 'Live now',
        game: s.game_name || null,
        viewers: s.viewer_count,
        url: `https://twitch.tv/${s.user_login}`,
        name: s.user_name,
        image: s.thumbnail_url.replace('{width}', '1280').replace('{height}', '720') + `?t=${Date.now()}`
    };
}

// ---------- YouTube ----------
const ytChannelCache = new Map();
async function ytGet(path, params) {
    if (!process.env.YOUTUBE_API_KEY) throw new UserError('YOUTUBE_API_KEY is missing in Render (Environment).');
    const url = `https://www.googleapis.com/youtube/v3/${path}?${new URLSearchParams({ ...params, key: process.env.YOUTUBE_API_KEY })}`;
    const r = await fetch(url, { signal: AbortSignal.timeout(15000) });
    if (r.status === 404) return null;
    if (!r.ok) throw new Error(`YouTube API ${r.status}`);
    return r.json();
}

async function youtubeChannelId(input) {
    const v = String(input).trim();
    if (/^UC[\w-]{22}$/.test(v)) return v;
    if (ytChannelCache.has(v)) return ytChannelCache.get(v);
    const handle = v.startsWith('@') ? v : `@${v}`;
    const j = await ytGet('channels', { part: 'id', forHandle: handle });
    const id = j?.items?.[0]?.id;
    if (!id) throw new UserError(`YouTube channel ${handle} was not found.`);
    ytChannelCache.set(v, id);
    return id;
}

// Uses the channel's "live streams" playlist (UULV...) = 2 quota units per check.
async function checkYoutube(input) {
    const channelId = await youtubeChannelId(input);
    const list = await ytGet('playlistItems', { part: 'contentDetails', playlistId: 'UULV' + channelId.slice(2), maxResults: '5' });
    const ids = (list?.items || []).map(i => i.contentDetails.videoId);
    if (!ids.length) return null;
    const vids = await ytGet('videos', { part: 'snippet,liveStreamingDetails,statistics', id: ids.join(',') });
    const v = (vids?.items || []).find(x => x.liveStreamingDetails?.actualStartTime && !x.liveStreamingDetails?.actualEndTime);
    if (!v) return null;
    return {
        id: v.id,
        title: v.snippet.title,
        game: null,
        viewers: v.liveStreamingDetails.concurrentViewers ? Number(v.liveStreamingDetails.concurrentViewers) : null,
        url: `https://www.youtube.com/watch?v=${v.id}`,
        name: v.snippet.channelTitle,
        image: (v.snippet.thumbnails?.maxres || v.snippet.thumbnails?.high || {}).url || null
    };
}

const check = (platform, name) => (platform === 'twitch' ? checkTwitch(name) : checkYoutube(name));

// ---------- announcement ----------
function announcement(s, stream, { test = false } = {}) {
    const display = s.liveDisplayName || stream.name;
    const embed = new EmbedBuilder()
        .setColor(s.livePlatform === 'twitch' ? COLORS.live : COLORS.danger)
        .setAuthor({ name: `${display} is live now` })
        .setTitle(stream.title.slice(0, 250))
        .setURL(stream.url)
        .setFooter({ text: `${FOOTER} • ${s.livePlatform === 'twitch' ? 'Twitch' : 'YouTube'}` })
        .setTimestamp();
    if (stream.game) embed.addFields({ name: 'Playing', value: stream.game, inline: true });
    if (stream.viewers != null) embed.addFields({ name: 'Viewers', value: String(stream.viewers), inline: true });
    if (stream.image) embed.setImage(stream.image);
    if (test) embed.setDescription('This is a test announcement. Nobody was pinged.');

    let content = '';
    let allowedMentions = { parse: [] };
    if (!test) {
        if (s.livePing === 'everyone') { content = '@everyone'; allowedMentions = { parse: ['everyone'] }; }
        else if (s.livePing === 'role' && s.liveRoleId) { content = `<@&${s.liveRoleId}>`; allowedMentions = { roles: [s.liveRoleId] }; }
        content = content ? `${content} **${display}** is live!` : '';
    }
    const row = new ActionRowBuilder().addComponents(
        new ButtonBuilder().setLabel('Watch now').setStyle(ButtonStyle.Link).setURL(stream.url)
    );
    return { content: content || undefined, embeds: [embed], components: [row], allowedMentions };
}

// ---------- polling ----------
async function poll(client, getDb) {
    const db = getDb();
    if (!db) return;
    const configs = await db.collection('settings').find({ liveEnabled: true }).toArray();
    for (const s of configs) {
        try {
            const guild = client.guilds.cache.get(s.guildId);
            const channel = guild?.channels.cache.get(s.liveChannelId);
            if (!channel) continue;
            const stream = await check(s.livePlatform, s.liveChannelName);
            if (!stream || stream.id === s.liveLastStreamId) continue;
            // remember first so a failed send can never cause a ping loop
            await updateSettings(db, s.guildId, { liveLastStreamId: stream.id });
            await channel.send(announcement(s, stream));
            console.log(`[LIVE] Announced ${stream.name} (${s.livePlatform}) in ${guild.name}`);
        } catch (e) {
            console.error(`[LIVE] Check failed for guild ${s.guildId}:`, e.message);
        }
    }
}

let timer = null;
function start(client, getDb) {
    if (timer) return;
    timer = setInterval(() => poll(client, getDb).catch(e => console.error('[LIVE]', e.message)), POLL_MS);
    console.log(`[LIVE] Watching live status every ${POLL_MS / 1000}s.`);
}

// ---------- commands ----------
async function handle(interaction, ctx) {
    if (!(interaction.isChatInputCommand() && NAMES.has(interaction.commandName))) return false;
    const guild = interaction.guild;
    const db = ctx.getDb();
    const reply = (content) => interaction.reply({ content, flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
    try {
        if (!db) throw new UserError('The database is not connected (check MONGODB_URI in Render).');

        if (interaction.commandName === 'live-disable') {
            await updateSettings(db, guild.id, { liveEnabled: false });
            await reply('Live announcements are turned off.');
            return true;
        }

        if (interaction.commandName === 'live-test') {
            const s = await getSettings(db, guild.id);
            if (!s.liveChannelId) throw new UserError('Run `/live-setup` first.');
            const channel = guild.channels.cache.get(s.liveChannelId);
            if (!channel) throw new UserError('The announcement channel does not exist anymore.');
            await channel.send(announcement(s, {
                id: 'test', title: 'Example stream title', game: 'DonutSMP', viewers: 123,
                url: s.livePlatform === 'twitch' ? `https://twitch.tv/${s.liveChannelName}` : 'https://www.youtube.com',
                name: s.liveChannelName, image: null
            }, { test: true }));
            await reply(`Test announcement posted in ${channel}.`);
            return true;
        }

        // live-setup
        const platform = interaction.options.getString('platform');
        const name = interaction.options.getString('channel_name').trim().replace(/^https?:\/\/(www\.)?(twitch\.tv|youtube\.com)\//i, '').replace(/\/.*$/, '');
        const channel = interaction.options.getChannel('announce_channel');
        const ping = interaction.options.getString('ping') || 'everyone';
        const role = interaction.options.getRole('role');
        if (ping === 'role' && !role) throw new UserError('Please choose the role to ping in the `role` option.');
        if (!/^@?[\w.-]{2,100}$/.test(name)) throw new UserError('That channel name does not look valid.');

        const perms = channel.permissionsFor(guild.members.me);
        if (!perms?.has([PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.EmbedLinks])) {
            throw new UserError(`I am not allowed to post in ${channel}.`);
        }
        if (ping === 'everyone' && !perms.has(PermissionFlagsBits.MentionEveryone)) {
            throw new UserError(`I need the "Mention @everyone" permission in ${channel} to ping everyone.`);
        }

        await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        let current = null;
        try {
            current = await check(platform, name); // also validates keys and the channel name
        } catch (e) {
            if (e instanceof UserError) throw e;
            throw new UserError(`Could not reach ${platform === 'twitch' ? 'Twitch' : 'YouTube'}: ${e.message}`);
        }
        const set = {
            liveEnabled: true, livePlatform: platform, liveChannelName: name, liveChannelId: channel.id,
            livePing: ping, liveLastStreamId: current ? current.id : null // no ping for a stream that is already running
        };
        const unset = [];
        if (ping === 'role') set.liveRoleId = role.id; else unset.push('liveRoleId');
        const display = interaction.options.getString('display_name');
        if (display) set.liveDisplayName = display.slice(0, 60); else unset.push('liveDisplayName');
        await updateSettings(db, guild.id, set, unset);

        await interaction.editReply({
            content: `Live announcements are on for **${display || name}** (${platform}) in ${channel}. Ping: ${ping === 'everyone' ? '@everyone' : ping === 'role' ? `${role}` : 'nobody'}.` +
                (current ? '\nThe stream is live right now. The next stream will be announced.' : '') +
                '\nUse `/live-test` to preview the message.',
            allowedMentions: { parse: [] }
        });
        return true;
    } catch (e) {
        if (!(e instanceof UserError)) throw e;
        if (interaction.deferred) await interaction.editReply({ content: e.message });
        else await reply(e.message);
        return true;
    }
}

module.exports = { commands, handle, start, poll, announcement };
