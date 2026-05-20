/**
 * Accumulates partial response chunks until complete.
 * Matches Java MessageChunkAccumulator.
 */
export class MessageChunkAccumulator {
  private chunks: string[] = [];

  /**
   * Adds a chunk to the accumulator.
   */
  add(chunk: string): void {
    this.chunks.push(chunk);
  }

  /**
   * Returns the current accumulated content without clearing.
   */
  getContent(): string {
    return this.chunks.join('');
  }

  /**
   * Returns whether the accumulator has any content.
   */
  isEmpty(): boolean {
    return this.chunks.length === 0;
  }

  /**
   * Clears all accumulated chunks.
   */
  clear(): void {
    this.chunks = [];
  }
}
