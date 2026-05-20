import { type DiscordInteraction } from '@ltdjms/shared';
/**
 * Centralized error handler for bot interactions.
 * Converts DomainError, DiscordAPIError, and unexpected errors
 * to user-friendly zh-TW messages.
 * Matches Java BotErrorHandler.
 */
export declare class BotErrorHandler {
    /**
     * Handles an error by replying to the interaction with a user-friendly message.
     * @param error - The error to handle
     * @param interaction - The Discord interaction to reply to
     */
    handle(error: unknown, interaction: DiscordInteraction): Promise<void>;
    /**
     * Converts an error to a user-friendly zh-TW message.
     */
    toUserMessage(error: unknown): string;
    private handleDomainError;
}
