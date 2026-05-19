import {
  type DomainEvent,
  type CurrencyConfigChangedEvent,
  type DiceGameConfigChangedEvent,
  type ProductChangedEvent,
  type RedemptionCodesGeneratedEvent,
  type AIAgentChannelConfigChangedEvent,
  type AIChannelConfigChangedEvent,
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type ProductRedemptionCompletedEvent,
  type AgentFailedEvent,
  type DispatchAfterSalesConfigChangedEvent,
  type EscortPricingChangedEvent,
  type EscortCatalogChangedEvent,
} from '@ltdjms/shared';
import { AdminPanelSessionManager } from '../../session/AdminPanelSessionManager.js';
import { AdminPanelViewState } from '../../session/types.js';

/**
 * Listens to domain events and updates active admin panel sessions.
 * Handles 13+ event types across different admin panel view states.
 * Matches Java AdminPanelUpdateListener.
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
    const sessions = this.sessionManager.getAllForGuild(guildId);

    if (sessions.length === 0) return;

    for (const session of sessions) {
      try {
        const shouldUpdate = this.shouldUpdateForViewState(event, session.viewState);
        if (!shouldUpdate) continue;

        // In a full implementation, this would:
        // 1. Query fresh data based on event type
        // 2. Build new embed
        // 3. Call editReply() on the session's interaction hook
        console.log(
          `[AdminPanelUpdateListener] Would update panel for guildId=${guildId}, userId=${session.userId}, viewState=${session.viewState}`,
        );
      } catch (err) {
        console.error(
          `[AdminPanelUpdateListener] Error updating panel:`,
          err,
        );
        // If updating fails, we could remove the session
      }
    }
  }

  private isAdminRelevantEvent(event: DomainEvent): boolean {
    return (
      this.isCurrencyConfigChanged(event) ||
      this.isDiceConfigChanged(event) ||
      this.isProductChanged(event) ||
      this.isCodesGenerated(event) ||
      this.isAIAgentChannelConfigChanged(event) ||
      this.isAIChannelConfigChanged(event) ||
      this.isBalanceChanged(event) ||
      this.isGameTokenChanged(event) ||
      this.isProductRedemptionCompleted(event) ||
      this.isAgentFailed(event) ||
      this.isDispatchAfterSalesConfigChanged(event) ||
      this.isEscortPricingChanged(event) ||
      this.isEscortCatalogChanged(event)
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

    if (this.isAIChannelConfigChanged(event)) {
      // AI channel allowlist changes trigger main panel refresh
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

    if (this.isDispatchAfterSalesConfigChanged(event)) {
      // After-sales config changes trigger main panel refresh
      return true;
    }

    if (this.isEscortPricingChanged(event)) {
      // Escort pricing changes trigger main panel refresh
      return true;
    }

    if (this.isEscortCatalogChanged(event)) {
      // Escort catalog changes trigger main panel refresh
      return viewState === AdminPanelViewState.MAIN;
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

  private isAIChannelConfigChanged(event: DomainEvent): event is AIChannelConfigChangedEvent {
    return 'channelId' in event && 'allowed' in event;
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

  private isDispatchAfterSalesConfigChanged(event: DomainEvent): event is DispatchAfterSalesConfigChangedEvent {
    return 'staffUserId' in event && 'operationType' in event;
  }

  private isEscortPricingChanged(event: DomainEvent): event is EscortPricingChangedEvent {
    return 'optionCode' in event && 'priceTwd' in event;
  }

  private isEscortCatalogChanged(event: DomainEvent): event is EscortCatalogChangedEvent {
    return 'optionCode' in event && 'operationType' in event;
  }
}
