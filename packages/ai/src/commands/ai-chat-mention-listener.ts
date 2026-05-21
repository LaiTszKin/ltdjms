import { type Message } from 'discord.js';
import {
  type AIChatService,
  type StreamingResponseHandler,
  StreamChunkType,
  Route,
  type Decision,
} from '../services/ai-chat-service.js';
import { type AIChatMentionRoutingDecision, resolveCategoryId, resolveRestrictionChannelId } from '../services/routing/routing-decision.js';
import { DomainError } from '@ltdjms/shared';
import { MessageSplitter } from '../services/MessageSplitter.js';

/**
 * Tracks reasoning messages for cleanup after streaming completes.
 * Matches Java ReasoningMessageTracker.
 */
class ReasoningMessageTracker {
  private initialMessage: Message | null = null;
  private reasoningMessages: Message[] = [];
  private deletionRequested = false;

  setInitialMessage(message: Message): void {
    this.initialMessage = message;
  }

  addReasoningMessage(message: Message): void {
    this.reasoningMessages.push(message);
  }

  /**
   * Deletes only reasoning messages, keeping the initial message intact.
   */
  async deleteReasoningMessages(): Promise<void> {
    await Promise.allSettled(
      this.reasoningMessages.map((msg) =>
        msg.delete().catch(() => {
          // Ignore deletion failures
        }),
      ),
    );
  }

  /**
   * Deletes all tracked reasoning messages.
   */
  async deleteAll(): Promise<void> {
    if (this.deletionRequested) return;
    this.deletionRequested = true;

    const toDelete: Message[] = [];

    if (this.initialMessage) {
      toDelete.push(this.initialMessage);
    }
    toDelete.push(...this.reasoningMessages);

    await Promise.allSettled(
      toDelete.map((msg) =>
        msg.delete().catch(() => {
          // Ignore deletion failures
        }),
      ),
    );
  }
}

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
    private readonly streamingBypassValidation: boolean = false,
  ) {}

  /**
   * Handles a message creation event (discord.js messageCreate).
   * Filters non-bot mentions, self-messages, and DMs.
   */
  async onMessageCreate(message: Message): Promise<void> {
    try {
      // Filter self-messages
      if (message.author.id === this.botUserId) return;

      // Filter DM
      if (!message.guild) return;

      // Check if bot is mentioned
      const botMentioned = message.mentions.has(this.botUserId);
      if (!botMentioned) return;

      // Get user message (default to "你好" if only mention)
      const userMessage = this.extractUserMessage(message);

      // Get routing parameters
      const guildId = message.guild.id;
      const channelId = message.channel.id;
      const restrictionChannelId = resolveRestrictionChannelId(message.channel);

      // Resolve category ID (thread -> parent channel -> category)
      const categoryId = resolveCategoryId(message.channel);

      // Get routing decision
      const decision: Decision = await this.routingDecision.decide(
        guildId,
        restrictionChannelId,
        categoryId,
      );

      // DENY — silent return
      if (decision.route === Route.DENY) {
        return;
      }

      // Route to appropriate handler
      if (decision.route === Route.AGENT_ROUTE) {
        await this.handleAgentStreamingResponse(
          message,
          guildId,
          channelId,
          userMessage,
        );
      } else {
        await this.handleChatStreamingResponse(
          message,
          guildId,
          channelId,
          userMessage,
        );
      }
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

  /**
   * Safely sends a message to the channel if it's text-based.
   * Returns null if the channel cannot be sent to.
   */
  private async sendToChannel(
    message: Message,
    content: string,
  ): Promise<Message | null> {
    try {
      if (message.channel.isTextBased()) {
        return await (message.channel as { send: (c: string) => Promise<Message> }).send(content) as Message;
      }
    } catch {
      // Ignore send failures
    }
    return null;
  }

  /**
   * Handles Agent-mode streaming response.
   * CONTENT chunks are buffered and sent after tool execution completes.
   * Reasoning messages are deleted on completion.
   */
  private async handleAgentStreamingResponse(
    message: Message,
    guildId: string,
    channelId: string,
    userMessage: string,
  ): Promise<void> {
    const tracker = new ReasoningMessageTracker();
    const pendingContent: string[] = [];
    let completionProcessed = false;

    // Send initial "thinking" message
    const thinkingMsg = await message.reply(':thought_balloon: AI 正在思考...');
    tracker.setInitialMessage(thinkingMsg);

    const handler: StreamingResponseHandler = {
      onChunk: async (chunk: string, isComplete: boolean, error: DomainError | null, chunkType?: StreamChunkType) => {
        if (error) {
          const errorMsg = this.mapErrorToUserMessage(error);
          thinkingMsg.edit(errorMsg).catch(() => {});
          return;
        }

        const type = chunkType ?? StreamChunkType.CONTENT;

        switch (type) {
          case StreamChunkType.REASONING:
            if (this.showReasoning && chunk) {
              // Send reasoning as spoiler
              const msg = await this.sendToChannel(message, `-# ||${chunk}||`);
              if (msg) tracker.addReasoningMessage(msg);
            }
            break;

          case StreamChunkType.TOOL_INTENT:
            // Show tool execution status to the user as a compact note
            if (chunk) {
              await this.sendToChannel(message, `-# ${chunk}`);
            }
            break;

          case StreamChunkType.CONTENT:
            if (chunk) {
              pendingContent.push(chunk);
            }
            break;
        }

        if (isComplete && !completionProcessed) {
          completionProcessed = true;

          // Delete reasoning messages first, then edit thinking message with final content
          await tracker.deleteReasoningMessages();
          if (pendingContent.length === 0) {
            thinkingMsg.edit(':question: AI 沒有產生回應').catch(() => {});
            return;
          }

          if (this.streamingBypassValidation) {
            // Content was not pre-paginated — split with MessageSplitter
            const fullContent = pendingContent.join('');
            const pages = this.splitter.split(fullContent);
            // P2-41: Fallback for empty split result with non-empty content
            if (pages.length === 0 && fullContent) {
              thinkingMsg.edit(fullContent).catch(() => {});
            } else {
              thinkingMsg.edit(pages[0]).catch(() => {});
              for (let i = 1; i < pages.length; i++) {
                await this.sendToChannel(message, pages[i]);
              }
            }
          } else {
            // Content already paginated by markdown validation pipeline
            thinkingMsg.edit(pendingContent[0]).catch(() => {});
            for (let i = 1; i < pendingContent.length; i++) {
              await this.sendToChannel(message, pendingContent[i]);
            }
          }
        }
      },
    };

    await this.aiChatService.generateStreamingResponse(
      guildId,
      channelId,
      message.author.id,
      userMessage,
      handler,
      true, // agentEnabled — loads agent prompts and enables tool-calling model
    );
  }

  /**
   * Handles Chat-mode (non-Agent) streaming response.
   * CONTENT chunks are sent in real-time.
   */
  private async handleChatStreamingResponse(
    message: Message,
    guildId: string,
    channelId: string,
    userMessage: string,
  ): Promise<void> {
    // Send initial thinking message
    const thinkingMsg = await message.reply(
      ':thought_balloon: AI 正在思考...',
    );
    const tracker = new ReasoningMessageTracker();
    tracker.setInitialMessage(thinkingMsg);
    let hasSentFirstContent = false;

    const handler: StreamingResponseHandler = {
      onChunk: async (chunk: string, isComplete: boolean, error: DomainError | null, chunkType?: StreamChunkType) => {
        if (error) {
          const errorMsg = this.mapErrorToUserMessage(error);
          thinkingMsg.edit(errorMsg).catch(() => {});
          return;
        }

        const type = chunkType ?? StreamChunkType.CONTENT;

        // Handle REASONING chunks (P2-12)
        if (type === StreamChunkType.REASONING) {
          if (this.showReasoning && chunk) {
            const msg = await this.sendToChannel(message, `-# ||${chunk}||`);
            if (msg) tracker.addReasoningMessage(msg);
          }
          if (isComplete) {
            await tracker.deleteReasoningMessages();
          }
          return;
        }

        if (type !== StreamChunkType.CONTENT || !chunk) {
          if (isComplete) {
            await tracker.deleteReasoningMessages();
          }
          return;
        }

        if (this.streamingBypassValidation) {
          // Buffer mode: collect all chunks
          if (isComplete) {
            await tracker.deleteReasoningMessages();
            // Replace thinking message with final content (split if needed)
            const pages = this.splitter.split(chunk);
            // P2-41: Fallback for empty split result with non-empty content
            if (pages.length === 0 && chunk) {
              thinkingMsg.edit(chunk).catch(() => {});
            } else if (pages.length > 0) {
              thinkingMsg.edit(pages[0]).catch(() => {});
              for (let i = 1; i < pages.length; i++) {
                await this.sendToChannel(message, pages[i]);
              }
            }
          }
        } else {
          // Real-time mode: edit thinking message with content
          if (!hasSentFirstContent) {
            thinkingMsg.edit(chunk).catch(() => {});
            hasSentFirstContent = true;
          } else {
            await this.sendToChannel(message, chunk);
          }
          if (isComplete) {
            await tracker.deleteReasoningMessages();
          }
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

  /**
   * Extracts user message content, stripping bot mention.
   */
  private extractUserMessage(message: Message): string {
    let content = message.content;
    // Remove bot mention
    content = content.replace(/<@!?(\d+)>/g, '').trim();

    // Default to "你好" if only mention
    if (!content) {
      return '你好';
    }

    return content;
  }

  /**
   * Maps DomainError to user-friendly Chinese error message.
   */
  private mapErrorToUserMessage(error: DomainError): string {
    switch (error.category) {
      case 'AI_SERVICE_AUTH_FAILED':
        return ':x: AI 服務認證失敗，請聯繫管理員檢查設定。';
      case 'AI_SERVICE_RATE_LIMITED':
        return ':hourglass: AI 服務目前忙碌中，請稍後再試。';
      case 'AI_SERVICE_TIMEOUT':
        return ':alarm_clock: AI 服務請求逾時，請稍後再試。';
      case 'AI_SERVICE_UNAVAILABLE':
        return ':warning: AI 服務目前無法使用，請稍後再試。';
      case 'AI_RESPONSE_EMPTY':
        return ':question: AI 沒有產生回應。';
      case 'AI_RESPONSE_INVALID':
        return ':warning: AI 回應格式異常，請重新嘗試。';
      default:
        return `:x: 發生錯誤：${error.message}`;
    }
  }
}
