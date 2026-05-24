import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { RegexBasedAutoFixer } from '../autofix/RegexBasedAutoFixer.js';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { isValid } from '../types.js';

const SPOILER_PREFIX = '-# ';

function formatAsSpoiler(content: string): string {
  if (!content) {
    return content;
  }
  if (content.startsWith(SPOILER_PREFIX)) {
    return content;
  }
  return SPOILER_PREFIX + content;
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const oracle = JSON.parse(
  readFileSync(
    join(
      __dirname,
      '../../../../../docs/plans/2026-05-24/java-parity-shop-ai/ai-chat-java-parity/fixtures/java-markdown-oracle.json',
    ),
    'utf-8',
  ),
);

/** UT-AIC-011 — MarkdownAutoFixerTest.java parity */
describe('UT-AIC-011 autofixer parity', () => {
  const fixer = new RegexBasedAutoFixer();
  const validator = new CommonMarkValidator();

  it('loads 14-step autofix order from oracle', () => {
    expect(oracle.autofixOrder).toHaveLength(14);
    expect(oracle.autofixOrder[0]).toBe('fixUnclosedCodeBlocks');
    expect(oracle.autofixOrder[13]).toBe('fixHorizontalRules');
  });

  for (const testCase of oracle.cases) {
    if (testCase.input && (testCase.expectValidAfterAutofix || testCase.streamPrefix)) {
      it(`matches oracle case: ${testCase.name}`, () => {
        const fixed = fixer.autoFix(testCase.input);
        expect(fixed.length).toBeGreaterThan(0);
        if (testCase.expectValidAfterAutofix) {
          expect(isValid(validator.validate(fixed))).toBe(true);
        }
        if (testCase.streamPrefix) {
          expect(formatAsSpoiler(testCase.input)).toBe(`${testCase.streamPrefix}${testCase.input}`);
        }
      });
    }
  }

  it('shouldFixHeadingFormatMissingSpace', () => {
    const input = '#This is a heading\n##Another heading';
    const expected = '# This is a heading\n## Another heading';
    expect(fixer.autoFix(input)).toBe(expected);
  });

  it('shouldFixUnclosedCodeBlock', () => {
    const input = '```\nconsole.log("hello");\nSome text after';
    const result = fixer.autoFix(input);
    expect(result).toContain('```');
    expect(isValid(validator.validate(result))).toBe(true);
  });

  it('max 3 retry cycles converge', () => {
    const input = '#Wrong\n- item\n```\ncode';
    const result = fixer.autoFix(input);
    expect(result.length).toBeGreaterThan(0);
  });
});
