/**
 * Simple token estimator for chat memory management.
 * Uses character-to-token ratio approximation.
 * Matches the token estimation strategy of common LLM providers.
 */
export class TokenEstimator {
  private static readonly CHARS_PER_TOKEN = 4; // Approximate for most models

  /**
   * Estimates the number of tokens in a text string.
   * Uses a simple character-to-token ratio (chars / 4).
   *
   * @param text - The text to estimate
   * @returns Estimated token count (rounded up)
   */
  estimateTokenCount(text: string): number {
    return Math.ceil(text.length / TokenEstimator.CHARS_PER_TOKEN);
  }

  /**
   * Estimates the total tokens for an array of messages.
   * Adds a fixed per-message overhead of 4 tokens for message formatting.
   *
   * @param messages - Array of messages with content strings
   * @returns Estimated total token count
   */
  estimateMessageTokens(messages: { content: string }[]): number {
    let total = 0;
    for (const msg of messages) {
      total += this.estimateTokenCount(msg.content) + 4; // +4 for message overhead
    }
    return total;
  }

  /**
   * Estimates remaining tokens available for a given context window.
   *
   * @param contextWindow - Total context window size (e.g., 4096, 8192, 16384)
   * @param messages - Array of messages already in context
   * @returns Remaining tokens available
   */
  estimateRemainingTokens(
    contextWindow: number,
    messages: { content: string }[],
  ): number {
    const used = this.estimateMessageTokens(messages);
    return Math.max(0, contextWindow - used);
  }
}
