import type { DomainEvent } from '@ltdjms/shared';
import { type DiscordRuntimeGateway, processWithConcurrencyLimit } from '@ltdjms/shared';
import type { BalanceChangedEvent } from '@ltdjms/economy';
import type { GameTokenChangedEvent } from '@ltdjms/games';
import { type Client, type TextChannel, EmbedBuilder } from 'discord.js';
import { PanelSessionManager } from '../session/PanelSessionManager.js';
import { UserPanelService } from '../services/UserPanelService.js';
import { UserPanelEmbedBuilder } from '../services/UserPanelEmbedBuilder.js';
import { USER_PANEL_FOOTER_PUSH_UPDATE } from '../constants/UserPanelConstants.js';
import type { PanelSessionData } from '../session/types.js';

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

  private readonly debounceTimers = new Map<string, ReturnType<typeof setTimeout>>();

  constructor(
    private readonly sessionManager: PanelSessionManager,
    private readonly userPanelService: UserPanelService,
    private readonly discordGateway: DiscordRuntimeGateway,
    private readonly embedBuilder: UserPanelEmbedBuilder = new UserPanelEmbedBuilder(),
  ) {}

  async onEvent(event: DomainEvent): Promise<void> {
    if (!this.isRelevantEvent(event)) return;

    const guildId = String(event.guildId);

    if (event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED) {
      this.scheduleDebouncedGuildUpdate(guildId);
      return;
    }

    if (event.eventType === EVENT_TYPES.BALANCE_CHANGED) {
      const userId = String((event as BalanceChangedEvent).userId);
      await this.updateUserPanel(guildId, userId);
      return;
    }

    if (event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED) {
      const userId = String((event as GameTokenChangedEvent).userId);
      await this.updateUserPanel(guildId, userId);
    }
  }

  private scheduleDebouncedGuildUpdate(guildId: string): void {
    const existing = this.debounceTimers.get(guildId);
    if (existing) clearTimeout(existing);

    const timer = setTimeout(() => {
      this.debounceTimers.delete(guildId);
      this.updateAllGuildPanels(guildId).catch((err) => {
        console.error(`[UserPanelUpdateListener] Error in guild update for ${guildId}:`, err);
      });
    }, UserPanelUpdateListener.DEBOUNCE_MS);

    this.debounceTimers.set(guildId, timer);
  }

  private async updateAllGuildPanels(guildId: string): Promise<void> {
    const sessions = this.sessionManager.getAllForGuild(guildId);
    const channelGroupMap = new Map<string, PanelSessionData[]>();

    for (const session of sessions) {
      if (!session.channelId || !session.messageId) continue;
      const group = channelGroupMap.get(session.channelId);
      if (group) {
        group.push(session);
      } else {
        channelGroupMap.set(session.channelId, [session]);
      }
    }

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
          try {
            await this.editSessionPanel(textChannel, guildId, session.userId);
          } catch {
            toRemove.push({ guildId: session.guildId, userId: session.userId });
          }
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
    } catch {
      this.sessionManager.removeSession(guildId, userId);
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

  private isRelevantEvent(event: DomainEvent): boolean {
    return (
      event.eventType === EVENT_TYPES.BALANCE_CHANGED ||
      event.eventType === EVENT_TYPES.GAME_TOKEN_CHANGED ||
      event.eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED
    );
  }
}
