/**
 * Accumulates partial response chunks until complete.
 * Matches Java MessageChunkAccumulator.
 */
export class MessageChunkAccumulator {
    chunks = [];
    /**
     * Adds a chunk to the accumulator.
     */
    add(chunk) {
        this.chunks.push(chunk);
    }
    /**
     * Returns the current accumulated content and clears the buffer.
     */
    flush() {
        const content = this.getContent();
        this.chunks = [];
        return content;
    }
    /**
     * Returns the current accumulated content without clearing.
     */
    getContent() {
        return this.chunks.join('');
    }
    /**
     * Returns whether the accumulator has any content.
     */
    isEmpty() {
        return this.chunks.length === 0;
    }
    /**
     * Clears all accumulated chunks.
     */
    clear() {
        this.chunks = [];
    }
}
//# sourceMappingURL=MessageChunkAccumulator.js.map