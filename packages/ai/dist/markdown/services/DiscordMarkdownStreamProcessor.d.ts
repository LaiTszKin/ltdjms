/**
 * Processes streaming Markdown content through the sanitize → fix → validate → paginate pipeline.
 * Matches Java DiscordMarkdownStreamProcessor.
 */
export declare class DiscordMarkdownStreamProcessor {
    private buffer;
    private readonly sanitizer;
    private readonly autoFixer;
    private readonly validator;
    private readonly paginator;
    constructor();
    /**
     * Processes a chunk and returns pages if ready.
     */
    onChunk(chunk: string): string[];
    /**
     * Flushes remaining content and returns final pages.
     */
    flush(): string[];
    /**
     * Processes a complete segment through the pipeline.
     */
    private processSegment;
    /**
     * Finds a segment point at heading boundaries.
     */
    private findSegmentPoint;
}
