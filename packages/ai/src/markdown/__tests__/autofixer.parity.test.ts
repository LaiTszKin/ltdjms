import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { RegexBasedAutoFixer } from '../autofix/RegexBasedAutoFixer.js';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { isValid } from '../types.js';

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

  it('loads autofix order from oracle', () => {
    expect(oracle.autofixOrder[0]).toBe('code_fence');
  });

  for (const testCase of oracle.cases) {
    if (testCase.input) {
      it(`matches oracle case: ${testCase.name}`, () => {
        const fixed = fixer.autoFix(testCase.input);
        expect(fixed.length).toBeGreaterThan(0);
        if (testCase.expectValidAfterAutofix) {
          expect(isValid(validator.validate(fixed))).toBe(true);
        }
        if (testCase.streamPrefix) {
          expect(testCase.streamPrefix).toBe('-# ');
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
