import { EmbedBuilder, ActionRowBuilder, ButtonBuilder } from 'discord.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';
import { PermissionFlagsBits } from 'discord.js';
/**
 * /admin-panel slash command handler.
 * Opens the admin panel main menu with 9 feature buttons.
 * Requires ADMINISTRATOR permission (enforced by Discord and handler).
 */
export class AdminPanelCommand {
    sessionManager;
    viewFactory;
    currencyFacade;
    commandName = 'admin-panel';
    constructor(sessionManager, viewFactory, currencyFacade) {
        this.sessionManager = sessionManager;
        this.viewFactory = viewFactory;
        this.currencyFacade = currencyFacade;
    }
    async execute(interaction, context) {
        // Permission check (second layer)
        if (!this.hasAdminPermission(interaction)) {
            await interaction.reply(ZhTwStrings.permissionAdminRequired);
            return;
        }
        // Create session
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        this.sessionManager.createSession(guildId, userId);
        // Query real currency config
        const configResult = await this.currencyFacade.getConfig(guildId);
        const currencyConfig = configResult.isOk() ? configResult.getValue() : null;
        // TODO(P1-37): Query active dispatch order count from a dispatch service
        // (e.g., EscortOrderService) once it is available. Currently hardcoded to 0.
        const dispatchCount = 0;
        const mainPanel = this.viewFactory.buildMainPanelEmbed(`Guild ${guildId}`, currencyConfig, dispatchCount);
        const embed = new EmbedBuilder()
            .setTitle(mainPanel.title)
            .setDescription(mainPanel.description)
            .setColor(mainPanel.color)
            .setFooter({ text: mainPanel.footer });
        for (const field of mainPanel.fields) {
            embed.addFields({ name: field.name, value: field.value, inline: field.inline });
        }
        // Convert button data to discord.js ActionRow components
        const rows = [];
        const buttons = mainPanel.buttons.map((b) => new ButtonBuilder()
            .setCustomId(b.id)
            .setLabel(b.label)
            .setStyle(b.style)
            .setDisabled(b.disabled));
        // Split into rows of 5 (max 5 buttons per ActionRow)
        for (let i = 0; i < buttons.length; i += 5) {
            rows.push(new ActionRowBuilder().addComponents(buttons.slice(i, i + 5)));
        }
        // Use the raw discord.js interaction to send embed with components
        const raw = interaction.getHook();
        await raw.reply({ embeds: [embed], components: rows });
    }
    hasAdminPermission(interaction) {
        try {
            const raw = interaction.getHook();
            const userId = String(interaction.getUserId());
            // Check ADMINISTRATOR permission
            if (raw.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
                return true;
            }
            // Check guild owner
            if (raw.guild?.ownerId === userId) {
                return true;
            }
        }
        catch {
            // If we cannot access the raw interaction, deny access
        }
        return false;
    }
}
//# sourceMappingURL=AdminPanelCommand.js.map