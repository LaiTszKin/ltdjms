import { MarkdownAutoFixer } from './MarkdownAutoFixer.js';
/**
 * Regex-based Markdown auto-fixer.
 * Applies 14 fix steps in a strict order.
 * Matches Java RegexBasedAutoFixer.
 */
export declare class RegexBasedAutoFixer implements MarkdownAutoFixer {
    /**
     * Applies all auto-fixes in order.
     * Max 3 retry cycles.
     */
    autoFix(markdown: string): string;
    private protectCodeBlocks;
    private restoreCodeBlocks;
    private fixUnclosedCodeBlocks;
    private fixHeadingLevelExceeded;
    private fixInlineHeadings;
    private fixHeadingFormat;
    private fixHeadingContainsListMarker;
    private fixHeadingInlineListItems;
    private fixEmbeddedLists;
    private fixInlineListMarkersInListLines;
    private fixListFormat;
    private normalizeUnorderedListMarkers;
    private fixNestedListIndentation;
    private fixDiscordUnderlineBold;
    private fixTaskList;
    private fixHorizontalRules;
}
