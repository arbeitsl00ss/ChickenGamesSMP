// ==========================================
// 🧰 UTILITY: /clear and /server-stats
// ==========================================
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, MessageFlags
} = require('discord.js');

const commands = [
    new SlashCommandBuilder().setName('clear').setDescription('Delete a number of messages in this channel')
        .setDefaultMemberPermissions(PermissionFlagsBits.ManageMessages).setDMPermission(false)
        .addIntegerOption(o => o.setName('amount').setDescription('How many messages? (1-100)')
            .setMinValue(1).setMaxValue(100).setRequired(true)),
    new SlashCommandBuilder().setName('server-stats').setDescription('Show live statistics of this server')
        .setDMPermission(false),
];

async function handle(interaction) {
    if (!interaction.isChatInputCommand()) return false;

    if (interaction.commandName === 'clear') {
        if (!interaction.member.permissions.has(PermissionFlagsBits.ManageMessages)) {
            await interaction.reply({ content: '❌ You do not have permission!', flags: MessageFlags.Ephemeral });
            return true;
        }
        const amount = interaction.options.getInteger('amount');
        const deleted = await interaction.channel.bulkDelete(amount, true);
        await interaction.reply({ content: `🧹 Deleted ${deleted.size} messages.`, flags: MessageFlags.Ephemeral });
        return true;
    }

    if (interaction.commandName === 'server-stats') {
        const guild = interaction.guild;
        await guild.members.fetch().catch(() => {});
        const total = guild.memberCount;
        const bots = guild.members.cache.filter(m => m.user.bot).size;
        const embed = new EmbedBuilder()
            .setTitle(`Server stats for ${guild.name}`)
            .addFields(
                { name: '👥 Total members', value: `${total}`, inline: true },
                { name: '🧑 Humans', value: `${total - bots}`, inline: true },
                { name: '🤖 Bots', value: `${bots}`, inline: true }
            )
            .setColor(require('../lib/brand').COLORS.success)
            .setTimestamp();
        await interaction.reply({ embeds: [embed] });
        return true;
    }

    return false;
}

module.exports = { commands, handle };
