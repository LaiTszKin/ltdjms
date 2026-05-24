import type { DomainEvent } from '@ltdjms/shared';
import type { AgentCompletedEvent, AgentFailedEvent } from '@ltdjms/shared';
import pino from 'pino';

/**
 * Observes agent completion/failure events for logging.
 * Final Discord delivery is handled synchronously by AIChatMentionListener.
 */
export class AgentCompletionListener {
  private readonly logger: pino.Logger;

  constructor(logger?: pino.Logger) {
    this.logger = logger ?? pino({ name: 'agent-completion-listener' });
  }

  accept(event: DomainEvent | null | undefined): void {
    if (!event) {
      return;
    }

    if (event.eventType === 'agent_completed') {
      this.handleAgentCompleted(event as AgentCompletedEvent);
      return;
    }

    if (event.eventType === 'agent_failed') {
      this.handleAgentFailed(event as AgentFailedEvent);
    }
  }

  private handleAgentCompleted(event: AgentCompletedEvent): void {
    const responsePreview = event.finalResponse?.trim();
    this.logger.info(
      {
        conversationId: event.conversationId,
        hasResponse: Boolean(responsePreview),
        responseLength: responsePreview?.length ?? 0,
      },
      'Agent 完成（最終回覆已由 mention listener 同步發送）',
    );

    if (!responsePreview) {
      this.logger.warn(
        { conversationId: event.conversationId },
        'Agent 完成但 finalResponse 為空（mention listener 應已發送 fallback）',
      );
    }
  }

  private handleAgentFailed(event: AgentFailedEvent): void {
    this.logger.warn(
      { conversationId: event.conversationId, reason: event.reason },
      'Agent 失敗（已由 mention listener 通知使用者）',
    );
  }
}
