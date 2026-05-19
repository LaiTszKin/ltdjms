import {
  type DomainEvent,
  type BalanceChangedEvent,
  type GameTokenChangedEvent,
  type CurrencyConfigChangedEvent,
} from '@ltdjms/shared';
import { PanelSessionManager } from '../../session/PanelSessionManager.js';
import { MemberInfoFacade } from '../../facades/MemberInfoFacade.js';

/**
 * Listens to domain events and updates active user panel sessions.
 * Events: BalanceChangedEvent, GameTokenChangedEvent, CurrencyConfigChangedEvent.
 * Matches Java UserPanelUpdateListener.
 */
export class UserPanelUpdateListener {
  constructor(
    private readonly sessionManager: PanelSessionManager,
    private readonly memberInfoFacade: MemberInfoFacade,
  ) {}

  /**
   * Handles a domain event and updates relevant user panels.
   * This is called by DomainEventPublisher for each registered listener.
   */
  async onEvent(event: DomainEvent): Promise<void> {
    // Only handle events relevant to user panels
    if (!this.isRelevantEvent(event)) return;

    const guildId = event.guildId;

    // Get all active sessions for this guild
    const sessions = this.sessionManager.getAllForGuild(guildId);

    if (sessions.length === 0) return;

    // Determine affected user IDs based on event type
    let affectedUserIds: number[] = [];

    if (this.isBalanceChanged(event)) {
      affectedUserIds = [event.userId];
    } else if (this.isTokenChanged(event)) {
      affectedUserIds = [event.userId];
    } else if (this.isConfigChanged(event)) {
      // Currency config change affects all users in guild
      affectedUserIds = sessions.map((s) => s.userId);
    }

    // Update each affected session
    for (const session of sessions) {
      if (!affectedUserIds.includes(session.userId)) continue;

      try {
        // Refresh and update (fire-and-forget, errors are caught)
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
      } catch (err) {
        console.error(
          `[UserPanelUpdateListener] Error updating panel for guildId=${guildId}, userId=${session.userId}:`,
          err,
        );
      }
    }
  }

  private isRelevantEvent(event: DomainEvent): boolean {
    return (
      this.isBalanceChanged(event) ||
      this.isTokenChanged(event) ||
      this.isConfigChanged(event)
    );
  }

  private isBalanceChanged(event: DomainEvent): event is BalanceChangedEvent {
    return 'newBalance' in event && 'userId' in event && !('currencyName' in event);
  }

  private isTokenChanged(event: DomainEvent): event is GameTokenChangedEvent {
    return 'newTokens' in event;
  }

  private isConfigChanged(event: DomainEvent): event is CurrencyConfigChangedEvent {
    return 'currencyName' in event && 'currencyIcon' in event;
  }
}
