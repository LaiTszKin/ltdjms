import { type DiscordInteraction, type DiscordContext } from '@ltdjms/shared';
import { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle } from 'discord.js';
import { type CommandHandler } from '../../commands/infra/CommandHandler.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { UserPanelEmbedBuilder } from './UserPanelEmbedBuilder.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

/**
 * /user-panel slash command handler.
 * Opens the user panel showing balance, tokens, and action buttons.
 */
export class UserPanelCommand implements CommandHandler {
  readonly commandName = 'user-panel';

  constructor(
    private readonly memberInfoFacade: MemberInfoFacade,
    private readonly sessionManager: PanelSessionManager,
    private readonly embedBuilder: UserPanelEmbedBuilder,
  ) {}

  async execute(interaction: DiscordInteraction, context: DiscordContext): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Create session
    this.sessionManager.createSession(guildId, userId);

    interaction.makeEphemeral();
    // Defer to prevent Discord 3-second timeout during async facade calls.
    await interaction.deferReply();

    // Query member info
    const result = await this.memberInfoFacade.getUserPanelView(guildId, userId);

    if (result.isErr()) {
      await interaction.reply(ZhTwStrings.unexpectedError);
      return;
    }

    const view = result.getValue();

    // Build embed from structured data
    const embedData = this.embedBuilder.buildUserPanelEmbed(view);
    const embed = new EmbedBuilder()
      .setTitle(embedData.title)
      .setDescription(embedData.description)
      .setColor(embedData.color);

    // Build action buttons
    const buttons = [
      new ButtonBuilder()
        .setCustomId('user_history_currency')
        .setLabel(ZhTwStrings.userPanelBtnCurrencyHistory)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId('user_history_token')
        .setLabel(ZhTwStrings.userPanelBtnTokenHistory)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId('user_history_redemption')
        .setLabel(ZhTwStrings.userPanelBtnRedemptionHistory)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId('user_redeem_code')
        .setLabel(ZhTwStrings.userPanelBtnRedeemCode)
        .setStyle(ButtonStyle.Primary),
    ];

    const row = new ActionRowBuilder<ButtonBuilder>().addComponents(buttons);

    // Send embed with components and store channelId/messageId for real-time push updates
    const replyMeta = await interaction.replyWithComponents(embed, [row]);

    if (replyMeta) {
      const session = this.sessionManager.getSession(guildId, userId);
      if (session) {
        session.channelId = replyMeta.channelId;
        session.messageId = replyMeta.id;
      }
    }
  }
}
