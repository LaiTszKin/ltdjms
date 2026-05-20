/**
 * Simple token estimator for chat memory management.
 * Uses character-to-token ratio approximation.
 * Matches the token estimation strategy of common LLM providers.
 */
export declare class TokenEstimator {
    private static readonly CHARS_PER_TOKEN;
    /**
     * Estimates the number of tokens in a text string.
     * Uses a simple character-to-token ratio (chars / 4).
     *
     * @param text - The text to estimate
     * @returns Estimated token count (rounded up)
     */
    estimateTokenCount(text: string): number;
    /**
     * Estimates the total tokens for an array of messages.
     * Adds a fixed per-message overhead of 4 tokens for message formatting.
     *
     * @param messages - Array of messages with content strings
     * @returns Estimated total token count
     */
    estimateMessageTokens(messages: {
        content: string;
    }[]): number;
    /**
     * Estimates remaining tokens available for a given context window.
     *
     * @param contextWindow - Total context window size (e.g., 4096, 8192, 16384)
     * @param messages - Array of messages already in context
     * @returns Remaining tokens available
     */
    estimateRemainingTokens(contextWindow: number, messages: {
        content: string;
    }[]): number;
}
