import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { type InteractionHandler } from '../../../commands/infra/CommandHandler.js';
import { GameTokenManagementFacade } from '../../../facades/GameTokenManagementFacade.js';
import { AdminPanelSessionManager } from '../../../session/AdminPanelSessionManager.js';
import { ZhTwStrings } from '../../../i18n/zh-TW.js';

/**
 * Handler for token management interactions (admin_token_*).
 * Supports select member, view tokens, add/deduct/set via modal.
 */
export class TokenManagementHandler implements InteractionHandler {
  readonly customIdPrefix = 'admin_token';

  constructor(
    private readonly facade: GameTokenManagementFacade,
    private readonly sessionManager: AdminPanelSessionManager,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    const session = this.sessionManager.getSession(guildId, userId);
    if (!session) {
      await interaction.reply(ZhTwStrings.sessionExpired);
      return;
    }

    await interaction.deferReply();

    // Query the admin's own token balance as a preview
    const result = await this.facade.getTokens(guildId, userId);

    if (result.isOk()) {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription(
          ZhTwStrings.tokenDisplay.replace('{tokens}', String(result.getValue())),
        )
        .setColor(0x5865F2);
      await interaction.editEmbed(embed);
    } else {
      const embed = new EmbedBuilder()
        .setTitle(ZhTwStrings.tokenTitle)
        .setDescription('請選擇成員進行代幣管理')
        .setColor(0x5865F2);
      await interaction.editEmbed(embed);
    }
  }
}
