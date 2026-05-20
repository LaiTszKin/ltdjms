import {
  type DomainEvent,
  type CurrencyConfigChangedEvent,
  type DiceGameConfigChangedEvent,
  type ProductChangedEvent,
  type RedemptionCodesGeneratedEvent,
  type AIAgentChannelConfigChangedEvent,
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type ProductRedemptionCompletedEvent,
  type AgentFailedEvent,
} from '@ltdjms/shared';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../session/types.js';

/**
 * Listens to domain events and updates active admin panel sessions.
 * Handles 13+ event types across different admin panel view states.
 * Matches Java AdminPanelUpdateListener.
 *
 * NOTE: The current implementation logs events and identifies which sessions
 * would be updated. To send actual updates to users, the session data should
 * be extended with an interaction hook or channel ID. This requires changes
 * to the session model and access to the Discord client, which will be
 * implemented when the session infrastructure is upgraded to Redis.
 */
export class AdminPanelUpdateListener {
  constructor(
    private readonly sessionManager: AdminPanelSessionManager,
  ) {}

  /**
   * Handles a domain event and updates relevant admin panels.
   * Only updates panels that are in the relevant view state.
   */
  async onEvent(event: DomainEvent): Promise<void> {
    if (!this.isAdminRelevantEvent(event)) return;

    const guildId = event.guildId;
    const eventType = this.getEventTypeName(event);
    const sessions = this.sessionManager.getAllForGuild(String(guildId));

    if (sessions.length === 0) {
      console.log(
        `[AdminPanelUpdateListener] Event ${eventType} for guildId=${guildId}: no active sessions to update`,
      );
      return;
    }

    let updatedCount = 0;
    for (const session of sessions) {
      try {
        const shouldUpdate = this.shouldUpdateForViewState(event, session.viewState);
        if (!shouldUpdate) continue;

        updatedCount++;

        // In a full implementation, this would:
        // 1. Query fresh data based on event type
        // 2. Build new embed using AdminPanelViewFactory
        // 3. Call editReply() on the session's stored interaction hook
        //
        // When Redis-based session infrastructure is available, the session
        // should store an interaction token or channel ID so the listener
        // can push updates without needing the original interaction object.

        console.log(
          `[AdminPanelUpdateListener] Event ${eventType} triggers update for ` +
          `guildId=${guildId}, userId=${session.userId}, viewState=${session.viewState}`,
        );
      } catch (err) {
        console.error(
          `[AdminPanelUpdateListener] Error updating panel for guildId=${guildId}, userId=${session.userId}:`,
          err,
        );
      }
    }

    if (updatedCount > 0) {
      console.log(
        `[AdminPanelUpdateListener] Event ${eventType}: updated ${updatedCount}/${sessions.length} active sessions in guildId=${guildId}`,
      );
    }
  }

  /**
   * Returns a human-readable name for the event type for logging.
   */
  private getEventTypeName(event: DomainEvent): string {
    if (this.isCurrencyConfigChanged(event)) return 'CurrencyConfigChanged';
    if (this.isDiceConfigChanged(event)) return 'DiceGameConfigChanged';
    if (this.isProductChanged(event)) return 'ProductChanged';
    if (this.isCodesGenerated(event)) return 'RedemptionCodesGenerated';
    if (this.isAIAgentChannelConfigChanged(event)) return 'AIAgentChannelConfigChanged';
    if (this.isBalanceChanged(event)) return 'BalanceChanged';
    if (this.isGameTokenChanged(event)) return 'GameTokenChanged';
    if (this.isProductRedemptionCompleted(event)) return 'ProductRedemptionCompleted';
    if (this.isAgentFailed(event)) return 'AgentFailed';
    return 'Unknown';
  }

  private isAdminRelevantEvent(event: DomainEvent): boolean {
    return (
      this.isCurrencyConfigChanged(event) ||
      this.isDiceConfigChanged(event) ||
      this.isProductChanged(event) ||
      this.isCodesGenerated(event) ||
      this.isAIAgentChannelConfigChanged(event) ||
      this.isBalanceChanged(event) ||
      this.isGameTokenChanged(event) ||
      this.isProductRedemptionCompleted(event) ||
      this.isAgentFailed(event)
    );
  }

  private shouldUpdateForViewState(
    event: DomainEvent,
    viewState: AdminPanelViewState,
  ): boolean {
    if (this.isCurrencyConfigChanged(event)) {
      // Currency config change always triggers main panel refresh
      return true;
    }

    if (this.isDiceConfigChanged(event)) {
      // Only relevant when viewing game settings
      return viewState === AdminPanelViewState.MAIN;
    }

    if (this.isProductChanged(event)) {
      // Only relevant when viewing product list or detail
      return (
        viewState === AdminPanelViewState.PRODUCT_LIST ||
        viewState === AdminPanelViewState.PRODUCT_DETAIL
      );
    }

    if (this.isCodesGenerated(event)) {
      return viewState === AdminPanelViewState.PRODUCT_CODE_LIST;
    }

    if (this.isAIAgentChannelConfigChanged(event)) {
      // AI config changes are always admin-relevant
      return true;
    }

    if (this.isBalanceChanged(event)) {
      // Balance changes are always admin-relevant (user panels)
      return true;
    }

    if (this.isGameTokenChanged(event)) {
      // Token changes are always admin-relevant (user panels)
      return true;
    }

    if (this.isProductRedemptionCompleted(event)) {
      return viewState === AdminPanelViewState.PRODUCT_CODE_LIST;
    }

    if (this.isAgentFailed(event)) {
      // Agent failures are always admin-relevant for monitoring
      return true;
    }

    return false;
  }

  private isCurrencyConfigChanged(event: DomainEvent): event is CurrencyConfigChangedEvent {
    return 'currencyName' in event && 'currencyIcon' in event;
  }

  private isDiceConfigChanged(event: DomainEvent): event is DiceGameConfigChangedEvent {
    return 'gameType' in event;
  }

  private isProductChanged(event: DomainEvent): event is ProductChangedEvent {
    return 'productId' in event && 'operationType' in event;
  }

  private isCodesGenerated(event: DomainEvent): event is RedemptionCodesGeneratedEvent {
    return 'productId' in event && 'count' in event;
  }

  private isAIAgentChannelConfigChanged(event: DomainEvent): event is AIAgentChannelConfigChangedEvent {
    return 'agentEnabled' in event && 'changedAt' in event;
  }

  private isBalanceChanged(event: DomainEvent): event is BalanceChangedEvent {
    return 'newBalance' in event;
  }

  private isGameTokenChanged(event: DomainEvent): event is GameTokenChangedEvent {
    return 'newTokens' in event;
  }

  private isProductRedemptionCompleted(event: DomainEvent): event is ProductRedemptionCompletedEvent {
    return 'transaction' in event;
  }

  private isAgentFailed(event: DomainEvent): event is AgentFailedEvent {
    return 'reason' in event;
  }
}
