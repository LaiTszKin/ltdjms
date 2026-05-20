import { buildModeSelectEmbed, buildModeSelectActionRow, embedViewToApiEmbed, formatPanelText, } from './DispatchPanelView.js';
import { buildErrorEmbed } from './DispatchPanelMessageFactory.js';
/**
 * `/dispatch-panel` slash command handler.
 * Opens the escort dispatch management panel for the invoking user.
 */
export class DispatchPanelCommandHandler {
    dispatchOrderService;
    commandName = 'dispatch-panel';
    constructor(dispatchOrderService) {
        this.dispatchOrderService = dispatchOrderService;
    }
    async execute(interaction, _context) {
        try {
            // Admin permission check (spec R14.1) — also enforced by
            // defaultMemberPermissions on the command definition.
            const memberPermissions = interaction.memberPermissions;
            if (memberPermissions && (BigInt(memberPermissions) & 0x8n) === 0n) {
                await interaction.reply('你沒有權限使用派單面板。');
                return;
            }
            const view = buildModeSelectEmbed();
            const buttons = buildModeSelectActionRow();
            const panelText = this.formatPanelText(view, buttons);
            await interaction.reply(panelText);
        }
        catch (e) {
            const message = e instanceof Error ? e.message : String(e);
            const errorView = buildErrorEmbed(`無法開啟派單面板：${message}`);
            await interaction.replyEmbed(embedViewToApiEmbed(errorView));
        }
    }
    formatPanelText(view, buttons) {
        return formatPanelText(view, buttons);
    }
}
//# sourceMappingURL=DispatchPanelCommandHandler.js.map