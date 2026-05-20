import {
  type DiscordInteraction,
  type DiscordContext,
} from '@ltdjms/shared';
import { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle } from 'discord.js';
import { type CommandHandler } from '../../commands/infra/CommandHandler.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewFactory } from './views/AdminPanelViewFactory.js';
import { CurrencyManagementFacade } from '../../facades/CurrencyManagementFacade.js';
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
  ) {}

  async execute(
    interaction: DiscordInteraction,
    context: DiscordContext,
  ): Promise<void> {
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

    // TODO(P1-37): Query active dispatch order count from a dispatch service
    // (e.g., EscortOrderService) once it is available. Currently hardcoded to 0.
    const dispatchCount = 0;

    // Attempt to get the actual guild name from the raw interaction
    const rawHook = interaction.getHook() as { guild?: { name?: string } };
    const guildName = rawHook.guild?.name ?? `Guild ${guildId}`;

    const mainPanel = this.viewFactory.buildMainPanelEmbed(
      guildName,
      currencyConfig,
      dispatchCount,
    );

    const embed = new EmbedBuilder()
      .setTitle(mainPanel.title)
      .setDescription(mainPanel.description)
      .setColor(mainPanel.color)
      .setFooter({ text: mainPanel.footer });

    for (const field of mainPanel.fields) {
      embed.addFields({ name: field.name, value: field.value, inline: field.inline });
    }

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

    // Use the raw discord.js interaction to send embed with components
    const raw = interaction.getHook() as {
      reply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<unknown>;
      fetchReply: () => Promise<{ channelId: string; id: string }>;
    };
    await raw.reply({ embeds: [embed], components: rows });

    // Store channelId and messageId for real-time push updates via listeners
    try {
      const replyMsg = await raw.fetchReply();
      if (replyMsg && 'channelId' in replyMsg && 'id' in replyMsg) {
        const session = this.sessionManager.getSession(guildId, userId);
        if (session) {
          session.channelId = String(replyMsg.channelId);
          session.messageId = String(replyMsg.id);
        }
      }
    } catch {
      // Non-critical: push updates will not be available but the panel still works
    }
  }

  private hasAdminPermission(interaction: DiscordInteraction): boolean {
    return interaction.isAdministrator();
  }
}
