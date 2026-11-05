// Per-server settings (log channel, welcome message) + moderation log helper.
const { ChannelType } = require('discord.js');

async function init(db) {
    await db.collection('settings').createIndex({ guildId: 1 }, { unique: true });
}

async function getSettings(db, guildId) {
    if (!db) return {};
    return (await db.collection('settings').findOne({ guildId })) || {};
}

async function updateSettings(db, guildId, set = {}, unset = []) {
    const update = { $set: { guildId, ...set } };
    if (unset.length) update.$unset = Object.fromEntries(unset.map((k) => [k, '']));
    await db.collection('settings').updateOne({ guildId }, update, { upsert: true });
}

// Sends an embed to the configured log channel (or any text channel with "log" in its name).
async function sendLog(guild, db, embed) {
    try {
        const s = await getSettings(db, guild.id);
        let channel = s.logChannelId ? guild.channels.cache.get(s.logChannelId) : null;
        if (!channel) {
            channel = guild.channels.cache.find((c) => c.type === ChannelType.GuildText && c.name.includes('log'));
        }
        if (channel) await channel.send({ embeds: [embed] });
    } catch (e) {
        console.error('[LOG] Could not send log message:', e.message);
    }
}

module.exports = { init, getSettings, updateSettings, sendLog };
