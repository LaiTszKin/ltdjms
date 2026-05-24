import { type Message, type TextChannel } from 'discord.js';
import {
  type AIChatService,
  type StreamingResponseHandler,
  StreamChunkType,
  Route,
  type Decision,
} from '../services/ai-chat-service.js';
import {
  type AIChatMentionRoutingDecision,
  resolveCategoryId,
  resolveRestrictionChannelId,
} from '../services/routing/routing-decision.js';
import { DomainError } from '@ltdjms/shared';
import { MessageSplitter } from '../services/MessageSplitter.js';
import { MessageChunkAccumulator } from '../services/message-chunk-accumulator.js';
import { ReasoningMessageTracker } from './reasoning-message-tracker.js';
import { AGENT_NON_THREAD_MESSAGE_ID } from '../services/conversation-constants.js';
import { prepareAgentFinalPages } from '../markdown/services/markdown-pipeline-factory.js';

const SPOILER_PREFIX = '-# ';
const EMPTY_RESPONSE_FALLBACK = ':question: AI 沒有產生回應';

/**
 * Listens for @bot mentions and routes them to the AI chat/agent system.
 * Matches Java AIChatMentionListener.
 */
export class AIChatMentionListener {
  private splitter = new MessageSplitter();

  constructor(
    private readonly routingDecision: AIChatMentionRoutingDecision,
    private readonly aiChatService: AIChatService,
    private readonly botUserId: string,
    private readonly showReasoning: boolean = false,
    private readonly enableMarkdownValidation: boolean = true,
    private readonly streamingBypassValidation: boolean = false,
  ) {}

  async onMessageCreate(message: Message): Promise<void> {
    try {
      if (message.author.id === this.botUserId) return;
      if (!message.guild) return;

      const botMentioned = message.mentions.has(this.botUserId);
      if (!botMentioned) return;

      const userMessage = this.extractUserMessage(message);
      const guildId = message.guild.id;
      const channelId = message.channel.id;
      const restrictionChannelId = resolveRestrictionChannelId(message.channel);
      const categoryId = resolveCategoryId(message.channel);

      const decision: Decision = await this.routingDecision.decide(
        guildId,
        channelId,
        restrictionChannelId,
        categoryId,
      );

      if (decision.route === Route.DENY) {
        return;
      }

      const thinkingMsg = await message.reply(':thought_balloon: AI 正在思考...');
      const streamProcessed = this.enableMarkdownValidation && !this.streamingBypassValidation;

      if (decision.route === Route.AGENT_ROUTE) {
        await this.handleAgentStreamingResponse(
          message,
          guildId,
          channelId,
          userMessage,
          thinkingMsg,
        );
        return;
      }

      await this.handleChatStreamingResponse(
        message,
        guildId,
        channelId,
        userMessage,
        thinkingMsg,
        streamProcessed,
      );
    } catch (error) {
      console.error(
        `[AIChatMentionListener] Error handling message: ${error instanceof Error ? error.message : String(error)}`,
      );
      try {
        await message.reply('抱歉，處理你的請求時發生了錯誤。請稍後再試。');
      } catch {
        // Ignore reply failure
      }
    }
  }

  private async handleAgentStreamingResponse(
    message: Message,
    guildId: string,
    channelId: string,
    userMessage: string,
    thinkingMsg: Message,
  ): Promise<void> {
    const streamProcessed = this.enableMarkdownValidation && !this.streamingBypassValidation;
    const tracker = new ReasoningMessageTracker();
    tracker.setInitialMessage(thinkingMsg);
    const finalContentChunks: string[] = [];
    let completionProcessed = false;
    let isFirstChunk = true;

    const handler: StreamingResponseHandler = {
      onChunk: async (
        chunk: string,
        isComplete: boolean,
        error: DomainError | null,
        chunkType?: StreamChunkType,
      ) => {
        if (error) {
          await thinkingMsg.edit(this.mapErrorToUserMessage(error)).catch(() => {});
          return;
        }

        const type = chunkType ?? StreamChunkType.CONTENT;
        if (chunk) {
          if (type === StreamChunkType.REASONING) {
            if (this.showReasoning) {
              const formatted = this.formatAsSpoiler(chunk);
              if (isFirstChunk) {
                await thinkingMsg.edit(formatted).catch(() => {});
                isFirstChunk = false;
              } else {
                const msg = await this.sendToChannel(message, formatted);
                if (msg) tracker.addReasoningMessage(msg);
              }
            }
          } else if (type === StreamChunkType.CONTENT) {
            if (chunk.trim()) {
              finalContentChunks.push(chunk);
            }
          } else if (type === StreamChunkType.TOOL_INTENT) {
            await this.sendToolIntentMessage(message, chunk);
          }
        }

        if (!isComplete || completionProcessed) {
          return;
        }
        completionProcessed = true;

        await tracker.deleteAll(async () => {
          await this.sendAgentFinalContent(message, finalContentChunks, streamProcessed);
        });
      },
    };

    await this.aiChatService.generateStreamingResponseWithId(
      guildId,
      channelId,
      message.author.id,
      userMessage,
      AGENT_NON_THREAD_MESSAGE_ID,
      handler,
      true,
    );
  }

  private async sendAgentFinalContent(
    message: Message,
    finalContentChunks: string[],
    streamProcessed: boolean,
  ): Promise<void> {
    if (finalContentChunks.length === 0) {
      await this.sendToChannel(message, EMPTY_RESPONSE_FALLBACK);
      return;
    }

    if (!streamProcessed) {
      const fullContent = finalContentChunks.join('').trim();
      if (!fullContent) {
        await this.sendToChannel(message, EMPTY_RESPONSE_FALLBACK);
        return;
      }

      const pages = prepareAgentFinalPages(fullContent, this.splitter);
      if (pages.length === 0) {
        await this.sendToChannel(message, EMPTY_RESPONSE_FALLBACK);
        return;
      }

      for (const page of pages) {
        if (!page?.trim()) {
          continue;
        }
        await this.sendToChannel(message, page);
      }
      return;
    }

    let sent = false;
    for (const chunk of finalContentChunks) {
      if (!chunk?.trim()) {
        continue;
      }

      const pages = this.splitter.split(chunk);
      for (const page of pages) {
        if (!page?.trim()) {
          continue;
        }
        await this.sendToChannel(message, page);
        sent = true;
      }
    }

    if (!sent) {
      await this.sendToChannel(message, EMPTY_RESPONSE_FALLBACK);
    }
  }

  private async handleChatStreamingResponse(
    message: Message,
    guildId: string,
    channelId: string,
    userMessage: string,
    thinkingMsg: Message,
    streamProcessed: boolean,
  ): Promise<void> {
    const tracker = new ReasoningMessageTracker();
    tracker.setInitialMessage(thinkingMsg);
    const contentAccumulator = new MessageChunkAccumulator();
    let completed = false;
    let isFirstChunk = true;
    let hasReasoning = false;
    const firstContentSent = { value: false };
    const bufferContentStarted = { value: false };

    const handler: StreamingResponseHandler = {
      onChunk: async (
        chunk: string,
        isComplete: boolean,
        error: DomainError | null,
        chunkType?: StreamChunkType,
      ) => {
        if (error) {
          await thinkingMsg.edit(this.mapErrorToUserMessage(error)).catch(() => {});
          return;
        }

        if (chunk && chunk.trim()) {
          const type = chunkType ?? StreamChunkType.CONTENT;

          if (type === StreamChunkType.REASONING) {
            if (!this.showReasoning) {
              return;
            }
            const formatted = this.formatAsSpoiler(chunk);
            hasReasoning = true;
            if (isFirstChunk) {
              await thinkingMsg.edit(formatted).catch(() => {});
              isFirstChunk = false;
            } else {
              const msg = await this.sendToChannel(message, formatted);
              if (msg) tracker.addReasoningMessage(msg);
            }
          } else if (type === StreamChunkType.CONTENT) {
            if (streamProcessed) {
              const allowEditThinking = !(this.showReasoning && hasReasoning);
              await this.sendStreamingContentChunk(
                message,
                thinkingMsg,
                chunk,
                firstContentSent,
                allowEditThinking,
              );
            } else {
              const readySegments = contentAccumulator.accumulate(chunk);
              for (const segment of readySegments) {
                await this.sendAccumulatedContentSegment(
                  message,
                  thinkingMsg,
                  segment,
                  hasReasoning,
                  bufferContentStarted,
                );
              }
            }
          }
        }

        if (!isComplete || completed) {
          return;
        }
        completed = true;

        if (streamProcessed) {
          if (!firstContentSent.value) {
            await thinkingMsg.edit(':question: AI 沒有產生回應').catch(() => {});
          }
          return;
        }

        const remainingContent = contentAccumulator.drain();
        if (!remainingContent && !bufferContentStarted.value) {
          await thinkingMsg.edit(':question: AI 沒有產生回應').catch(() => {});
          return;
        }

        if (remainingContent) {
          await this.sendAccumulatedContentSegment(
            message,
            thinkingMsg,
            remainingContent,
            hasReasoning,
            bufferContentStarted,
          );
        }
      },
    };

    await this.aiChatService.generateStreamingResponse(
      guildId,
      channelId,
      message.author.id,
      userMessage,
      handler,
    );
  }

  private async sendAccumulatedContentSegment(
    message: Message,
    thinkingMessage: Message,
    content: string,
    hasReasoning: boolean,
    contentStarted: { value: boolean },
  ): Promise<void> {
    if (this.showReasoning && hasReasoning) {
      await this.sendBufferedContent(message, null, content);
      contentStarted.value = true;
      return;
    }

    if (!contentStarted.value) {
      contentStarted.value = true;
      await this.sendBufferedContent(message, thinkingMessage, content);
      return;
    }

    await this.sendBufferedContent(message, null, content);
  }

  private async sendBufferedContent(
    message: Message,
    thinkingMessage: Message | null,
    content: string,
  ): Promise<void> {
    const parts = this.splitter.split(content);
    if (parts.length === 0) {
      return;
    }

    if (thinkingMessage) {
      await thinkingMessage.edit(parts[0]).catch(() => {});
    } else {
      await this.sendToChannel(message, parts[0]);
    }
    for (let i = 1; i < parts.length; i++) {
      await this.sendToChannel(message, parts[i]);
    }
  }

  private async sendToolIntentMessage(message: Message, content: string): Promise<void> {
    for (const part of this.splitter.split(content)) {
      if (part.trim()) {
        await this.sendToChannel(message, part);
      }
    }
  }

  private async sendStreamingContentChunk(
    message: Message,
    thinkingMessage: Message,
    chunk: string,
    firstContentSent: { value: boolean },
    allowEditThinking: boolean,
  ): Promise<void> {
    if (!firstContentSent.value) {
      firstContentSent.value = true;
      if (allowEditThinking) {
        await thinkingMessage.edit(chunk).catch(() => {});
        return;
      }
    }
    await this.sendToChannel(message, chunk);
  }

  private formatAsSpoiler(content: string): string {
    if (!content) {
      return content;
    }
    if (content.startsWith(SPOILER_PREFIX)) {
      return content;
    }
    return SPOILER_PREFIX + content;
  }

  private extractUserMessage(message: Message): string {
    const content = message.content.replace(/<@!?(\d+)>/g, '').trim();
    return content || '你好';
  }

  private mapErrorToUserMessage(error: DomainError): string {
    switch (error.category) {
      case 'AI_SERVICE_AUTH_FAILED':
        return ':x: AI 服務認證失敗，請聯絡管理員';
      case 'AI_SERVICE_RATE_LIMITED':
        return ':timer: AI 服務暫時忙碌，請稍後再試';
      case 'AI_SERVICE_TIMEOUT':
        return ':hourglass: AI 服務連線逾時，請稍後再試';
      case 'AI_SERVICE_UNAVAILABLE':
        return ':warning: AI 服務暫時無法使用';
      case 'AI_RESPONSE_EMPTY':
        return ':question: AI 沒有產生回應';
      case 'AI_RESPONSE_INVALID':
        return ':warning: AI 回應格式錯誤';
      default:
        return `:warning: 發生錯誤：${error.message}`;
    }
  }

  private async sendToChannel(message: Message, content: string): Promise<Message | null> {
    if (!message.channel.isTextBased()) {
      return null;
    }
    return (message.channel as TextChannel).send(content);
  }
}
