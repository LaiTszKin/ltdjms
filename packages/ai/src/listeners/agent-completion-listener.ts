import type { DomainEvent, DiscordRuntimeGateway } from '@ltdjms/shared';
import type { AgentCompletedEvent, AgentFailedEvent } from '@ltdjms/shared';
import type { GuildTextBasedChannel } from 'discord.js';
import { MessageSplitter } from '../services/MessageSplitter.js';
import pino from 'pino';

const EMPTY_RESPONSE_FALLBACK = ':question: AI 沒有產生回應';

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
  ) {
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
    try {
      const channel = this.resolveMessageChannel(event.guildId, event.channelId);
      if (!channel) {
        this.logger.warn(
          { guildId: event.guildId, channelId: event.channelId },
          '無法取得可發送訊息的頻道',
        );
        return;
      }

      const parts = this.splitter.split(event.finalResponse);
      let sentAny = false;
      for (const message of parts) {
        if (!message?.trim()) {
          continue;
        }
        void channel.send(message).catch(() => undefined);
        sentAny = true;
      }

      if (!sentAny) {
        void channel.send(EMPTY_RESPONSE_FALLBACK).catch(() => undefined);
      }

      this.logger.info(
        { conversationId: event.conversationId, parts: parts.length },
        'Agent 完成，已發送最終回應',
      );
    } catch (error) {
      this.logger.error({ err: error }, '處理 Agent 完成事件時發生錯誤');
    }
  }

  private handleAgentFailed(event: AgentFailedEvent): void {
    try {
      const channel = this.resolveMessageChannel(event.guildId, event.channelId);
      if (!channel) {
        this.logger.warn(
          { guildId: event.guildId, channelId: event.channelId },
          '無法取得可發送訊息的頻道',
        );
        return;
      }

      void channel.send(`❌ ${event.reason}`).catch(() => undefined);
      this.logger.warn(
        { conversationId: event.conversationId, reason: event.reason },
        'Agent 失敗，已發送錯誤訊息',
      );
    } catch (error) {
      this.logger.error({ err: error }, '處理 Agent 失敗事件時發生錯誤');
    }
  }

  private resolveMessageChannel(
    guildId: string,
    channelId: string,
  ): GuildTextBasedChannel | null {
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
