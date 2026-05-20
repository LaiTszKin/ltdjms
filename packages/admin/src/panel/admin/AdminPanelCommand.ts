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
import { PermissionFlagsBits } from 'discord.js';

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

    const mainPanel = this.viewFactory.buildMainPanelEmbed(
      `Guild ${guildId}`,
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

    // Split into rows of 5 (max 5 buttons per ActionRow)
    for (let i = 0; i < buttons.length; i += 5) {
      rows.push(
        new ActionRowBuilder<ButtonBuilder>().addComponents(
          buttons.slice(i, i + 5),
        ),
      );
    }

    // Use the raw discord.js interaction to send embed with components
    const raw = interaction.getHook() as {
      reply: (opts: { embeds: EmbedBuilder[]; components: ActionRowBuilder<ButtonBuilder>[] }) => Promise<void>;
    };
    await raw.reply({ embeds: [embed], components: rows });
  }

  private hasAdminPermission(interaction: DiscordInteraction): boolean {
    try {
      const raw = interaction.getHook() as {
        memberPermissions?: { has(permission: bigint): boolean };
        guild?: { ownerId: string };
      };
      const userId = String(interaction.getUserId());

      // Check ADMINISTRATOR permission
      if (raw.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
        return true;
      }

      // Check guild owner
      if (raw.guild?.ownerId === userId) {
        return true;
      }
    } catch {
      // If we cannot access the raw interaction, deny access
    }
    return false;
  }
}
