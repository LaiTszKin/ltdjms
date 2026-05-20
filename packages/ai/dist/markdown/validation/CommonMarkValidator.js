import { ErrorType, valid, invalid, } from '../types.js';
/**
 * CommonMarkValidator implementation.
 * Detects 8 types of Markdown errors using line-level regex and state tracking.
 * Matches Java CommonMarkValidator.
 *
 * Rules:
 * - HEADING_FORMAT: missing space after #
 * - HEADING_LEVEL_EXCEEDED: more than 6 #
 * - HEADING_CONTAINS_LIST_MARKER: heading content starts with list marker
 * - MALFORMED_LIST: list marker missing space after it
 * - MALFORMED_NESTED_LIST: nested list indentation not multiple of 4
 * - UNCLOSED_CODE_BLOCK: code fence not closed
 * - DISCORD_RENDER_ISSUE: hr, __bold__, task-list, table
 * - INLINE_HEADING: ## not at start of line
 */
export class CommonMarkValidator {
    validate(markdown) {
        if (!markdown || markdown.trim().length === 0) {
            return valid(markdown);
        }
        const errors = [];
        const lines = markdown.split('\n');
        let inCodeBlock = false;
        let codeFenceChar = '';
        let foundUnclosedCodeBlock = false;
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const lineNum = i + 1;
            // Track code block state
            if (this.isCodeFence(line)) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeFenceChar = line.match(/^(`{3,}|~{3,})/)?.[1] ?? '```';
                }
                else if (line.trimStart().startsWith(codeFenceChar)) {
                    inCodeBlock = false;
                    codeFenceChar = '';
                }
                continue; // Don't validate inside code blocks
            }
            // Check for unclosed code block at end of file (before skip to ensure detection)
            if (i === lines.length - 1 && inCodeBlock) {
                foundUnclosedCodeBlock = true;
            }
            // Skip content inside code blocks
            if (inCodeBlock)
                continue;
            // Check heading rules
            this.checkHeadings(line, lineNum, errors);
            // Check inline headings
            this.checkInlineHeadings(line, lineNum, errors);
            // Check list format
            this.checkListFormat(line, lineNum, errors);
            // Check nested list indentation
            this.checkNestedListIndentation(line, lineNum, errors);
            // Check Discord unsupported syntax
            this.checkDiscordUnsupportedSyntax(line, lineNum, errors);
        }
        // Add unclosed code block error
        if (foundUnclosedCodeBlock) {
            errors.push({
                errorType: ErrorType.UNCLOSED_CODE_BLOCK,
                line: lines.length,
                column: 1,
                context: '程式碼區塊未閉合',
                suggestion: '請在程式碼區塊結尾加上 ``` 或 ~~~',
            });
        }
        if (errors.length > 0) {
            return invalid(errors);
        }
        return valid(markdown);
    }
    isCodeFence(line) {
        return /^\s*(```|~~~)/.test(line);
    }
    checkHeadings(line, lineNum, errors) {
        const headingMatch = line.match(/^(#{1,})\s*(.*)$/);
        if (!headingMatch)
            return;
        const hashes = headingMatch[1];
        const content = headingMatch[2];
        // Check heading level exceeded
        if (hashes.length > 6) {
            errors.push({
                errorType: ErrorType.HEADING_LEVEL_EXCEEDED,
                line: lineNum,
                column: 1,
                context: line.slice(0, 50),
                suggestion: `標題層級不能超過 6（目前 ${hashes.length} 層），請改為 ######`,
            });
            return; // Don't flag other heading issues on this line
        }
        // Check heading format (space after #)
        if (content.length > 0 && !line.startsWith(hashes + ' ')) {
            errors.push({
                errorType: ErrorType.HEADING_FORMAT,
                line: lineNum,
                column: hashes.length + 1,
                context: line.slice(0, 50),
                suggestion: '# 後需要加上空格',
            });
        }
        // Check heading contains list marker
        if (content && /^[-*+]\s/.test(content)) {
            errors.push({
                errorType: ErrorType.HEADING_CONTAINS_LIST_MARKER,
                line: lineNum,
                column: hashes.length + 1,
                context: line.slice(0, 50),
                suggestion: '標題中不應包含列表標記',
            });
        }
    }
    checkInlineHeadings(line, lineNum, errors) {
        // Check for ## not at start of line (inline heading)
        // Pattern: text ## heading — but not at start of line and not inside existing heading marker
        // Match when ## is preceded by any character except newline, #, or backtick
        const inlineMatch = line.match(/(?<=[^\n#`])#{2,6}\s+\S/);
        if (inlineMatch && inlineMatch.index && inlineMatch.index > 0) {
            errors.push({
                errorType: ErrorType.INLINE_HEADING,
                line: lineNum,
                column: (inlineMatch.index ?? 0) + 1,
                context: line.slice(Math.max(0, (inlineMatch.index ?? 0) - 5), (inlineMatch.index ?? 0) + 20),
                suggestion: '標題應在行首，而非行內',
            });
        }
    }
    checkListFormat(line, lineNum, errors) {
        const trimmed = line.trimStart();
        // Skip empty lines
        if (!trimmed)
            return;
        // Check unordered list: - or * or + without space after
        const unorderedMatch = trimmed.match(/^[-*+](\S)/);
        if (unorderedMatch) {
            errors.push({
                errorType: ErrorType.MALFORMED_LIST,
                line: lineNum,
                column: line.indexOf(trimmed[0]) + 2,
                context: trimmed.slice(0, 50),
                suggestion: '列表標記後需要加上空格（如 "- item"）',
            });
            return;
        }
        // Check ordered list: 1. without space after
        const orderedMatch = trimmed.match(/^\d+\.(\S)/);
        if (orderedMatch) {
            const prefixMatch = trimmed.match(/^\d+\./);
            const prefixLen = prefixMatch ? prefixMatch[0].length : 0;
            errors.push({
                errorType: ErrorType.MALFORMED_LIST,
                line: lineNum,
                column: line.indexOf(trimmed[0]) + prefixLen + 1,
                context: trimmed.slice(0, 50),
                suggestion: '編號列表後需要加上空格（如 "1. item"）',
            });
            return;
        }
        // Check multiple list markers on the same line
        if (trimmed.startsWith('-') || trimmed.startsWith('*') || trimmed.startsWith('+')) {
            const remaining = trimmed.slice(1).trim();
            if (remaining.startsWith('-') || remaining.startsWith('*') || remaining.startsWith('+')) {
                errors.push({
                    errorType: ErrorType.MALFORMED_LIST,
                    line: lineNum,
                    column: line.indexOf(trimmed[0]) + 2,
                    context: trimmed.slice(0, 50),
                    suggestion: '同一行有多個列表標記',
                });
            }
        }
    }
    checkNestedListIndentation(line, lineNum, errors) {
        const trimmed = line.trimStart();
        if (!trimmed)
            return;
        // Only check lines that look like list items
        if (!/^[-*+]\s/.test(trimmed) && !/^\d+\.\s/.test(trimmed))
            return;
        const leadingSpaces = line.length - line.trimStart().length;
        if (leadingSpaces > 0) {
            // Nested lists should use 4-space multiples
            const indentLevel = leadingSpaces / 4;
            if (!Number.isInteger(indentLevel) || leadingSpaces % 4 !== 0) {
                errors.push({
                    errorType: ErrorType.MALFORMED_NESTED_LIST,
                    line: lineNum,
                    column: 1,
                    context: trimmed.slice(0, 50),
                    suggestion: `巢狀列表縮排應為 4 的倍數（目前 ${leadingSpaces} 空格）`,
                });
            }
        }
    }
    checkDiscordUnsupportedSyntax(line, lineNum, errors) {
        const trimmed = line.trimStart();
        // Check horizontal rules (---, ***, ___)
        if (/^[-*_]{3,}\s*$/.test(trimmed) && trimmed.length >= 3) {
            errors.push({
                errorType: ErrorType.DISCORD_RENDER_ISSUE,
                line: lineNum,
                column: 1,
                context: trimmed.slice(0, 50),
                suggestion: 'Discord 不支援水平分隔線，建議改用其他方式分隔內容',
            });
        }
        // Check __text__ (Discord underline bold compatibility)
        // Discord only supports **bold** not __bold__
        const underlineBoldRegex = /(?:^|\s)__([^_\n]+)__(?:\s|$)/;
        if (underlineBoldRegex.test(trimmed)) {
            errors.push({
                errorType: ErrorType.DISCORD_RENDER_ISSUE,
                line: lineNum,
                column: trimmed.search(underlineBoldRegex) + 1,
                context: trimmed.slice(0, 50),
                suggestion: 'Discord 不支援 __text__ 底線粗體，請改用 **text**',
            });
        }
        // Check task list items
        if (/^\s*[-*+]\s\[[ x]\]/i.test(trimmed)) {
            errors.push({
                errorType: ErrorType.DISCORD_RENDER_ISSUE,
                line: lineNum,
                column: 1,
                context: trimmed.slice(0, 50),
                suggestion: 'Discord 不支援任務列表（task list），請改用普通列表',
            });
        }
    }
}
/**
 * MarkdownErrorFormatter for formatting validation errors into human-readable strings.
 */
export class MarkdownErrorFormatter {
    format(errors, originalContent) {
        if (errors.length === 0)
            return '';
        const lines = ['Markdown 格式問題：'];
        for (const error of errors) {
            lines.push(`- 第 ${error.line} 行：${error.suggestion}（${error.errorType}）`);
        }
        return lines.join('\n');
    }
}
//# sourceMappingURL=CommonMarkValidator.js.map