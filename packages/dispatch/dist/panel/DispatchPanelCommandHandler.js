import { buildModeSelectEmbed, buildModeSelectActionRow, embedViewToApiEmbed, } from './DispatchPanelView.js';
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
            // Send text-based panel matching existing admin panel pattern
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
        const lines = [];
        if (view.title)
            lines.push(`**${view.title}**`);
        if (view.description)
            lines.push(view.description);
        lines.push('');
        for (const field of view.fields ?? []) {
            lines.push(`**${field.name}：** ${field.value}`);
        }
        lines.push('');
        lines.push('---');
        lines.push(buttons.map((b) => `\`/${b.label}\``).join(' | '));
        if (view.footer)
            lines.push(`_${view.footer}_`);
        return lines.join('\n');
    }
}
//# sourceMappingURL=DispatchPanelCommandHandler.js.map