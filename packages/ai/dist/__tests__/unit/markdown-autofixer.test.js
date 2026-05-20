import { describe, it, expect } from 'vitest';
import { RegexBasedAutoFixer } from '../../markdown/autofix/RegexBasedAutoFixer.js';
describe('RegexBasedAutoFixer', () => {
    const fixer = new RegexBasedAutoFixer();
    describe('fixHeadingFormat', () => {
        it('should add space after #', () => {
            const result = fixer.autoFix('#Heading');
            expect(result).toBe('# Heading');
        });
        it('should add space after ##', () => {
            const result = fixer.autoFix('##Heading');
            expect(result).toBe('## Heading');
        });
    });
    describe('fixHeadingLevelExceeded', () => {
        it('should truncate ####### to ######', () => {
            const result = fixer.autoFix('####### Heading');
            expect(result).toBe('###### Heading');
        });
    });
    describe('fixListFormat', () => {
        it('should add space after -', () => {
            const result = fixer.autoFix('-item');
            expect(result).toBe('- item');
        });
        it('should add space after *', () => {
            // *item → after fixListFormat adds space: * item → after normalizeUnorderedListMarkers: - item
            const result = fixer.autoFix('*item');
            expect(result).toBe('- item');
        });
        it('should not modify valid list', () => {
            const result = fixer.autoFix('- item');
            expect(result).toBe('- item');
        });
        it('should skip horizontal rules', () => {
            const content = '---';
            const result = fixer.autoFix(content);
            // Horizontal rule should be removed by fixHorizontalRules
            expect(result).not.toContain('---');
        });
    });
    describe('fixDiscordUnderlineBold', () => {
        it('should convert __text__ to **text**', () => {
            const result = fixer.autoFix('this is __bold__ text');
            expect(result).toBe('this is **bold** text');
        });
    });
    describe('fixTaskList', () => {
        it('should convert - [x] to - ', () => {
            const result = fixer.autoFix('- [x] done');
            // after fixTaskList: "-  done" → fixListFormat normalize → "- done"
            expect(result).toBe('- done');
        });
        it('should convert - [ ] to - ', () => {
            const result = fixer.autoFix('- [ ] todo');
            expect(result).toBe('- todo');
        });
    });
    describe('fixHorizontalRules', () => {
        it('should remove ---', () => {
            const result = fixer.autoFix('text\n---\nmore');
            expect(result).toBe('text\nmore');
        });
        it('should remove ***', () => {
            const result = fixer.autoFix('text\n***\nmore');
            expect(result).toBe('text\nmore');
        });
    });
    describe('fixInlineHeadings', () => {
        it('should split inline heading', () => {
            const result = fixer.autoFix('text## heading');
            expect(result).toBe('text\n## heading');
        });
    });
    describe('fixHeadingContainsListMarker', () => {
        it('should remove list marker from heading', () => {
            const result = fixer.autoFix('### - title');
            expect(result).toBe('### title');
        });
    });
    describe('protectCodeBlocks', () => {
        it('should not modify code inside code fences', () => {
            const content = '# Title\n\n```\n### -item\n- [x] task inside code\n```';
            const result = fixer.autoFix(content);
            expect(result).toBe('# Title\n\n```\n### -item\n- [x] task inside code\n```');
        });
    });
    describe('normalizeUnorderedListMarkers', () => {
        it('should convert * to -', () => {
            const result = fixer.autoFix('* item');
            expect(result).toBe('- item');
        });
        it('should convert + to -', () => {
            const result = fixer.autoFix('+ item');
            expect(result).toBe('- item');
        });
    });
    describe('fixNestedListIndentation', () => {
        it('should fix 2-space indent to 4-space', () => {
            const result = fixer.autoFix('  - nested item');
            expect(result).toBe('    - nested item');
        });
    });
    describe('full pipeline', () => {
        it('should fix multiple issues', () => {
            const content = `#Title

## Section 1

-text without space

__bold content__

- [ ] task list

---

### - list in heading`;
            const result = fixer.autoFix(content);
            expect(result).toContain('# Title');
            expect(result).toContain('- text');
            expect(result).not.toContain('__bold');
            // **bold** may or may not survive pipeline due to bold/italic handling
            expect(result).not.toContain('---');
            expect(result).not.toContain('### -');
            expect(result).toContain('###');
        });
    });
});
//# sourceMappingURL=markdown-autofixer.test.js.map