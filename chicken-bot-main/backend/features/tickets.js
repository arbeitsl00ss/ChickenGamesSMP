// ==========================================
// 🎫 SUPPORT TICKETS (with categories)
// Slash commands: /ticket-setup /close  +  category select menu and buttons.
// Categories and the staff role are stored inside the menu's custom ID / options,
// so no database is needed. sendTicketPanel() is also used by the web dashboard.
// ==========================================
const {
    SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, ActionRowBuilder, ButtonBuilder,
    StringSelectMenuBuilder, ButtonStyle, ChannelType, MessageFlags
} = require('discord.js');
const { sendLog } = require('../lib/settings');

const SELECT_PREFIX = 'ticket_cat:';
const DEFAULT_CATEGORIES = ['Support', 'Report a user', 'Question', 'Other'];

const commands = [
    new SlashCommandBuilder().setName('ticket-setup').setDescription('Post the support ticket panel in this channel')
        .setDefaultMemberPermissions(PermissionFlagsBits.Administrator).setDMPermission(false)
        .addStringOption(o => o.setName('categories').setDescription('Ticket categories, separated by commas (max 25), e.g. Support, Report, Application'))
        .addRoleOption(o => o.setName('staff_role').setDescription('Role that can see and is pinged in every ticket')),
    new SlashCommandBuilder().setName('close').setDescription('Close the current support ticket')
        .setDMPermission(false),
];

// "Support, Report, Application" or one per line -> clean unique list (max 25)
function parseCategories(input) {
    const raw = Array.isArray(input) ? input : String(input || '').split(/[,\n]/);
    const unique = [...new Set(raw.map((s) => String(s).trim().slice(0, 80)).filter(Boolean))].slice(0, 25);
    return unique.length ? unique : DEFAULT_CATEGORIES;
}

async function sendTicketPanel(channel, { categories, staffRoleId } = {}) {
    const list = parseCategories(categories);
    const embed = new EmbedBuilder()
        .setTitle('Chicken Games Support 🎫')
        .setDescription('Need help or have a question? Choose a category from the menu below to open a private ticket with the team.')
        .setColor(require('../lib/brand').COLORS.primary);
    const menu = new StringSelectMenuBuilder()
        .setCustomId(SELECT_PREFIX + (staffRoleId || '0'))
        .setPlaceholder('Select a ticket category...')
        .addOptions(list.map((c) => ({ label: c, value: c })));
    await channel.send({ embeds: [embed], components: [new ActionRowBuilder().addComponents(menu)] });
}

const closeRow = () => new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('ticket_close').setLabel('Close ticket').setEmoji('🔒').setStyle(ButtonStyle.Danger)
);

async function closeTicket(interaction, ctx) {
    if (!interaction.channel.name.startsWith('ticket-')) {
        await interaction.reply({ content: '❌ This only works in ticket channels!', flags: MessageFlags.Ephemeral });
        return;
    }
    await interaction.reply('🔒 This ticket will be closed and deleted in 5 seconds...');

    const logEmbed = new EmbedBuilder()
        .setTitle('Ticket closed 🔒')
        .setDescription(`The ticket **${interaction.channel.name}** was closed by ${interaction.user.tag}.`)
        .setColor(require('../lib/brand').COLORS.danger)
        .setTimestamp();
    await sendLog(interaction.guild, ctx.getDb(), logEmbed);

    setTimeout(() => interaction.channel.delete().catch(() => {}), 5000);
}

async function openTicket(interaction, ctx, category, staffRoleId) {
    const guild = interaction.guild;
    await interaction.reply({ content: '⏳ Creating your ticket...', flags: MessageFlags.Ephemeral });

    try {
        let parent = guild.channels.cache.find(c => c.name.toLowerCase() === 'tickets' && c.type === ChannelType.GuildCategory);
        if (!parent) {
            parent = await guild.channels.create({ name: 'TICKETS', type: ChannelType.GuildCategory });
        }

        // one open ticket per member (the member's ID is stored in the channel topic)
        const existing = guild.channels.cache.find(c => c.parentId === parent.id && c.topic === interaction.user.id);
        if (existing) {
            await interaction.editReply({ content: `❌ You already have an open ticket: ${existing}` });
            return;
        }

        const staffRole = staffRoleId && staffRoleId !== '0' ? guild.roles.cache.get(staffRoleId) : null;
        const access = [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory];
        const overwrites = [
            { id: guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
            { id: guild.members.me.id, allow: [...access, PermissionFlagsBits.ManageChannels] },
            { id: interaction.user.id, allow: access }
        ];
        if (staffRole) overwrites.push({ id: staffRole.id, allow: access });

        const ticketChannel = await guild.channels.create({
            name: `ticket-${interaction.user.username}`,
            type: ChannelType.GuildText,
            parent: parent.id,
            topic: interaction.user.id,
            permissionOverwrites: overwrites
        });

        const embed = new EmbedBuilder()
            .setTitle(`Ticket opened 🎫 – ${category}`)
            .setDescription(`Welcome ${interaction.user},\nplease describe your issue as precisely as possible. The team will answer soon.\n\nUse the button below or **\`/close\`** to close this ticket.`)
            .addFields({ name: 'Category', value: category, inline: true }, { name: 'Opened by', value: interaction.user.tag, inline: true })
            .setColor(require('../lib/brand').COLORS.primary)
            .setTimestamp();

        await ticketChannel.send({
            content: staffRole ? `${staffRole}` : undefined,
            embeds: [embed],
            components: [closeRow()],
            allowedMentions: { roles: staffRole ? [staffRole.id] : [] }
        });
        await interaction.editReply({ content: `✅ Ticket created: ${ticketChannel}` });

        await sendLog(guild, ctx.getDb(), new EmbedBuilder()
            .setTitle('Ticket opened 🎫')
            .setDescription(`${interaction.user.tag} opened ${ticketChannel} (category: **${category}**).`)
            .setColor(require('../lib/brand').COLORS.primary).setTimestamp());
    } catch (err) {
        console.error('[TICKET]', err);
        await interaction.editReply({ content: '❌ Could not create the ticket channel (am I allowed to manage channels?).' });
    }
}

async function handle(interaction, ctx) {
    if (interaction.isChatInputCommand() && interaction.commandName === 'ticket-setup') {
        if (!interaction.member.permissions.has(PermissionFlagsBits.Administrator)) {
            await interaction.reply({ content: '❌ Only administrators can do this!', flags: MessageFlags.Ephemeral });
            return true;
        }
        const role = interaction.options.getRole('staff_role');
        await interaction.reply({ content: 'Posting the ticket panel...', flags: MessageFlags.Ephemeral });
        await sendTicketPanel(interaction.channel, {
            categories: interaction.options.getString('categories'),
            staffRoleId: role?.id
        });
        return true;
    }

    if (interaction.isChatInputCommand() && interaction.commandName === 'close') {
        await closeTicket(interaction, ctx);
        return true;
    }

    if (interaction.isButton() && interaction.customId === 'ticket_close') {
        await closeTicket(interaction, ctx);
        return true;
    }

    // category chosen in the menu
    if (interaction.isStringSelectMenu() && interaction.customId.startsWith(SELECT_PREFIX)) {
        const staffRoleId = interaction.customId.slice(SELECT_PREFIX.length);
        await openTicket(interaction, ctx, interaction.values[0], staffRoleId);
        // reset the menu so the same category can be picked again
        interaction.message.edit({ components: interaction.message.components }).catch(() => {});
        return true;
    }

    // panels from older versions (single button)
    if (interaction.isButton() && interaction.customId === 'open_ticket') {
        await openTicket(interaction, ctx, 'General', '0');
        return true;
    }

    return false;
}

module.exports = { commands, handle, sendTicketPanel, parseCategories };
