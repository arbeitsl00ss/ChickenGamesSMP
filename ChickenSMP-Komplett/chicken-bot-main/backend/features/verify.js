// ==========================================
// ✅ VERIFY / ACCEPT RULES
// Slash command: /verify-setup  +  the "Accept rules" button.
// The role to give is stored inside the button ID, so no database is needed.
// sendVerifyPanel() is also used by the web dashboard.
// ==========================================
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, ActionRowBuilder, ButtonBuilder,
    ButtonStyle, MessageFlags
} = require('discord.js');
const { UserError } = require('../lib/errors');

const BUTTON_PREFIX = 'verify_accept:';
const DEFAULT_RULES = 'Please read the server rules carefully. By clicking the button below you accept them and get access to the rest of the server.';

const commands = [
    new SlashCommandBuilder().setName('verify-setup').setDescription('Post a "accept the rules" panel in this channel')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator).setDMPermission(false)
        .addRoleOption(o => o.setName('role').setDescription('Role members get after accepting').setRequired(true))
        .addStringOption(o => o.setName('rules').setDescription('Rules text (default: a short standard text)').setMaxLength(1500))
        .addStringOption(o => o.setName('title').setDescription('Panel title').setMaxLength(100)),
];

// Throws a UserError if the bot cannot hand out this role.
function checkRole(guild, roleId) {
    const role = guild.roles.cache.get(roleId);
    if (!role) throw new UserError('Role not found.');
    if (role.managed || role.id === guild.id) throw new UserError('This role cannot be assigned.');
    if (!guild.members.me.permissions.has(PermissionFlagsBits.ManageRoles)) {
        throw new UserError('I need the "Manage Roles" permission.');
    }
    if (role.position >= guild.members.me.roles.highest.position) {
        throw new UserError('That role is above my highest role. Move my role higher in the server settings.');
    }
    return role;
}

async function sendVerifyPanel(channel, role, rules, title) {
    const embed = new EmbedBuilder()
        .setTitle(title || 'Verification ✅')
        .setDescription((rules || DEFAULT_RULES).slice(0, 4000))
        .setColor(require('../lib/brand').COLORS.success)
        .setFooter({ text: 'barbar_354' });
    const row = new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId(BUTTON_PREFIX + role.id).setLabel('Accept rules').setEmoji('✅').setStyle(ButtonStyle.Success)
    );
    await channel.send({ embeds: [embed], components: [row] });
}

async function handle(interaction) {
    if (interaction.isChatInputCommand() && interaction.commandName === 'verify-setup') {
        try {
            const role = checkRole(interaction.guild, interaction.options.getRole('role').id);
            await interaction.reply({ content: `✅ Posting the verification panel (role: ${role}).`, flags: MessageFlags.Ephemeral, allowedMentions: { parse: [] } });
            await sendVerifyPanel(interaction.channel, role, interaction.options.getString('rules'), interaction.options.getString('title'));
        } catch (e) {
            if (!(e instanceof UserError)) throw e;
            await interaction.reply({ content: `❌ ${e.message}`, flags: MessageFlags.Ephemeral });
        }
        return true;
    }

    if (interaction.isButton() && interaction.customId.startsWith(BUTTON_PREFIX)) {
        const reply = (content) => interaction.reply({ content, flags: MessageFlags.Ephemeral });
        try {
            const role = checkRole(interaction.guild, interaction.customId.slice(BUTTON_PREFIX.length));
            if (interaction.member.roles.cache.has(role.id)) {
                await reply('✅ You are already verified.');
                return true;
            }
            await interaction.member.roles.add(role.id, 'Accepted the rules');
            await reply('✅ Thank you! You accepted the rules and now have access to the server.');
        } catch (e) {
            if (!(e instanceof UserError)) throw e;
            await reply('❌ Verification is not set up correctly. Please tell a team member.');
        }
        return true;
    }

    return false;
}

module.exports = { commands, handle, checkRole, sendVerifyPanel };
