import { StreamChunkType, Route, } from '../services/ai-chat-service.js';
import { MessageSplitter } from '../services/MessageSplitter.js';
/**
 * Tracks reasoning messages for cleanup after streaming completes.
 * Matches Java ReasoningMessageTracker.
 */
class ReasoningMessageTracker {
    initialMessage = null;
    reasoningMessages = [];
    deletionRequested = false;
    setInitialMessage(message) {
        this.initialMessage = message;
    }
    addReasoningMessage(message) {
        this.reasoningMessages.push(message);
    }
    /**
     * Deletes all tracked reasoning messages.
     */
    async deleteAll() {
        if (this.deletionRequested)
            return;
        this.deletionRequested = true;
        const toDelete = [];
        if (this.initialMessage) {
            toDelete.push(this.initialMessage);
        }
        toDelete.push(...this.reasoningMessages);
        await Promise.allSettled(toDelete.map((msg) => msg.delete().catch(() => {
            // Ignore deletion failures
        })));
    }
}
/**
 * Listens for @bot mentions and routes them to the AI chat/agent system.
 * Matches Java AIChatMentionListener.
 */
export class AIChatMentionListener {
    routingDecision;
    aiChatService;
    botUserId;
    showReasoning;
    streamingBypassValidation;
    splitter = new MessageSplitter();
    constructor(routingDecision, aiChatService, botUserId, showReasoning = false, streamingBypassValidation = false) {
        this.routingDecision = routingDecision;
        this.aiChatService = aiChatService;
        this.botUserId = botUserId;
        this.showReasoning = showReasoning;
        this.streamingBypassValidation = streamingBypassValidation;
    }
    /**
     * Handles a message creation event (discord.js messageCreate).
     * Filters non-bot mentions, self-messages, and DMs.
     */
    async onMessageCreate(message) {
        try {
            // Filter self-messages
            if (message.author.id === this.botUserId)
                return;
            // Filter DM
            if (!message.guild)
                return;
            // Check if bot is mentioned
            const botMentioned = message.mentions.has(this.botUserId);
            if (!botMentioned)
                return;
            // Get user message (default to "你好" if only mention)
            const userMessage = this.extractUserMessage(message);
            // Get routing parameters
            const guildId = message.guild.id;
            const channelId = message.channel.id;
            const restrictionChannelId = this.resolveRestrictionChannelId(message);
            // Resolve category ID
            let categoryId = null;
            if ('parentId' in message.channel && message.channel.parentId) {
                categoryId = message.channel.parentId;
            }
            // Get routing decision
            const decision = await this.routingDecision.decide(guildId, channelId, restrictionChannelId, categoryId);
            // DENY — silent return
            if (decision.route === Route.DENY) {
                return;
            }
            // Route to appropriate handler
            if (decision.route === Route.AGENT_ROUTE) {
                await this.handleAgentStreamingResponse(message, guildId, channelId, userMessage);
            }
            else {
                await this.handleChatStreamingResponse(message, guildId, channelId, userMessage);
            }
        }
        catch (error) {
            console.error(`[AIChatMentionListener] Error handling message: ${error instanceof Error ? error.message : String(error)}`);
            try {
                await message.reply('抱歉，處理你的請求時發生了錯誤。請稍後再試。');
            }
            catch {
                // Ignore reply failure
            }
        }
    }
    /**
     * Safely sends a message to the channel if it's text-based.
     * Returns null if the channel cannot be sent to.
     */
    async sendToChannel(message, content) {
        try {
            if (message.channel && 'send' in message.channel && typeof message.channel.send === 'function') {
                return await message.channel.send(content);
            }
        }
        catch {
            // Ignore send failures
        }
        return null;
    }
    /**
     * Handles Agent-mode streaming response.
     * CONTENT chunks are buffered and sent after tool execution completes.
     * Reasoning messages are deleted on completion.
     */
    async handleAgentStreamingResponse(message, guildId, channelId, userMessage) {
        const tracker = new ReasoningMessageTracker();
        const pendingContent = [];
        // Send initial "thinking" message
        const thinkingMsg = await message.reply(':thought_balloon: AI 正在思考...');
        tracker.setInitialMessage(thinkingMsg);
        const handler = {
            onChunk: (chunk, _isComplete, error) => {
                if (error) {
                    const errorMsg = this.mapErrorToUserMessage(error);
                    thinkingMsg.edit(errorMsg).catch(() => { });
                    return;
                }
                if (chunk) {
                    pendingContent.push(chunk);
                }
            },
            onChunkWithType: (chunk, isComplete, error, type) => {
                if (error) {
                    const errorMsg = this.mapErrorToUserMessage(error);
                    thinkingMsg.edit(errorMsg).catch(() => { });
                    return;
                }
                switch (type) {
                    case StreamChunkType.REASONING:
                        if (this.showReasoning && chunk) {
                            // Send reasoning as spoiler
                            this.sendToChannel(message, `-# ||${chunk}||`).then((msg) => {
                                if (msg)
                                    tracker.addReasoningMessage(msg);
                            });
                        }
                        break;
                    case StreamChunkType.TOOL_INTENT:
                        if (chunk) {
                            // Send tool intent immediately
                            this.sendToChannel(message, chunk);
                        }
                        break;
                    case StreamChunkType.CONTENT:
                        if (chunk) {
                            pendingContent.push(chunk);
                        }
                        break;
                }
                if (isComplete) {
                    // Delete reasoning messages first
                    tracker.deleteAll().then(() => {
                        // Then send final content
                        const fullContent = pendingContent.join('');
                        if (!fullContent) {
                            this.sendToChannel(message, ':question: AI 沒有產生回應');
                            return;
                        }
                        const pages = this.splitter.split(fullContent);
                        for (const page of pages) {
                            this.sendToChannel(message, page);
                        }
                    });
                }
            },
        };
        await this.aiChatService.generateStreamingResponse(guildId, channelId, message.author.id, userMessage, handler);
    }
    /**
     * Handles Chat-mode (non-Agent) streaming response.
     * CONTENT chunks are sent in real-time.
     */
    async handleChatStreamingResponse(message, guildId, channelId, userMessage) {
        // Send initial thinking message
        const thinkingMsg = await message.reply(':thought_balloon: AI 正在思考...');
        const handler = {
            onChunk: (chunk, _isComplete, error) => {
                if (error) {
                    const errorMsg = this.mapErrorToUserMessage(error);
                    thinkingMsg.edit(errorMsg).catch(() => { });
                    return;
                }
                if (chunk) {
                    thinkingMsg.edit(chunk).catch(() => { });
                }
            },
            onChunkWithType: (chunk, isComplete, error, type) => {
                if (error) {
                    const errorMsg = this.mapErrorToUserMessage(error);
                    thinkingMsg.edit(errorMsg).catch(() => { });
                    return;
                }
                if (type === StreamChunkType.CONTENT && chunk) {
                    if (this.streamingBypassValidation) {
                        // Buffer mode: collect all chunks
                        if (isComplete) {
                            // Replace thinking message with final content (split if needed)
                            const pages = this.splitter.split(chunk);
                            if (pages.length > 0) {
                                thinkingMsg.edit(pages[0]).catch(() => { });
                                for (let i = 1; i < pages.length; i++) {
                                    this.sendToChannel(message, pages[i]);
                                }
                            }
                        }
                    }
                    else {
                        // Real-time mode: edit thinking message with content
                        thinkingMsg.edit(chunk).catch(() => { });
                    }
                }
            },
        };
        await this.aiChatService.generateStreamingResponse(guildId, channelId, message.author.id, userMessage, handler);
    }
    /**
     * Extracts user message content, stripping bot mention.
     */
    extractUserMessage(message) {
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
     * Resolves restriction channel ID for threads.
     */
    resolveRestrictionChannelId(message) {
        if (message.channel.isThread()) {
            return message.channel.parentId ?? message.channel.id;
        }
        return message.channel.id;
    }
    /**
     * Maps DomainError to user-friendly Chinese error message.
     */
    mapErrorToUserMessage(error) {
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
//# sourceMappingURL=ai-chat-mention-listener.js.map