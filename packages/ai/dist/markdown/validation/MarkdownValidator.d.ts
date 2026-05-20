import { type ValidationResult } from '../types.js';
/**
 * Markdown validator interface.
 */
export interface MarkdownValidator {
    validate(markdown: string): ValidationResult;
}
