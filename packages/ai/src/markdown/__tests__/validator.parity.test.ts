import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { ErrorType, isInvalid, isValid } from '../types.js';

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

/** UT-AIC-010 — CommonMarkValidatorTest_* parity */
describe('UT-AIC-010 validator parity', () => {
  const validator = new CommonMarkValidator();

  it('loads java-markdown-oracle.json error types', () => {
    expect(oracle.errorTypes).toContain('UNBALANCED_EMPHASIS');
  });

  it('flags heading without space', () => {
    const result = validator.validate('#Heading');
    expect(isInvalid(result)).toBe(true);
    if (isInvalid(result)) {
      expect(result.errors[0].errorType).toBe(ErrorType.HEADING_FORMAT);
    }
  });

  it('passes valid heading', () => {
    expect(isValid(validator.validate('# Heading'))).toBe(true);
  });

  it('flags unclosed code block', () => {
    const result = validator.validate('```\ncode');
    expect(isInvalid(result)).toBe(true);
    if (isInvalid(result)) {
      expect(result.errors.some((e) => e.errorType === ErrorType.UNCLOSED_CODE_BLOCK)).toBe(true);
    }
  });

  it('flags malformed list', () => {
    const result = validator.validate('-item');
    expect(isInvalid(result)).toBe(true);
    if (isInvalid(result)) {
      expect(result.errors[0].errorType).toBe(ErrorType.MALFORMED_LIST);
    }
  });
});
