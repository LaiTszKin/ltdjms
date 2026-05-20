import { describe, it, expect } from 'vitest';
import { CommonMarkValidator } from '../../markdown/validation/CommonMarkValidator.js';
import { ErrorType, isValid, isInvalid } from '../../markdown/types.js';
describe('CommonMarkValidator', () => {
    const validator = new CommonMarkValidator();
    describe('HEADING_FORMAT (missing space after #)', () => {
        it('should flag heading without space after #', () => {
            const result = validator.validate('#Heading');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors[0].errorType).toBe(ErrorType.HEADING_FORMAT);
            }
        });
        it('should pass valid heading with space', () => {
            const result = validator.validate('# Heading');
            expect(isValid(result)).toBe(true);
        });
        it('should flag multiple levels without space', () => {
            const result = validator.validate('##Heading');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors[0].errorType).toBe(ErrorType.HEADING_FORMAT);
            }
        });
    });
    describe('HEADING_LEVEL_EXCEEDED (>6 #)', () => {
        it('should flag 7 # as exceeded', () => {
            const result = validator.validate('####### Heading');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors[0].errorType).toBe(ErrorType.HEADING_LEVEL_EXCEEDED);
            }
        });
        it('should pass 6 # as valid', () => {
            const result = validator.validate('###### Heading');
            expect(isValid(result)).toBe(true);
        });
    });
    describe('HEADING_CONTAINS_LIST_MARKER', () => {
        it('should flag heading with list marker', () => {
            const result = validator.validate('### - title');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors.some(e => e.errorType === ErrorType.HEADING_CONTAINS_LIST_MARKER)).toBe(true);
            }
        });
        it('should pass heading without list marker', () => {
            const result = validator.validate('### title');
            expect(isValid(result)).toBe(true);
        });
    });
    describe('MALFORMED_LIST (missing space after -)', () => {
        it('should flag -item without space', () => {
            const result = validator.validate('-item');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors[0].errorType).toBe(ErrorType.MALFORMED_LIST);
            }
        });
        it('should pass - item with space', () => {
            const result = validator.validate('- item');
            expect(isValid(result)).toBe(true);
        });
        it('should flag 1.item without space', () => {
            const result = validator.validate('1.item');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors[0].errorType).toBe(ErrorType.MALFORMED_LIST);
            }
        });
    });
    describe('MALFORMED_NESTED_LIST (indentation not 4n)', () => {
        it('should flag 2-space indentation for nested list', () => {
            const result = validator.validate('  - item');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors.some(e => e.errorType === ErrorType.MALFORMED_NESTED_LIST)).toBe(true);
            }
        });
        it('should pass 4-space indentation for nested list', () => {
            const result = validator.validate('    - item');
            expect(isValid(result)).toBe(true);
        });
    });
    describe('UNCLOSED_CODE_BLOCK', () => {
        it('should flag unclosed code fence', () => {
            const result = validator.validate('```\ncode\n');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors.some(e => e.errorType === ErrorType.UNCLOSED_CODE_BLOCK)).toBe(true);
            }
        });
        it('should pass closed code fence', () => {
            const result = validator.validate('```\ncode\n```');
            expect(isValid(result)).toBe(true);
        });
    });
    describe('DISCORD_RENDER_ISSUE', () => {
        it('should flag horizontal rule ---', () => {
            const result = validator.validate('---');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors.some(e => e.errorType === ErrorType.DISCORD_RENDER_ISSUE)).toBe(true);
            }
        });
        it('should flag __bold__ syntax', () => {
            const result = validator.validate('this is __bold__ text');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors.some(e => e.errorType === ErrorType.DISCORD_RENDER_ISSUE)).toBe(true);
            }
        });
        it('should flag task list - [x]', () => {
            const result = validator.validate('- [x] done');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors.some(e => e.errorType === ErrorType.DISCORD_RENDER_ISSUE)).toBe(true);
            }
        });
    });
    describe('INLINE_HEADING', () => {
        it('should flag inline heading marker', () => {
            const result = validator.validate('some text ## heading');
            expect(isInvalid(result)).toBe(true);
            if (isInvalid(result)) {
                expect(result.errors.some(e => e.errorType === ErrorType.INLINE_HEADING)).toBe(true);
            }
        });
    });
    describe('code block protection', () => {
        it('should not flag content inside code blocks', () => {
            const result = validator.validate('```\n# this looks like a heading but is inside code\n```');
            expect(isValid(result)).toBe(true);
        });
    });
    describe('empty/whitespace validation', () => {
        it('should pass empty string', () => {
            const result = validator.validate('');
            expect(isValid(result)).toBe(true);
        });
        it('should pass whitespace-only string', () => {
            const result = validator.validate('   \n  \n');
            expect(isValid(result)).toBe(true);
        });
    });
    describe('mixed valid content', () => {
        it('should pass valid markdown content', () => {
            const content = `# Title

This is a paragraph with **bold** and *italic*.

## Section

- Item 1
- Item 2

\`\`\`
code block
\`\`\``;
            const result = validator.validate(content);
            expect(isValid(result)).toBe(true);
        });
    });
});
//# sourceMappingURL=markdown-validator.test.js.map