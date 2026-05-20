import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { type EscortDispatchOrderService } from '../service/index.js';
import {
  buildModeSelectEmbed,
  buildModeSelectActionRow,
  embedViewToApiEmbed,
  buttonsToComponents,
  formatPanelText,
} from './DispatchPanelView.js';
import { buildErrorEmbed } from './DispatchPanelMessageFactory.js';

/**
 * `/dispatch-panel` slash command handler.
 * Opens the escort dispatch management panel for the invoking user.
 */
export class DispatchPanelCommandHandler {
  readonly commandName = 'dispatch-panel';

  constructor(private readonly dispatchOrderService: EscortDispatchOrderService) {}

  async execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void> {
    try {
      // Admin permission check (spec R14.1) — also enforced by
      // defaultMemberPermissions on the command definition.
      const memberPermissions = (interaction as unknown as { memberPermissions?: string }).memberPermissions;
      if (memberPermissions && (BigInt(memberPermissions) & 0x8n) === 0n) {
        await interaction.reply('你沒有權限使用派單面板。');
        return;
      }

      const view = buildModeSelectEmbed();
      const buttons = buildModeSelectActionRow();
      const panelText = this.formatPanelText(view, buttons);
      await interaction.reply(panelText);
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      const errorView = buildErrorEmbed(`無法開啟派單面板：${message}`);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
    }
  }

  private formatPanelText(
    view: ReturnType<typeof buildModeSelectEmbed>,
    buttons: ReturnType<typeof buildModeSelectActionRow>,
  ): string {
    return formatPanelText(view, buttons);
  }
}
