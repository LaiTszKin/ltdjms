import { type Message } from 'discord.js';
import { type AIChatService } from '../services/ai-chat-service.js';
import { type AIChatMentionRoutingDecision } from '../services/routing/routing-decision.js';
/**
 * Listens for @bot mentions and routes them to the AI chat/agent system.
 * Matches Java AIChatMentionListener.
 */
export declare class AIChatMentionListener {
    private readonly routingDecision;
    private readonly aiChatService;
    private readonly botUserId;
    private readonly showReasoning;
    private readonly streamingBypassValidation;
    private splitter;
    constructor(routingDecision: AIChatMentionRoutingDecision, aiChatService: AIChatService, botUserId: string, showReasoning?: boolean, streamingBypassValidation?: boolean);
    /**
     * Handles a message creation event (discord.js messageCreate).
     * Filters non-bot mentions, self-messages, and DMs.
     */
    onMessageCreate(message: Message): Promise<void>;
    /**
     * Safely sends a message to the channel if it's text-based.
     * Returns null if the channel cannot be sent to.
     */
    private sendToChannel;
    /**
     * Handles Agent-mode streaming response.
     * CONTENT chunks are buffered and sent after tool execution completes.
     * Reasoning messages are deleted on completion.
     */
    private handleAgentStreamingResponse;
    /**
     * Handles Chat-mode (non-Agent) streaming response.
     * CONTENT chunks are sent in real-time.
     */
    private handleChatStreamingResponse;
    /**
     * Extracts user message content, stripping bot mention.
     */
    private extractUserMessage;
    /**
     * Resolves restriction channel ID for threads.
     */
    private resolveRestrictionChannelId;
    /**
     * Maps DomainError to user-friendly Chinese error message.
     */
    private mapErrorToUserMessage;
}
