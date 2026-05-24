import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { EmbedBuilder } from 'discord.js';
import { type CommandHandler } from '../infra/CommandHandler.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import {
  UserPanelEmbedBuilder,
  getCurrencyHistoryButtonLabel,
} from '../services/UserPanelEmbedBuilder.js';
import { UserPanelService } from '../services/UserPanelService.js';
import { ZhTwStrings } from '../i18n/zh-TW.js';

/**
 * /user-panel slash command handler.
 * Opens the user panel showing balance, tokens, and action buttons.
 * Mirrors Java UserPanelCommandHandler.
 */
export class UserPanelCommand implements CommandHandler {
  readonly commandName = 'user-panel';

  constructor(
    private readonly userPanelService: UserPanelService,
    private readonly sessionManager: PanelSessionManager,
    private readonly embedBuilder: UserPanelEmbedBuilder,
  ) {}

  async execute(interaction: DiscordInteraction, _context: DiscordContext): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    interaction.makeEphemeral();
    await interaction.deferReply();

    const result = await this.userPanelService.getUserPanelView(guildId, userId);

    if (result.isErr()) {
      await interaction.reply(ZhTwStrings.unexpectedError);
      return;
    }

    const view = result.getValue();
    const userMention = `<@${userId}>`;
    const embedData = this.embedBuilder.buildPanelEmbed(view, userMention);
    const embed = new EmbedBuilder()
      .setTitle(embedData.title)
      .setDescription(embedData.description)
      .addFields(embedData.fields)
      .setColor(embedData.color)
      .setFooter({ text: embedData.footer ?? '' });

    const rows = UserPanelEmbedBuilder.buildPanelComponents(getCurrencyHistoryButtonLabel(view));
    const replyMeta = await interaction.replyWithComponents(embed, rows);

    if (replyMeta) {
      this.sessionManager.createSession(guildId, userId);
      const session = this.sessionManager.getSession(guildId, userId);
      if (session) {
        session.channelId = replyMeta.channelId;
        session.messageId = replyMeta.id;
      }
    }
  }
}
