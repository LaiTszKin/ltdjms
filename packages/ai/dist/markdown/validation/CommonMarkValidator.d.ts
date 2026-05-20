import { MarkdownValidator } from './MarkdownValidator.js';
import { type MarkdownError, type ValidationResult } from '../types.js';
/**
 * CommonMarkValidator implementation.
 * Detects 8 types of Markdown errors using line-level regex and state tracking.
 * Matches Java CommonMarkValidator.
 *
 * Rules:
 * - HEADING_FORMAT: missing space after #
 * - HEADING_LEVEL_EXCEEDED: more than 6 #
 * - HEADING_CONTAINS_LIST_MARKER: heading content starts with list marker
 * - MALFORMED_LIST: list marker missing space after it
 * - MALFORMED_NESTED_LIST: nested list indentation not multiple of 4
 * - UNCLOSED_CODE_BLOCK: code fence not closed
 * - DISCORD_RENDER_ISSUE: hr, __bold__, task-list, table
 * - INLINE_HEADING: ## not at start of line
 */
export declare class CommonMarkValidator implements MarkdownValidator {
    validate(markdown: string): ValidationResult;
    private isCodeFence;
    private checkHeadings;
    private checkInlineHeadings;
    private checkListFormat;
    private checkNestedListIndentation;
    private checkDiscordUnsupportedSyntax;
}
/**
 * MarkdownErrorFormatter for formatting validation errors into human-readable strings.
 */
export declare class MarkdownErrorFormatter {
    format(errors: MarkdownError[], originalContent?: string): string;
}
