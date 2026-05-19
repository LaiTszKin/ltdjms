import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { AIConfigManagementFacade } from '../../../facades/AIConfigManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Handler for AI agent config interactions (admin_aiagent_*).
 * Supports enable/disable/remove agent mode on channels.
 */
export class AIAgentConfigHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_aiagent';

  constructor(
    private readonly facade: AIConfigManagementFacade,
    private readonly sessionManager: AdminPanelSessionManager,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    _context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await interaction.reply('AI Agent 設定功能');
  }
}
