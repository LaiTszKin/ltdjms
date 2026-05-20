/**
 * Error types for Markdown validation.
 * Matches Java ErrorType enum exactly.
 */
export var ErrorType;
(function (ErrorType) {
    ErrorType["HEADING_LEVEL_EXCEEDED"] = "HEADING_LEVEL_EXCEEDED";
    ErrorType["HEADING_FORMAT"] = "HEADING_FORMAT";
    ErrorType["HEADING_CONTAINS_LIST_MARKER"] = "HEADING_CONTAINS_LIST_MARKER";
    ErrorType["MALFORMED_LIST"] = "MALFORMED_LIST";
    ErrorType["MALFORMED_NESTED_LIST"] = "MALFORMED_NESTED_LIST";
    ErrorType["UNCLOSED_CODE_BLOCK"] = "UNCLOSED_CODE_BLOCK";
    ErrorType["DISCORD_RENDER_ISSUE"] = "DISCORD_RENDER_ISSUE";
    ErrorType["INLINE_HEADING"] = "INLINE_HEADING";
})(ErrorType || (ErrorType = {}));
/** Creates a valid result. */
export function valid(content) {
    return { _tag: 'valid', content };
}
/** Creates an invalid result. */
export function invalid(errors) {
    return { _tag: 'invalid', errors };
}
/** Type guard for Valid. */
export function isValid(result) {
    return result._tag === 'valid';
}
/** Type guard for Invalid. */
export function isInvalid(result) {
    return result._tag === 'invalid';
}
//# sourceMappingURL=types.js.map