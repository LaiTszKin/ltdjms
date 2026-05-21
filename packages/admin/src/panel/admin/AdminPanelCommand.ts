import {
  type DiscordInteraction,
  type DiscordContext,
  type DiscordEmbedBuilder,
} from '@ltdjms/shared';
import { ActionRowBuilder, ButtonBuilder, ButtonStyle } from 'discord.js';
import { type CommandHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewFactory } from './views/AdminPanelViewFactory.js';
import { CurrencyManagementFacade } from '../../facades/CurrencyManagementFacade.js';
import { DispatchManagementFacade } from '../../facades/DispatchManagementFacade.js';
import { ZhTwStrings } from '../../i18n/zh-TW.js';

/**
 * /admin-panel slash command handler.
 * Opens the admin panel main menu with 9 feature buttons.
 * Requires ADMINISTRATOR permission (enforced by Discord and handler).
 */
export class AdminPanelCommand implements CommandHandler {
  readonly commandName = 'admin-panel';

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
    private readonly viewFactory: AdminPanelViewFactory,
    private readonly currencyFacade: CurrencyManagementFacade,
    private readonly dispatchFacade: DispatchManagementFacade,
    private readonly embedBuilder: DiscordEmbedBuilder,
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
    await interaction.deferReply();

    // Permission check (second layer)
    if (!this.hasAdminPermission(interaction)) {
      await interaction.reply(ZhTwStrings.permissionAdminRequired);
      return;
    }

    // Create session
    const guildId = interaction.getGuildId();
    const userId = interaction.getUserId();
    this.sessionManager.createSession(guildId, userId);

    // Query real currency config
    const configResult = await this.currencyFacade.getConfig(guildId);
    const currencyConfig = configResult.isOk() ? configResult.getValue() : null;

    const dispatchResult = await this.dispatchFacade.countActiveOrders(guildId);
    const dispatchCount = dispatchResult.isOk() ? dispatchResult.getValue() : 0;

    // Attempt to get the actual guild name from the interaction
    const guildName = interaction.getGuildName() ?? `Guild ${guildId}`;

    const mainPanel = this.viewFactory.buildMainPanelEmbed(
      guildName,
      currencyConfig,
      dispatchCount,
    );

    // Build embed with fields before calling build()
    this.embedBuilder
      .setTitle(mainPanel.title)
      .setDescription(mainPanel.description)
      .setColor(mainPanel.color)
      .setFooter(mainPanel.footer);

    for (const field of mainPanel.fields) {
      this.embedBuilder.addField(field.name, field.value, field.inline);
    }

    const embed = this.embedBuilder.build();

    // Convert button data to discord.js ActionRow components
    const rows: ActionRowBuilder<ButtonBuilder>[] = [];
    const buttons = mainPanel.buttons.map((b) =>
      new ButtonBuilder()
        .setCustomId(b.id)
        .setLabel(b.label)
        .setStyle(b.style as ButtonStyle)
        .setDisabled(b.disabled),
    );

    // Split into rows of 3 (3x3 grid per spec)
    for (let i = 0; i < buttons.length; i += 3) {
      rows.push(
        new ActionRowBuilder<ButtonBuilder>().addComponents(
          buttons.slice(i, i + 3),
        ),
      );
    }

    // Send embed with components and store channelId/messageId for real-time push updates
    const replyMeta = await interaction.replyWithComponents(embed, rows);

    if (replyMeta) {
      const session = this.sessionManager.getSession(guildId, userId);
      if (session) {
        session.channelId = replyMeta.channelId;
        session.messageId = replyMeta.id;
      }
    }
  }

  private hasAdminPermission(interaction: DiscordInteraction): boolean {
    return interaction.isAdministrator();
  }
}
