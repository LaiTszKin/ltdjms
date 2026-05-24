/**
 * Error types for Markdown validation.
 * Matches Java ErrorType enum exactly.
 */
export enum ErrorType {
  MALFORMED_LIST = 'MALFORMED_LIST',
  UNCLOSED_CODE_BLOCK = 'UNCLOSED_CODE_BLOCK',
  HEADING_LEVEL_EXCEEDED = 'HEADING_LEVEL_EXCEEDED',
  HEADING_FORMAT = 'HEADING_FORMAT',
  HEADING_CONTAINS_LIST_MARKER = 'HEADING_CONTAINS_LIST_MARKER',
  MALFORMED_NESTED_LIST = 'MALFORMED_NESTED_LIST',
  MALFORMED_TABLE = 'MALFORMED_TABLE',
  ESCAPE_CHARACTER_MISSING = 'ESCAPE_CHARACTER_MISSING',
  DISCORD_RENDER_ISSUE = 'DISCORD_RENDER_ISSUE',
}

/**
 * A single Markdown validation error.
 * Matches Java MarkdownError record.
 */
export interface MarkdownError {
  errorType: ErrorType;
  line: number;
  column: number;
  context: string; // max 50 chars
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
export function valid(content: string): ValidationResult {
  return { _tag: 'valid', content };
}

/** Creates an invalid result. */
export function invalid(errors: MarkdownError[]): ValidationResult {
  return { _tag: 'invalid', errors };
}

/** Type guard for Valid. */
export function isValid(result: ValidationResult): result is Valid {
  return result._tag === 'valid';
}

/** Type guard for Invalid. */
export function isInvalid(result: ValidationResult): result is Invalid {
  return result._tag === 'invalid';
}
