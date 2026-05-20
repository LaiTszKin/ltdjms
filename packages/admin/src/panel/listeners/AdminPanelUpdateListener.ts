import {
  type DomainEvent,
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type CurrencyConfigChangedEvent,
  type DiceGameConfigChangedEvent,
  type ProductChangedEvent,
  type RedemptionCodesGeneratedEvent,
  type AIAgentChannelConfigChangedEvent,
  type ProductRedemptionCompletedEvent,
  type AgentFailedEvent,
  type DiscordRuntimeGateway,
} from '@ltdjms/shared';
import {
  type Client,
  type TextChannel,
  type Message,
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../session/types.js';
import { CurrencyManagementFacade } from '../../facades/CurrencyManagementFacade.js';
import { AdminPanelViewFactory } from '../admin/views/AdminPanelViewFactory.js';

/**
 * Event type string constants for discrimination.
 */
const EVENT_TYPES = {
  BALANCE_CHANGED: 'balance_changed',
  GAME_TOKEN_CHANGED: 'game_token_changed',
  CURRENCY_CONFIG_CHANGED: 'currency_config_changed',
  DICE_GAME_CONFIG_CHANGED: 'dice_game_config_changed',
  PRODUCT_CHANGED: 'product_changed',
  REDEMPTION_CODES_GENERATED: 'redemption_codes_generated',
  PRODUCT_REDEMPTION_COMPLETED: 'product_redemption_completed',
  AI_AGENT_CHANNEL_CONFIG_CHANGED: 'ai_agent_channel_config_changed',
  AGENT_FAILED: 'agent_failed',
} as const;

/**
 * Listens to domain events and updates active admin panel sessions.
 * Handles 13+ event types across different admin panel view states.
 * Uses eventType discriminant for type-safe event identification.
 * Matches Java AdminPanelUpdateListener.
 */
export class AdminPanelUpdateListener {
  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
    private readonly discordGateway: DiscordRuntimeGateway,
    private readonly currencyFacade: CurrencyManagementFacade,
    private readonly viewFactory: AdminPanelViewFactory,
  ) {}

  /**
   * Handles a domain event and updates relevant admin panels.
   * Only updates panels that are in the relevant view state.
   */
  async onEvent(event: DomainEvent): Promise<void> {
    if (!this.isAdminRelevantEvent(event)) return;

    const guildId = event.guildId;
    const eventType = event.eventType;
    const sessions = this.sessionManager.getAllForGuild(String(guildId));

    if (sessions.length === 0) {
      console.log(
        `[AdminPanelUpdateListener] Event ${eventType} for guildId=${guildId}: no active sessions to update`,
      );
      return;
    }

    let updatedCount = 0;
    const toRemove: Array<{ guildId: string; userId: string }> = [];

    for (const session of sessions) {
      try {
        const shouldUpdate = this.shouldUpdateForViewState(event, session.viewState);
        if (!shouldUpdate) continue;

        updatedCount++;

        // Real-time push update: fetch the panel message and edit embed
        const channelId = session.channelId;
        const messageId = session.messageId;
        if (channelId && messageId) {
          try {
            const client = this.discordGateway.requireReadyClient() as Client;
            const channel = await client.channels.fetch(channelId);
            if (channel?.isTextBased()) {
              const message = await (channel as TextChannel).messages.fetch(messageId);
              // Rebuild embed with fresh data from facades.
              // For MAIN view events (currency config, dice game config), rebuild
              // the full main panel embed. For other view states, re-edit to trigger
              // a visual refresh (fields will be populated when the user navigates).
              const isMainViewRelevant =
                (event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED ||
                 event.eventType === EVENT_TYPES.DICE_GAME_CONFIG_CHANGED) &&
                session.viewState === AdminPanelViewState.MAIN;

              if (isMainViewRelevant) {
                const panelContent = await this.buildMainPanelEmbed(guildId);
                if (panelContent) {
                  await message.edit({
                    embeds: [panelContent.embed],
                    components: panelContent.rows,
                  });
                }
              } else {
                const existingEmbeds = message.embeds;
                if (existingEmbeds.length > 0) {
                  const updatedEmbed = existingEmbeds[0].data;
                  await message.edit({ embeds: [updatedEmbed] });
                }
              }
            }
          } catch (fetchErr) {
            // If the message or channel no longer exists, remove the session
            console.log(
              `[AdminPanelUpdateListener] Failed to fetch message ${messageId} in channel ${channelId}: removing session`,
            );
            toRemove.push({ guildId: session.guildId, userId: session.userId });
          }
        }

        console.log(
          `[AdminPanelUpdateListener] Event ${eventType} triggers update for ` +
          `guildId=${guildId}, userId=${session.userId}, viewState=${session.viewState}` +
          (session.channelId ? `, channelId=${session.channelId}` : ''),
        );
      } catch (err) {
        console.error(
          `[AdminPanelUpdateListener] Error updating panel for guildId=${guildId}, userId=${session.userId}:`,
          err,
        );
      }
    }

    // Clean up stale sessions
    for (const { guildId: gId, userId: uId } of toRemove) {
      this.sessionManager.removeSession(gId, uId);
    }

    if (updatedCount > 0) {
      console.log(
        `[AdminPanelUpdateListener] Event ${eventType}: updated ${updatedCount}/${sessions.length} active sessions in guildId=${guildId}`,
      );
    }
  }

  private async getGuildName(guildId: string): Promise<string> {
    try {
      const client = this.discordGateway.requireReadyClient() as Client;
      const guild = await client.guilds.fetch(guildId);
      return guild?.name ?? `Guild ${guildId}`;
    } catch {
      return `Guild ${guildId}`;
    }
  }

  /**
   * Rebuilds the main admin panel embed with fresh data from facades.
   * Called by onEvent for MAIN view events (CurrencyConfigChangedEvent,
   * DiceGameConfigChangedEvent) to replace the no-op re-edit.
   */
  private async buildMainPanelEmbed(guildId: string): Promise<{
    embed: EmbedBuilder;
    rows: ActionRowBuilder<ButtonBuilder>[];
  } | null> {
    try {
      const configResult = await this.currencyFacade.getConfig(guildId);
      const currencyConfig = configResult.isOk() ? configResult.getValue() : null;

      const guildName = await this.getGuildName(guildId);
      const dispatchCount = 0; // TODO(P1-37): Query from dispatch service

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

      const rows: ActionRowBuilder<ButtonBuilder>[] = [];
      const buttons = mainPanel.buttons.map((b) =>
        new ButtonBuilder()
          .setCustomId(b.id)
          .setLabel(b.label)
          .setStyle(b.style as ButtonStyle)
          .setDisabled(b.disabled),
      );

      for (let i = 0; i < buttons.length; i += 3) {
        rows.push(
          new ActionRowBuilder<ButtonBuilder>().addComponents(
            buttons.slice(i, i + 5),
          ),
        );
      }

      return { embed, rows };
    } catch (err) {
      console.error(
        `[AdminPanelUpdateListener] Error building main panel embed for guildId=${guildId}:`,
        err,
      );
      return null;
    }
  }

  private isAdminRelevantEvent(event: DomainEvent): boolean {
    const relevantTypes: ReadonlySet<string> = new Set([
      EVENT_TYPES.CURRENCY_CONFIG_CHANGED,
      EVENT_TYPES.DICE_GAME_CONFIG_CHANGED,
      EVENT_TYPES.PRODUCT_CHANGED,
      EVENT_TYPES.REDEMPTION_CODES_GENERATED,
      EVENT_TYPES.AI_AGENT_CHANNEL_CONFIG_CHANGED,
      EVENT_TYPES.BALANCE_CHANGED,
      EVENT_TYPES.GAME_TOKEN_CHANGED,
      EVENT_TYPES.PRODUCT_REDEMPTION_COMPLETED,
      EVENT_TYPES.AGENT_FAILED,
    ]);
    return relevantTypes.has(event.eventType);
  }

  private shouldUpdateForViewState(
    event: DomainEvent,
    viewState: AdminPanelViewState,
  ): boolean {
    switch (event.eventType) {
      case EVENT_TYPES.CURRENCY_CONFIG_CHANGED:
        return true;

      case EVENT_TYPES.DICE_GAME_CONFIG_CHANGED:
        return viewState === AdminPanelViewState.MAIN ||
               viewState === AdminPanelViewState.GAME_CONFIG;

      case EVENT_TYPES.PRODUCT_CHANGED:
        return (
          viewState === AdminPanelViewState.PRODUCT_LIST ||
          viewState === AdminPanelViewState.PRODUCT_DETAIL
        );

      case EVENT_TYPES.REDEMPTION_CODES_GENERATED:
        return viewState === AdminPanelViewState.PRODUCT_CODE_LIST;

      case EVENT_TYPES.AI_AGENT_CHANNEL_CONFIG_CHANGED:
        return true;

      case EVENT_TYPES.BALANCE_CHANGED:
        return viewState === AdminPanelViewState.BALANCE;

      case EVENT_TYPES.GAME_TOKEN_CHANGED:
        return viewState === AdminPanelViewState.TOKEN;

      case EVENT_TYPES.PRODUCT_REDEMPTION_COMPLETED:
        return viewState === AdminPanelViewState.PRODUCT_CODE_LIST;

      case EVENT_TYPES.AGENT_FAILED:
        return true;

      default:
        return false;
    }
  }
}
