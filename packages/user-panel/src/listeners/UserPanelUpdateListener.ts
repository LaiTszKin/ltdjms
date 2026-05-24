import type { DomainEvent } from '@ltdjms/shared';
import { type DiscordRuntimeGateway } from '@ltdjms/shared';
import type {
  BalanceChangedEvent,
  CurrencyConfigChangedEvent,
} from '@ltdjms/economy';
import type { GameTokenChangedEvent } from '@ltdjms/games';
import { type Client, type TextChannel } from 'discord.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import { MemberInfoFacade } from '../facades/MemberInfoFacade.js';
import { UserPanelEmbedBuilder } from '../services/UserPanelEmbedBuilder.js';
import { EmbedBuilder } from 'discord.js';

const EVENT_TYPES = {
  BALANCE_CHANGED: 'balance_changed',
  GAME_TOKEN_CHANGED: 'game_token_changed',
  CURRENCY_CONFIG_CHANGED: 'currency_config_changed',
} as const;

/**
 * Listens to domain events and updates active user panel sessions.
 */
export class UserPanelUpdateListener {
  private readonly lastUpdateTimestamps = new Map<string, number>();
  private cleanupCounter = 0;

  private static readonly MAX_THROTTLE_ENTRIES = 500;

  constructor(
    private readonly sessionManager: PanelSessionManager,
    private readonly memberInfoFacade: MemberInfoFacade,
    private readonly discordGateway: DiscordRuntimeGateway,
    private readonly embedBuilder?: UserPanelEmbedBuilder,
  ) {}

  async onEvent(event: DomainEvent): Promise<void> {
    if (!this.isRelevantEvent(event)) return;

    const guildId = String(event.guildId);
    const eventType = event.eventType;

    const throttleKey = `${guildId}:${eventType}`;
    if (this.shouldThrottle(throttleKey)) return;

    const sessions = this.sessionManager.getAllForGuild(guildId);

    if (sessions.length === 0) {
      console.log(
        `[UserPanelUpdateListener] Event ${event.eventType} for guildId=${guildId}: no active sessions to update`,
      );
      return;
    }

    let affectedUserIds: Set<string>;

    if (event.eventType === EVENT_TYPES.BALANCE_CHANGED) {
      affectedUserIds = new Set([String((event as BalanceChangedEvent).userId)]);
    } else if (event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED) {
      affectedUserIds = new Set([String((event as GameTokenChangedEvent).userId)]);
    } else if (event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED) {
      affectedUserIds = new Set(sessions.map((s) => s.userId));
    } else {
      affectedUserIds = new Set();
    }

    let updatedCount = 0;
    const toRemove: Array<{ guildId: string; userId: string }> = [];

    for (const session of sessions) {
      if (!affectedUserIds.has(session.userId)) continue;

      try {
        const result = await this.memberInfoFacade.getUserPanelView(guildId, session.userId);

        if (result.isErr()) {
          console.error(
            `[UserPanelUpdateListener] Failed to refresh panel for guildId=${guildId}, userId=${session.userId}:`,
            result.getError(),
          );
          continue;
        }

        const view = result.getValue();
        updatedCount++;

        const channelId = session.channelId;
        const messageId = session.messageId;
        if (channelId && messageId) {
          try {
            const client = this.discordGateway.requireReadyClient() as Client;
            const channel = await client.channels.fetch(channelId);
            if (channel?.isTextBased()) {
              const message = await (channel as TextChannel).messages.fetch(messageId);

              const embedBuilder = this.embedBuilder ?? new UserPanelEmbedBuilder();
              const embedData = embedBuilder.buildUserPanelEmbed(view);
              const embed = new EmbedBuilder()
                .setTitle(embedData.title)
                .setDescription(embedData.description)
                .setColor(embedData.color);

              await message.edit({ embeds: [embed] });
            }
          } catch {
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

  private shouldThrottle(key: string, minIntervalMs = 200): boolean {
    const now = Date.now();
    const last = this.lastUpdateTimestamps.get(key) ?? 0;
    if (now - last < minIntervalMs) return true;
    this.lastUpdateTimestamps.set(key, now);

    this.cleanupCounter++;
    if (this.cleanupCounter % 50 === 0) {
      const cutoff = now - 60_000;
      for (const [k, v] of this.lastUpdateTimestamps) {
        if (v < cutoff) this.lastUpdateTimestamps.delete(k);
      }
      if (this.lastUpdateTimestamps.size >= UserPanelUpdateListener.MAX_THROTTLE_ENTRIES) {
        const sorted = [...this.lastUpdateTimestamps.entries()].sort((a, b) => a[1] - b[1]);
        const evictCount = sorted.length - UserPanelUpdateListener.MAX_THROTTLE_ENTRIES;
        for (let i = 0; i < evictCount; i++) {
          this.lastUpdateTimestamps.delete(sorted[i][0]);
        }
      }
    }

    return false;
  }
}
