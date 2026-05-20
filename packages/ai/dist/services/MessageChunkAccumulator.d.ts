/**
 * Accumulates partial response chunks until complete.
 * Matches Java MessageChunkAccumulator.
 */
export declare class MessageChunkAccumulator {
    private chunks;
    /**
     * Adds a chunk to the accumulator.
     */
    add(chunk: string): void;
    /**
     * Returns the current accumulated content and clears the buffer.
     *
     * TODO (P3-19): This method corresponds to Java's flush() but is not yet called
     * by any consumer. It will be used when implementing chunked processing
     * (e.g., per-sentence or per-paragraph streaming).
     */
    flush(): string;
    /**
     * Returns the current accumulated content without clearing.
     */
    getContent(): string;
    /**
     * Returns whether the accumulator has any content.
     */
    isEmpty(): boolean;
    /**
     * Clears all accumulated chunks.
     */
    clear(): void;
}
