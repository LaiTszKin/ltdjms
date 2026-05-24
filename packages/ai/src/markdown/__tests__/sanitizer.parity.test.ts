import { describe, it, expect } from 'vitest';
import { DiscordMarkdownSanitizer } from '../services/DiscordMarkdownSanitizer.js';

/** UT-AIC-015 — DiscordMarkdownSanitizer.java parity */
describe('UT-AIC-015 sanitizer parity', () => {
  const sanitizer = new DiscordMarkdownSanitizer();

  it('shouldRemoveHtmlCommentsAndTags', () => {
    const input = 'Hello <!-- secret --> <b>bold</b> world';
    expect(sanitizer.sanitize(input)).toBe('Hello  bold world');
  });

  it('shouldFlattenNestedBlockquotes', () => {
    const input = '>> nested quote';
    expect(sanitizer.sanitize(input)).toBe('> nested quote');
  });

  it('shouldConvertTablesToCodeBlocks', () => {
    const input = '| Name | Value |\n| --- | --- |\n| foo | bar |';
    const output = sanitizer.sanitize(input);
    expect(output).toContain('```text');
    expect(output).toContain('| Name | Value |');
    expect(output).toContain('| foo | bar |');
  });

  it('shouldPreserveCodeBlocksWhileSanitizing', () => {
    const input = '```html\n<b>keep</b>\n```\n<p>remove</p>';
    expect(sanitizer.sanitize(input)).toContain('```html\n<b>keep</b>\n```');
    expect(sanitizer.sanitize(input)).not.toContain('<p>');
  });

  it('shouldReturnEmptyInputUnchanged', () => {
    expect(sanitizer.sanitize('')).toBe('');
  });
});
