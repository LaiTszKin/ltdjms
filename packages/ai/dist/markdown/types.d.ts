/**
 * Error types for Markdown validation.
 * Matches Java ErrorType enum exactly.
 */
export declare enum ErrorType {
    HEADING_LEVEL_EXCEEDED = "HEADING_LEVEL_EXCEEDED",
    HEADING_FORMAT = "HEADING_FORMAT",
    HEADING_CONTAINS_LIST_MARKER = "HEADING_CONTAINS_LIST_MARKER",
    MALFORMED_LIST = "MALFORMED_LIST",
    MALFORMED_NESTED_LIST = "MALFORMED_NESTED_LIST",
    UNCLOSED_CODE_BLOCK = "UNCLOSED_CODE_BLOCK",
    DISCORD_RENDER_ISSUE = "DISCORD_RENDER_ISSUE",
    INLINE_HEADING = "INLINE_HEADING"
}
/**
 * A single Markdown validation error.
 * Matches Java MarkdownError record.
 */
export interface MarkdownError {
    errorType: ErrorType;
    line: number;
    column: number;
    context: string;
    suggestion: string;
}
/**
 * Validation result discriminated union.
 * Matches Java ValidationResult sealed interface.
 */
export type ValidationResult = Valid | Invalid;
export interface Valid {
    readonly _tag: 'valid';
    content: string;
}
export interface Invalid {
    readonly _tag: 'invalid';
    errors: MarkdownError[];
}
/** Creates a valid result. */
export declare function valid(content: string): ValidationResult;
/** Creates an invalid result. */
export declare function invalid(errors: MarkdownError[]): ValidationResult;
/** Type guard for Valid. */
export declare function isValid(result: ValidationResult): result is Valid;
/** Type guard for Invalid. */
export declare function isInvalid(result: ValidationResult): result is Invalid;
