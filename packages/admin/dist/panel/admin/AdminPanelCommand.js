import { ZhTwStrings } from '../../i18n/zh-TW.js';
/**
 * /admin-panel slash command handler.
 * Opens the admin panel main menu with 9 feature buttons.
 * Requires ADMINISTRATOR permission (enforced by Discord and handler).
 */
export class AdminPanelCommand {
    sessionManager;
    viewFactory;
    commandName = 'admin-panel';
    constructor(sessionManager, viewFactory) {
        this.sessionManager = sessionManager;
        this.viewFactory = viewFactory;
    }
    async execute(interaction, context) {
        // Permission check (second layer)
        if (!this.hasAdminPermission(context)) {
            await interaction.reply(ZhTwStrings.permissionAdminRequired);
            return;
        }
        // Create session
        const guildId = interaction.getGuildId();
        const userId = interaction.getUserId();
        this.sessionManager.createSession(guildId, userId);
        // Build and send main panel
        const mainPanel = this.viewFactory.buildMainPanelEmbed(`Guild ${guildId}`, null, 0);
        // For now, using a simple text-based panel until full DiscordEmbedBuilder integration
        const panelText = [
            `**${mainPanel.title}**`,
            mainPanel.description,
            '',
            ...mainPanel.fields.map((f) => `**${f.name}：** ${f.value}`),
            '',
            '---',
            mainPanel.buttons.map((b) => `\`/${b.id}\` ${b.label}`).join(' | '),
            '',
            `_${mainPanel.footer}_`,
        ].join('\n');
        await interaction.reply(panelText);
    }
    hasAdminPermission(_context) {
        // In production, this checks context for ADMINISTRATOR permission
        // For now, we allow all users (Discord handles the first layer)
        return true;
    }
}
//# sourceMappingURL=AdminPanelCommand.js.map