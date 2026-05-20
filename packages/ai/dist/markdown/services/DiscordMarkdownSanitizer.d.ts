/**
 * Sanitizes Markdown for Discord compatibility.
 * Matches Java DiscordMarkdownSanitizer.
 *
 * Operations:
 * 1. Remove HTML comments (<!-- ... -->)
 * 2. Remove HTML tags (<...>)
 * 3. Flatten nested blockquotes (>> → >)
 * 4. Convert tables to ```text code blocks
 */
export declare class DiscordMarkdownSanitizer {
    /**
     * Sanitizes Markdown content for Discord display.
     */
    sanitize(markdown: string): string;
    private protectCodeBlocks;
    private restoreCodeBlocks;
    /**
     * Converts Markdown tables to ```text code blocks.
     * Detects tables by header separator line pattern.
     */
    private convertTablesToCodeBlocks;
}
