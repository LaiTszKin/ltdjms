import { MAX_MESSAGE_LENGTH } from './ai-chat-service.js';

/**
 * Splits long messages into chunks at paragraph or sentence boundaries.
 * Matches Java MessageSplitter.
 */
export class MessageSplitter {
  private readonly maxLength: number;

  constructor(maxLength: number = MAX_MESSAGE_LENGTH) {
    this.maxLength = maxLength;
  }

  /**
   * Splits content into chunks at:
   * 1. Paragraph boundaries (\n\n)
   * 2. Sentence boundaries (。！？)
   * 3. Hard split at maxLength
   */
  split(content: string): string[] {
    if (!content || content.length === 0) {
      return [];
    }

    if (content.length <= this.maxLength) {
      return [content];
    }

    const chunks: string[] = [];
    let remaining = content;

    while (remaining.length > 0) {
      if (remaining.length <= this.maxLength) {
        chunks.push(remaining);
        break;
      }

      const splitIndex = this.findSplitIndex(remaining, this.maxLength);
      chunks.push(remaining.slice(0, splitIndex));
      remaining = remaining.slice(splitIndex).trimStart();
    }

    return chunks;
  }

  /**
   * Finds the best split index within maxLength.
   * Prefers paragraph boundaries > sentence boundaries > hard split.
   */
  private findSplitIndex(text: string, maxLen: number): number {
    const slice = text.slice(0, maxLen);

    // Try paragraph boundary first
    const paragraphBreak = slice.lastIndexOf('\n\n');
    if (paragraphBreak > 0) {
      return paragraphBreak;
    }

    // Try Chinese sentence boundaries
    for (const delimiter of ['。', '！', '？']) {
      const index = slice.lastIndexOf(delimiter);
      if (index > 0) {
        return index + delimiter.length;
      }
    }

    // Fall back to hard split
    return maxLen;
  }
}
