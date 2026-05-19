/**
 * Markdown auto-fixer interface.
 */
export interface MarkdownAutoFixer {
  autoFix(markdown: string): string;
}
