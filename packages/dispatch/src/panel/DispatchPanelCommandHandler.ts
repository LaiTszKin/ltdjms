import { type DiscordInteraction } from '@ltdjms/shared';
import { type EscortDispatchOrderService } from '../service/index.js';
import {
  buildModeSelectEmbed,
  buildModeSelectActionRow,
  embedViewToApiEmbed,
  buildPanelReplyPayload,
} from './DispatchPanelView.js';
import { buildErrorEmbed } from './DispatchPanelMessageFactory.js';

type DiscordRawHook = {
  reply: (options: Record<string, unknown>) => Promise<unknown>;
};

/**
 * `/dispatch-panel` slash command handler.
 * Opens the escort dispatch management panel for the invoking user.
 */
export class DispatchPanelCommandHandler {
  readonly commandName = 'dispatch-panel';

  constructor(private readonly dispatchOrderService: EscortDispatchOrderService) {}

  async execute(interaction: DiscordInteraction): Promise<void> {
    try {
      // Admin permission check (spec R14.1) — also enforced by
      // defaultMemberPermissions on the command definition.
      // Allow through if user has ADMINISTRATOR permission or is the guild owner.
      if (!interaction.isAdministrator()) {
        await interaction.reply('你沒有權限使用派單面板。');
        return;
      }

      const view = buildModeSelectEmbed();
      const buttons = buildModeSelectActionRow();
      const payload = buildPanelReplyPayload(view, buttons);
      const hook = interaction.getHook() as DiscordRawHook;
      await hook.reply({
        embeds: [payload.embed],
        components: payload.components,
        ephemeral: true,
      });
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      const errorView = buildErrorEmbed(`無法開啟派單面板：${message}`);
      await interaction.replyEmbed(embedViewToApiEmbed(errorView) as never);
    }
  }
}
