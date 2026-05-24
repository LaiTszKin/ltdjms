import type { DomainEvent, DiscordRuntimeGateway } from '@ltdjms/shared';
import type { AgentCompletedEvent, AgentFailedEvent } from '@ltdjms/shared';
import type { GuildTextBasedChannel } from 'discord.js';
import { MessageSplitter } from '../services/MessageSplitter.js';
import { DiscordMarkdownStreamProcessor } from '../markdown/services/DiscordMarkdownStreamProcessor.js';
import { MarkdownHeadingSegmenter } from '../markdown/services/MarkdownHeadingSegmenter.js';
import type { CommonMarkValidator } from '../markdown/validation/CommonMarkValidator.js';
import type { MarkdownAutoFixer } from '../markdown/autofix/MarkdownAutoFixer.js';
import type { DiscordMarkdownSanitizer } from '../markdown/services/DiscordMarkdownSanitizer.js';
import type { DiscordMarkdownPaginator } from '../markdown/services/DiscordMarkdownPaginator.js';
import pino from 'pino';

const EMPTY_RESPONSE_FALLBACK = ':question: AI 沒有產生回應';

export interface AgentCompletionMarkdownPipeline {
  validator: CommonMarkValidator;
  autoFixer: MarkdownAutoFixer;
  sanitizer: DiscordMarkdownSanitizer;
  paginator: DiscordMarkdownPaginator;
}

/**
 * Sends Discord notifications when agent sessions complete or fail.
 * Matches Java AgentCompletionListener.
 */
export class AgentCompletionListener {
  private readonly splitter = new MessageSplitter();
  private readonly logger: pino.Logger;

  constructor(
    private readonly runtimeGateway: DiscordRuntimeGateway,
    logger?: pino.Logger,
    private readonly markdownPipeline?: AgentCompletionMarkdownPipeline,
  ) {
    this.logger = logger ?? pino({ name: 'agent-completion-listener' });
  }

  accept(event: DomainEvent | null | undefined): void {
    if (!event) {
      return;
    }

    if (event.eventType === 'agent_completed') {
      void this.handleAgentCompleted(event as AgentCompletedEvent).catch((error) => {
        this.logger.error({ err: error }, '處理 Agent 完成事件時發生錯誤');
      });
      return;
    }

    if (event.eventType === 'agent_failed') {
      this.handleAgentFailed(event as AgentFailedEvent);
    }
  }

  private async handleAgentCompleted(event: AgentCompletedEvent): Promise<void> {
    const channel = this.resolveMessageChannel(event.guildId, event.channelId);
    if (!channel) {
      this.logger.warn(
        { guildId: event.guildId, channelId: event.channelId },
        '無法取得可發送訊息的頻道',
      );
      return;
    }

    const pages = this.prepareFinalPages(event.finalResponse);
    let sentAny = false;
    for (const message of pages) {
      if (!message?.trim()) {
        continue;
      }
      await channel.send(message);
      sentAny = true;
    }

    if (!sentAny) {
      await channel.send(EMPTY_RESPONSE_FALLBACK);
    }

    this.logger.info(
      { conversationId: event.conversationId, parts: pages.length },
      'Agent 完成，已發送最終回應',
    );
  }

  private handleAgentFailed(event: AgentFailedEvent): void {
    // Errors are surfaced by AIChatMentionListener via streaming handler.onChunk(error).
    // Publishing agent_failed remains for observability; avoid duplicate Discord messages.
    this.logger.warn(
      { conversationId: event.conversationId, reason: event.reason },
      'Agent 失敗（已由 mention listener 通知使用者）',
    );
  }

  private prepareFinalPages(finalResponse: string): string[] {
    if (!this.markdownPipeline) {
      return this.splitter.split(finalResponse);
    }

    const processor = new DiscordMarkdownStreamProcessor(
      new MarkdownHeadingSegmenter(),
      this.markdownPipeline.validator,
      this.markdownPipeline.autoFixer,
      this.markdownPipeline.sanitizer,
      this.markdownPipeline.paginator,
    );
    const pages = [...processor.onChunk(finalResponse), ...processor.flush()];
    if (pages.length === 0) {
      return this.splitter.split(finalResponse);
    }
    return pages;
  }

  private resolveMessageChannel(guildId: string, channelId: string): GuildTextBasedChannel | null {
    if (!/^\d+$/.test(channelId)) {
      this.logger.warn({ channelId }, '無法解析頻道 ID');
      return null;
    }

    const channel =
      this.runtimeGateway.findGuildChannel(guildId, channelId) ??
      this.runtimeGateway.findThreadChannel(guildId, channelId);

    if (!channel || typeof channel !== 'object') {
      return null;
    }

    const candidate = channel as { isTextBased?: () => boolean };
    if (typeof candidate.isTextBased === 'function' && candidate.isTextBased()) {
      return channel as GuildTextBasedChannel;
    }

    return null;
  }
}
