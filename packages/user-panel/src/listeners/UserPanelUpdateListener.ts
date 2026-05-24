import type { DomainEvent } from '@ltdjms/shared';
import {
  type DiscordRuntimeGateway,
  groupSessionsByChannel,
  processWithConcurrencyLimit,
} from '@ltdjms/shared';
import type { BalanceChangedEvent } from '@ltdjms/economy';
import type { GameTokenChangedEvent } from '@ltdjms/games';
import { type Client, type TextChannel, EmbedBuilder, DiscordAPIError } from 'discord.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import { UserPanelService } from '../services/UserPanelService.js';
import { UserPanelEmbedBuilder } from '../services/UserPanelEmbedBuilder.js';
import { USER_PANEL_FOOTER_PUSH_UPDATE } from '../constants/UserPanelConstants.js';

const EVENT_TYPES = {
  BALANCE_CHANGED: 'balance_changed',
  GAME_TOKEN_CHANGED: 'game_token_changed',
  CURRENCY_CONFIG_CHANGED: 'currency_config_changed',
} as const;

/**
 * Listens to domain events and updates active user panel sessions.
 * Mirrors Java UserPanelUpdateListener (embed-only updates).
 */
export class UserPanelUpdateListener {
  /** @internal accessible for tests (set DEBOUNCE_MS = 1 in vitest beforeEach). */
  static readonly DEBOUNCE_MS = 500;

  private static readonly MAX_CONCURRENCY = 3;

  private readonly guildDebounceTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly userDebounceTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly updateChains = new Map<string, Promise<void>>();

  constructor(
    private readonly sessionManager: PanelSessionManager,
    private readonly userPanelService: UserPanelService,
    private readonly discordGateway: DiscordRuntimeGateway,
    private readonly embedBuilder: UserPanelEmbedBuilder = new UserPanelEmbedBuilder(),
  ) {}

  dispose(): void {
    for (const timer of this.guildDebounceTimers.values()) {
      clearTimeout(timer);
    }
    this.guildDebounceTimers.clear();

    for (const timer of this.userDebounceTimers.values()) {
      clearTimeout(timer);
    }
    this.userDebounceTimers.clear();
    this.updateChains.clear();
  }

  async onEvent(event: DomainEvent): Promise<void> {
    if (!this.isRelevantEvent(event)) return;

    const guildId = String(event.guildId);

    if (event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED) {
      this.scheduleDebouncedGuildUpdate(guildId);
      return;
    }

    if (event.eventType === EVENT_TYPES.BALANCE_CHANGED) {
      const userId = String((event as BalanceChangedEvent).userId);
      this.scheduleDebouncedUserUpdate(guildId, userId);
      return;
    }

    if (event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED) {
      const userId = String((event as GameTokenChangedEvent).userId);
      this.scheduleDebouncedUserUpdate(guildId, userId);
    }
  }

  private sessionKey(guildId: string, userId: string): string {
    return `${guildId}:${userId}`;
  }

  private scheduleDebouncedGuildUpdate(guildId: string): void {
    const existing = this.guildDebounceTimers.get(guildId);
    if (existing) clearTimeout(existing);

    const timer = setTimeout(() => {
      this.guildDebounceTimers.delete(guildId);
      this.updateAllGuildPanels(guildId).catch((err) => {
        console.error(`[UserPanelUpdateListener] Error in guild update for ${guildId}:`, err);
      });
    }, UserPanelUpdateListener.DEBOUNCE_MS);

    this.guildDebounceTimers.set(guildId, timer);
  }

  private scheduleDebouncedUserUpdate(guildId: string, userId: string): void {
    const key = this.sessionKey(guildId, userId);
    const existing = this.userDebounceTimers.get(key);
    if (existing) clearTimeout(existing);

    const timer = setTimeout(() => {
      this.userDebounceTimers.delete(key);
      this.runSerializedUpdate(guildId, userId, () => this.updateUserPanel(guildId, userId)).catch(
        (err) => {
          console.error(
            `[UserPanelUpdateListener] Error in user update for ${guildId}/${userId}:`,
            err,
          );
        },
      );
    }, UserPanelUpdateListener.DEBOUNCE_MS);

    this.userDebounceTimers.set(key, timer);
  }

  private runSerializedUpdate(
    guildId: string,
    userId: string,
    updateFn: () => Promise<void>,
  ): Promise<void> {
    const key = this.sessionKey(guildId, userId);
    const previous = this.updateChains.get(key) ?? Promise.resolve();
    const next = previous
      .then(updateFn)
      .catch((err) => {
        console.error(`[UserPanelUpdateListener] Serialized update failed for ${key}:`, err);
      })
      .finally(() => {
        if (this.updateChains.get(key) === next) {
          this.updateChains.delete(key);
        }
      });

    this.updateChains.set(key, next);
    return next;
  }

  private async updateAllGuildPanels(guildId: string): Promise<void> {
    const sessions = this.sessionManager.getAllForGuild(guildId);
    const channelGroupMap = groupSessionsByChannel(sessions);

    if (channelGroupMap.size === 0) return;

    const client = this.discordGateway.requireReadyClient() as Client;
    const toRemove: Array<{ guildId: string; userId: string }> = [];
    const channelEntries = Array.from(channelGroupMap.entries());

    await processWithConcurrencyLimit(
      channelEntries,
      async ([channelId, groupSessions]) => {
        const channel = await client.channels.fetch(channelId);
        if (!channel?.isTextBased()) return;
        const textChannel = channel as TextChannel;

        for (const session of groupSessions) {
          await this.runSerializedUpdate(guildId, session.userId, async () => {
            try {
              await this.editSessionPanel(textChannel, guildId, session.userId);
            } catch (err) {
              if (this.shouldRemoveSession(err)) {
                toRemove.push({ guildId: session.guildId, userId: session.userId });
              }
            }
          });
        }
      },
      UserPanelUpdateListener.MAX_CONCURRENCY,
    );

    for (const { guildId: gId, userId: uId } of toRemove) {
      this.sessionManager.removeSession(gId, uId);
    }
  }

  private async updateUserPanel(guildId: string, userId: string): Promise<void> {
    const session = this.sessionManager.peekSession(guildId, userId);
    if (!session?.channelId || !session.messageId) return;

    try {
      const client = this.discordGateway.requireReadyClient() as Client;
      const channel = await client.channels.fetch(session.channelId);
      if (!channel?.isTextBased()) return;

      await this.editSessionPanel(channel as TextChannel, guildId, userId);
    } catch (err) {
      if (this.shouldRemoveSession(err)) {
        this.sessionManager.removeSession(guildId, userId);
      } else {
        console.warn(
          `[UserPanelUpdateListener] Transient error updating panel for ${guildId}/${userId}:`,
          err,
        );
      }
    }
  }

  private async editSessionPanel(
    textChannel: TextChannel,
    guildId: string,
    userId: string,
  ): Promise<void> {
    const session = this.sessionManager.peekSession(guildId, userId);
    if (!session?.messageId) return;

    const result = await this.userPanelService.getUserPanelView(guildId, userId);
    if (result.isErr()) return;

    const view = result.getValue();
    const userMention = `<@${userId}>`;
    const embedData = this.embedBuilder.buildPanelEmbed(
      view,
      userMention,
      USER_PANEL_FOOTER_PUSH_UPDATE,
    );

    const embed = new EmbedBuilder()
      .setTitle(embedData.title)
      .setDescription(embedData.description)
      .addFields(embedData.fields)
      .setColor(embedData.color)
      .setFooter({ text: embedData.footer ?? '' });

    const message = await textChannel.messages.fetch(session.messageId);
    await message.edit({ embeds: [embed] });
  }

  private shouldRemoveSession(err: unknown): boolean {
    const code = this.extractDiscordErrorCode(err);
    if (code === undefined) return false;
    return [10003, 10008, 50001, 50013].includes(code);
  }

  private extractDiscordErrorCode(err: unknown): number | undefined {
    if (err instanceof DiscordAPIError) {
      return typeof err.code === 'number' ? err.code : Number(err.code);
    }

    if (typeof err === 'object' && err !== null && 'code' in err) {
      const code = Number((err as { code: unknown }).code);
      return Number.isNaN(code) ? undefined : code;
    }

    return undefined;
  }

  private isRelevantEvent(event: DomainEvent): boolean {
    return (
      event.eventType === EVENT_TYPES.BALANCE_CHANGED ||
      event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED ||
      event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED
    );
  }
}
