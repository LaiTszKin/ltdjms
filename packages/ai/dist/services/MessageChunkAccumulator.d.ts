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
