import {
  type DomainEvent,
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type CurrencyConfigChangedEvent,
  type DiscordRuntimeGateway,
} from '@ltdjms/shared';
import { type Client, type TextChannel } from 'discord.js';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';
import { UserPanelEmbedBuilder } from '../user/UserPanelEmbedBuilder.js';
import { EmbedBuilder } from 'discord.js';

/**
 * Event type string constants for discrimination.
 */
const EVENT_TYPES = {
  BALANCE_CHANGED: 'balance_changed',
  GAME_TOKEN_CHANGED: 'game_token_changed',
  CURRENCY_CONFIG_CHANGED: 'currency_config_changed',
} as const;

/**
 * Listens to domain events and updates active user panel sessions.
 * Uses eventType discriminant for type-safe event identification.
 * Events: BalanceChangedEvent, GameTokenChangedEvent, CurrencyConfigChangedEvent.
 * Matches Java UserPanelUpdateListener.
 */
export class UserPanelUpdateListener {
  constructor(
    private readonly sessionManager: PanelSessionManager,
    private readonly memberInfoFacade: MemberInfoFacade,
    private readonly discordGateway: DiscordRuntimeGateway,
    private readonly embedBuilder?: UserPanelEmbedBuilder,
  ) {}

  /**
   * Handles a domain event and updates relevant user panels.
   * This is called by DomainEventPublisher for each registered listener.
   */
  async onEvent(event: DomainEvent): Promise<void> {
    // Only handle events relevant to user panels
    if (!this.isRelevantEvent(event)) return;

    const guildId = String(event.guildId);

    // Get all active sessions for this guild
    const sessions = this.sessionManager.getAllForGuild(guildId);

    if (sessions.length === 0) {
      console.log(
        `[UserPanelUpdateListener] Event ${event.eventType} for guildId=${guildId}: no active sessions to update`,
      );
      return;
    }

    // Determine affected user IDs based on event type
    let affectedUserIds: string[] = [];

    if (event.eventType === EVENT_TYPES.BALANCE_CHANGED) {
      affectedUserIds = [String((event as BalanceChangedEvent).userId)];
    } else if (event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED) {
      affectedUserIds = [String((event as GameTokenChangedEvent).userId)];
    } else if (event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED) {
      // Currency config change affects all users in guild
      affectedUserIds = sessions.map((s) => s.userId);
    }

    // Update each affected session
    let updatedCount = 0;
    const toRemove: Array<{ guildId: string; userId: string }> = [];

    for (const session of sessions) {
      if (!affectedUserIds.includes(session.userId)) continue;

      try {
        // Refresh data
        const result = await this.memberInfoFacade.getUserPanelView(
          guildId,
          session.userId,
        );

        if (result.isErr()) {
          console.error(
            `[UserPanelUpdateListener] Failed to refresh panel for guildId=${guildId}, userId=${session.userId}:`,
            result.getError(),
          );
          continue;
        }

        const view = result.getValue();
        updatedCount++;

        // Real-time push update: fetch the panel message and rebuild embed
        const channelId = session.channelId;
        const messageId = session.messageId;
        if (channelId && messageId) {
          try {
            const client = this.discordGateway.requireReadyClient() as Client;
            const channel = await client.channels.fetch(channelId);
            if (channel?.isTextBased()) {
              const message = await (channel as TextChannel).messages.fetch(messageId);

              // Rebuild embed using UserPanelEmbedBuilder
              const embedBuilder = this.embedBuilder ?? new UserPanelEmbedBuilder();
              const embedData = embedBuilder.buildUserPanelEmbed(view);
              const embed = new EmbedBuilder()
                .setTitle(embedData.title)
                .setDescription(embedData.description)
                .setColor(embedData.color);

              await message.edit({ embeds: [embed] });
            }
          } catch (fetchErr) {
            // If the message or channel no longer exists, remove the session
            console.log(
              `[UserPanelUpdateListener] Failed to fetch message ${messageId} in channel ${channelId}: removing session`,
            );
            toRemove.push({ guildId: session.guildId, userId: session.userId });
          }
        }

        console.log(
          `[UserPanelUpdateListener] Updated panel for ` +
          `guildId=${guildId}, userId=${session.userId}: ` +
          `balance=${view.balance}${view.currencyIcon}, tokens=${view.tokens}` +
          (session.channelId ? `, channelId=${session.channelId}` : ''),
        );
      } catch (err) {
        console.error(
          `[UserPanelUpdateListener] Error updating panel for guildId=${guildId}, userId=${session.userId}:`,
          err,
        );
      }
    }

    // Clean up stale sessions
    for (const { guildId: gId, userId: uId } of toRemove) {
      this.sessionManager.removeSession(gId, uId);
    }

    console.log(
      `[UserPanelUpdateListener] Event ${event.eventType}: updated ${updatedCount}/${sessions.length} active sessions in guildId=${guildId}`,
    );
  }

  private isRelevantEvent(event: DomainEvent): boolean {
    return (
      event.eventType === EVENT_TYPES.BALANCE_CHANGED ||
      event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED ||
      event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED
    );
  }
}
