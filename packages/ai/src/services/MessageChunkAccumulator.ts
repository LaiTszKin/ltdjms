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
   * Returns the current accumulated content and clears the buffer.
   *
   * TODO (P3-19): This method corresponds to Java's flush() but is not yet called
   * by any consumer. It will be used when implementing chunked processing
   * (e.g., per-sentence or per-paragraph streaming).
   */
  flush(): string {
    const content = this.getContent();
    this.chunks = [];
    return content;
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
