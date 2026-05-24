import { MAX_MESSAGE_LENGTH } from './ai-chat-service.js';

/**
 * Accumulates streaming output and splits at paragraph or length boundaries.
 * Matches Java MessageChunkAccumulator.
 */
export class MessageChunkAccumulator {
  private buffer = '';

  /**
   * Accumulates delta content and returns segments ready to send (at most one).
   */
  accumulate(delta: string | null | undefined): string[] {
    if (delta) {
      this.buffer += delta;
    }

    const readyToSend: string[] = [];

    while (true) {
      const paragraphEnd = this.findFirstParagraphBoundary();
      if (paragraphEnd <= 0 || paragraphEnd > MAX_MESSAGE_LENGTH) {
        break;
      }
      const chunk = this.buffer.slice(0, paragraphEnd);
      if (chunk.trim().length === 0) {
        this.buffer = this.buffer.slice(paragraphEnd);
        continue;
      }
      readyToSend.push(chunk);
      this.buffer = this.buffer.slice(paragraphEnd);
      return readyToSend;
    }

    if (this.buffer.length >= MAX_MESSAGE_LENGTH) {
      readyToSend.push(this.buffer.slice(0, MAX_MESSAGE_LENGTH));
      this.buffer = this.buffer.slice(MAX_MESSAGE_LENGTH);
    }

    return readyToSend;
  }

  /** Returns remaining content at stream end (trimmed). */
  drain(): string {
    const remaining = this.buffer.trim();
    this.buffer = '';
    return remaining;
  }

  private findFirstParagraphBoundary(): number {
    const idx = this.buffer.indexOf('\n\n');
    return idx >= 0 ? idx + 2 : -1;
  }
}
