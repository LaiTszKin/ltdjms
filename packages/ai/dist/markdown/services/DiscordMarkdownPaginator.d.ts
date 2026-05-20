/**
 * Splits long Markdown content into Discord-compatible pages.
 * Matches Java DiscordMarkdownPaginator.
 *
 * Splitting priority:
 * 1. At heading boundaries (^#{1,6}\s+.+)
 * 2. At code fence boundaries
 * 3. Hard split at 1900 chars
 */
export declare class DiscordMarkdownPaginator {
    private readonly maxLength;
    constructor(maxLength?: number);
    /**
     * Paginates Markdown content into chunks.
     * Returns empty array for null/empty content.
     */
    paginate(content: string | null | undefined): string[];
    /**
     * Finds the last heading boundary before maxLength.
     */
    private findLastHeadingBefore;
    /**
     * Finds a code fence boundary before maxLength.
     * Fences at position 0 are ignored (can't split at start).
     */
    private findCodeFenceBoundary;
    /**
     * Handles code fence boundaries when splitting pages.
     * Ensures code fences are properly closed/opened across page boundaries.
     *
     * Uses a state machine: scan the page line-by-line, tracking whether we are
     * inside a code fence, so we know whether a fence started on a previous page
     * is closed in this one, or a new fence was opened without a close.
     */
    private handleCodeFenceBoundary;
}
