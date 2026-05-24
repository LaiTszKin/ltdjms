import type { DomainEvent, DiscordRuntimeGateway } from '@ltdjms/shared';
import type {
  LangChain4jToolExecutedEvent,
  LangChain4jToolExecutionStartedEvent,
} from '@ltdjms/shared';
import type { GuildTextBasedChannel, Message } from 'discord.js';
import { MessageSplitter } from '../services/MessageSplitter.js';
import pino from 'pino';

/**
 * Sends Discord notifications when LangChain agent tools start/complete.
 * Matches Java ToolExecutionListener.
 */
export class ToolExecutionListener {
  private readonly splitter = new MessageSplitter();
  private readonly logger: pino.Logger;

  constructor(
    private readonly runtimeGateway: DiscordRuntimeGateway,
    logger?: pino.Logger,
  ) {
    this.logger = logger ?? pino({ name: 'tool-execution-listener' });
  }

  accept(event: DomainEvent | null | undefined): void {
    if (!event) {
      return;
    }

    if (event.eventType === 'langchain4j_tool_execution_started') {
      this.handleToolExecutionStarted(event as LangChain4jToolExecutionStartedEvent);
      return;
    }

    if (event.eventType === 'langchain4j_tool_executed') {
      this.handleToolExecuted(event as LangChain4jToolExecutedEvent);
    }
  }

  private handleToolExecutionStarted(event: LangChain4jToolExecutionStartedEvent): void {
    try {
      const channel = this.resolveMessageChannel(event.guildId, event.channelId);
      if (!channel) {
        this.logger.warn(
          { guildId: event.guildId, channelId: event.channelId },
          '無法取得工具通知頻道',
        );
        return;
      }

      const message = `🤖 我先執行這一步：正在呼叫工具「${event.toolName}」...`;
      void this.sendChunks(channel, message);
    } catch (error) {
      this.logger.error({ err: error }, '處理工具開始事件時發生錯誤');
    }
  }

  private handleToolExecuted(event: LangChain4jToolExecutedEvent): void {
    try {
      const channel = this.resolveMessageChannel(event.guildId, event.channelId);
      if (!channel) {
        this.logger.warn(
          { guildId: event.guildId, channelId: event.channelId },
          '無法取得工具通知頻道',
        );
        return;
      }

      const message = event.success
        ? `✅ 工具「${event.toolName}」執行成功`
        : `❌ 工具「${event.toolName}」執行失敗：${event.result}`;

      void this.sendChunks(channel, message);
    } catch (error) {
      this.logger.error({ err: error }, '處理工具執行事件時發生錯誤');
    }
  }

  private resolveMessageChannel(
    guildId: string,
    channelId: string,
  ): GuildTextBasedChannel | null {
    const channel =
      this.runtimeGateway.findGuildChannel(guildId, channelId) ??
      this.runtimeGateway.findThreadChannel(guildId, channelId);

    if (!channel || typeof channel !== 'object') {
      return null;
    }

    const candidate = channel as { isTextBased?: () => boolean; send?: unknown };
    if (typeof candidate.isTextBased === 'function' && candidate.isTextBased()) {
      return channel as GuildTextBasedChannel;
    }

    return null;
  }

  private async sendChunks(channel: GuildTextBasedChannel, message: string): Promise<void> {
    for (const chunk of this.splitter.split(message)) {
      if (chunk.trim()) {
        await channel.send(chunk).catch(() => undefined);
      }
    }
  }
}
