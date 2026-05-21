import type { DomainEvent } from '@ltdjms/shared';
import { type DiscordRuntimeGateway, processWithConcurrencyLimit } from '@ltdjms/shared';
import type {
  BalanceChangedEvent,
  GameTokenChangedEvent,
  CurrencyConfigChangedEvent,
  DiceGameConfigChangedEvent,
} from '@ltdjms/economy';
import type {
  ProductChangedEvent,
  RedemptionCodesGeneratedEvent,
  ProductRedemptionCompletedEvent,
} from '@ltdjms/shop';
import type {
  AIAgentChannelConfigChangedEvent,
  AgentFailedEvent,
} from '@ltdjms/ai';
import {
  type Client,
  type TextChannel,
  EmbedBuilder,
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
} from 'discord.js';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState, type AdminPanelSessionData } from '../../session/types.js';
import { CurrencyManagementFacade } from '../../facades/CurrencyManagementFacade.js';
import { DispatchManagementFacade } from '../../facades/DispatchManagementFacade.js';
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
  AI_CHANNEL_CONFIG_CHANGED: 'ai_channel_config_changed',
  DISPATCH_AFTER_SALES_CONFIG_CHANGED: 'dispatch_after_sales_config_changed',
  ESCORT_PRICING_CHANGED: 'escort_pricing_changed',
  ESCORT_CATALOG_CHANGED: 'escort_catalog_changed',
} as const;

/**
 * Listens to domain events and updates active admin panel sessions.
 * Handles 13+ event types across different admin panel view states.
 * Uses eventType discriminant for type-safe event identification.
 * Matches Java AdminPanelUpdateListener.
 *
 * NOTE(P2-17): 目前僅注入 CurrencyManagementFacade，因此非 MAIN 視圖的更新
 * 僅為 no-op re-edit（視覺刷新但內容不變）。若需非 MAIN 視圖的完整重建，
 * 應注入 ProductFacade、GameTokenFacade 等對應的 facade。
 * 見 buildMainPanelEmbed() 下方 else 分支。
 */
export class AdminPanelUpdateListener {
  /** Tracks last update timestamp per guildId:eventType for rate-limit protection. */
  private readonly lastUpdateTimestamps = new Map<string, number>();
  private cleanupCounter = 0;

  /** Debounce timers that coalesce rapid consecutive same-key events into a single update. */
  private readonly debounceTimers = new Map<string, ReturnType<typeof setTimeout>>();

  /** Coalescing window in ms: events within this interval reset the timer. */
  static readonly DEBOUNCE_MS = 500;

  /** Max concurrent Discord API calls when processing batched updates. */
  private static readonly MAX_CONCURRENCY = 3;

  /** Max entries in the throttle map before evicting oldest entries. */
  private static readonly MAX_THROTTLE_ENTRIES = 500;

  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
    private readonly discordGateway: DiscordRuntimeGateway,
    private readonly currencyFacade: CurrencyManagementFacade,
    private readonly dispatchFacade: DispatchManagementFacade,
    private readonly viewFactory: AdminPanelViewFactory,
  ) {}

  /**
   * Handles a domain event and schedules a debounced batched update for admin panels.
   * Rapid consecutive same-type events are coalesced into a single update
   * within a 500ms window, reducing Discord API calls from O(3 x sessions x events)
   * to O(1 x channel-groups).
   */
  async onEvent(event: DomainEvent): Promise<void> {
    if (!this.isAdminRelevantEvent(event)) return;

    const guildId = String(event.guildId);
    const eventType = event.eventType;

    // Debounce: coalesce rapid consecutive same-type events into a single batched update
    const debounceKey = `${guildId}:${eventType}`;
    this.scheduleDebouncedUpdate(debounceKey, guildId, event);
  }

  /**
   * Schedules or resets the debounce timer for a given key.
   * When the timer fires, the batched update is executed.
   */
  private scheduleDebouncedUpdate(
    key: string,
    guildId: string,
    event: DomainEvent,
  ): void {
    const existing = this.debounceTimers.get(key);
    if (existing) clearTimeout(existing);

    const timer = setTimeout(() => {
      this.debounceTimers.delete(key);
      this.processBatchedUpdate(guildId, event).catch((err) => {
        console.error(
          `[AdminPanelUpdateListener] Error in batched update for ${key}:`,
          err,
        );
      });
    }, AdminPanelUpdateListener.DEBOUNCE_MS);

    this.debounceTimers.set(key, timer);
  }

  /**
   * Processes a batched update for all active sessions in a guild.
   * Groups sessions by channelId so channel.fetch is called once per unique channel,
   * then runs the channel groups concurrently with a bounded concurrency limit.
   */
  private async processBatchedUpdate(
    guildId: string,
    event: DomainEvent,
  ): Promise<void> {
    const eventType = event.eventType;

    // Rate-limit protection: skip if less than 200ms since last same-type update
    const throttleKey = `${guildId}:${eventType}`;
    if (this.shouldThrottle(throttleKey)) return;

    const sessions = this.sessionManager.getAllForGuild(guildId);

    if (sessions.length === 0) {
      console.log(
        `[AdminPanelUpdateListener] Event ${eventType} for guildId=${guildId}: no active sessions to update`,
      );
      return;
    }

    // Filter to sessions that should be updated for this event type
    const relevantSessions = sessions.filter((s) =>
      this.shouldUpdateForViewState(event, s.viewState),
    );

    // Group by channelId so channel.fetch is done once per unique channel.
    // Also skips sessions without a channelId or messageId.
    const channelGroupMap = new Map<string, AdminPanelSessionData[]>();
    for (const session of relevantSessions) {
      if (!session.channelId || !session.messageId) continue;
      const group = channelGroupMap.get(session.channelId);
      if (group) {
        group.push(session);
      } else {
        channelGroupMap.set(session.channelId, [session]);
      }
    }

    if (channelGroupMap.size === 0) return;

    const toRemove: Array<{ guildId: string; userId: string }> = [];
    const client = this.discordGateway.requireReadyClient() as Client;

    // Pre-build the main panel embed once if any MAIN-view session needs it,
    // rather than rebuilding per session.
    const isMainEvent =
      eventType === EVENT_TYPES.CURRENCY_CONFIG_CHANGED ||
      eventType === EVENT_TYPES.DICE_GAME_CONFIG_CHANGED;
    const hasMainViewSessions = relevantSessions.some(
      (s) => s.viewState === AdminPanelViewState.MAIN,
    );

    let sharedMainPanel: {
      embed: EmbedBuilder;
      rows: ActionRowBuilder<ButtonBuilder>[];
    } | null = null;
    if (isMainEvent && hasMainViewSessions) {
      sharedMainPanel = await this.buildMainPanelEmbed(guildId);
    }

    const channelEntries = Array.from(channelGroupMap.entries());

    await processWithConcurrencyLimit(
      channelEntries,
      async ([channelId, groupSessions]) => {
        const channel = await client.channels.fetch(channelId);
        if (!channel?.isTextBased()) return;
        const textChannel = channel as TextChannel;

        for (const session of groupSessions) {
          try {
            const message = await textChannel.messages.fetch(session.messageId!);

            if (isMainEvent && sharedMainPanel && session.viewState === AdminPanelViewState.MAIN) {
              await message.edit({
                embeds: [sharedMainPanel.embed],
                components: sharedMainPanel.rows,
              });
            } else {
              const existingEmbeds = message.embeds;
              if (existingEmbeds.length > 0) {
                const updatedEmbed = existingEmbeds[0].data;
                await message.edit({ embeds: [updatedEmbed] });
              }
            }

            console.log(
              `[AdminPanelUpdateListener] Event ${eventType} triggers update for ` +
              `guildId=${guildId}, userId=${session.userId}, viewState=${session.viewState}` +
              `, channelId=${channelId}`,
            );
          } catch (fetchErr) {
            console.log(
              `[AdminPanelUpdateListener] Failed to fetch message ${session.messageId} in channel ${channelId}: removing session`,
            );
            toRemove.push({ guildId: session.guildId, userId: session.userId });
          }
        }
      },
      AdminPanelUpdateListener.MAX_CONCURRENCY,
    );

    // Clean up stale sessions
    for (const { guildId: gId, userId: uId } of toRemove) {
      this.sessionManager.removeSession(gId, uId);
    }

    console.log(
      `[AdminPanelUpdateListener] Event ${eventType}: updated ${channelGroupMap.size} channel groups in guildId=${guildId}`,
    );
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
      const dispatchResult = await this.dispatchFacade.countActiveOrders(guildId);
      const dispatchCount = dispatchResult.isOk() ? dispatchResult.getValue() : 0;

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
            buttons.slice(i, i + 3),
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
      EVENT_TYPES.AI_CHANNEL_CONFIG_CHANGED,
      EVENT_TYPES.DISPATCH_AFTER_SALES_CONFIG_CHANGED,
      EVENT_TYPES.ESCORT_PRICING_CHANGED,
      EVENT_TYPES.ESCORT_CATALOG_CHANGED,
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

      case EVENT_TYPES.AI_CHANNEL_CONFIG_CHANGED:
        return viewState === AdminPanelViewState.AI_CHANNEL;

      case EVENT_TYPES.DISPATCH_AFTER_SALES_CONFIG_CHANGED:
        return viewState === AdminPanelViewState.DISPATCH_STAFF;

      case EVENT_TYPES.ESCORT_PRICING_CHANGED:
        return true;

      case EVENT_TYPES.ESCORT_CATALOG_CHANGED:
        return true;

      default:
        return false;
    }
  }

  /** Rate-limit: skip if less than minIntervalMs (default 200ms) since last update for same key. */
  private shouldThrottle(key: string, minIntervalMs = 200): boolean {
    const now = Date.now();
    const last = this.lastUpdateTimestamps.get(key) ?? 0;
    if (now - last < minIntervalMs) return true;
    this.lastUpdateTimestamps.set(key, now);

    // Periodic cleanup: evict entries older than 60s every 50 calls;
    // if still over capacity after time-based eviction, trim oldest entries.
    this.cleanupCounter++;
    if (this.cleanupCounter % 50 === 0) {
      const cutoff = now - 60_000;
      for (const [k, v] of this.lastUpdateTimestamps) {
        if (v < cutoff) this.lastUpdateTimestamps.delete(k);
      }
      if (this.lastUpdateTimestamps.size >= AdminPanelUpdateListener.MAX_THROTTLE_ENTRIES) {
        const sorted = [...this.lastUpdateTimestamps.entries()]
          .sort((a, b) => a[1] - b[1]);
        const evictCount = sorted.length - AdminPanelUpdateListener.MAX_THROTTLE_ENTRIES;
        for (let i = 0; i < evictCount; i++) {
          this.lastUpdateTimestamps.delete(sorted[i][0]);
        }
      }
    }

    return false;
  }
}
