import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
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

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();

    // Create session
    this.sessionManager.createSession(guildId, userId);

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
        .setCustomId('user_currency_history')
        .setLabel(ZhTwStrings.userPanelBtnCurrencyHistory)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId('user_token_history')
        .setLabel(ZhTwStrings.userPanelBtnTokenHistory)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId('user_redemption_history')
        .setLabel(ZhTwStrings.userPanelBtnRedemptionHistory)
        .setStyle(ButtonStyle.Secondary),
      new ButtonBuilder()
        .setCustomId('user_redeem_code')
        .setLabel(ZhTwStrings.userPanelBtnRedeemCode)
        .setStyle(ButtonStyle.Primary),
    ];

    const row = new ActionRowBuilder<ButtonBuilder>().addComponents(buttons);

    // Use the raw discord.js interaction to send embed with components
    const raw = interaction.getHook() as {
      reply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
    };
    await raw.reply({ embeds: [embed], components: [row] });
  }
}
