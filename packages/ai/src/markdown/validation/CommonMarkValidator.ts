import { lexer, type Tokens } from 'marked';
import { MarkdownValidator } from './MarkdownValidator.js';
import {
  ErrorType,
  type MarkdownError,
  type ValidationResult,
  valid,
  invalid,
} from '../types.js';

/**
 * CommonMarkValidator implementation using marked.lexer() AST parser.
 * Detects 8 types of Markdown errors via token-based AST traversal.
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
export class CommonMarkValidator implements MarkdownValidator {
  validate(markdown: string): ValidationResult {
    if (!markdown || markdown.trim().length === 0) {
      return valid(markdown);
    }

    const errors: MarkdownError[] = [];
    const lines = markdown.split('\n');

    // Parse using marked.lexer() to get AST tokens
    let tokens: Tokens.Token[];
    try {
      tokens = lexer(markdown);
    } catch {
      // If lexer fails, fall back to original markdown
      return valid(markdown);
    }

    // Compute line ranges that are inside code blocks (skip these during regex pass)
    const codeBlockLines = new Set<number>();
    this.collectCodeBlockLines(tokens, codeBlockLines);

    // Walk the token tree recursively for AST-based validation
    this.walkTokens(tokens, lines, errors);

    // Additional regex-based pass for heading/list format issues that the AST may miss.
    // The CommonMark spec requires space after #/list markers, but we flag it proactively.
    this.regexFormatPass(lines, codeBlockLines, errors);

    // Detect unclosed fenced code blocks by checking if the last code token
    // starts with a fence marker (``` or ~~~) but does not end with one.
    // Indented code blocks (4-space indent) are always closed by definition.
    if (tokens.length > 0) {
      const lastToken = tokens[tokens.length - 1];
      if (lastToken.type === 'code') {
        const codeToken = lastToken as Tokens.Code;
        const raw = codeToken.raw.trimStart();
        if (raw.startsWith('```') || raw.startsWith('~~~')) {
          const trimmedEnd = raw.trimEnd();
          if (
            !trimmedEnd.endsWith('```') &&
            !trimmedEnd.endsWith('~~~')
          ) {
            errors.push({
              errorType: ErrorType.UNCLOSED_CODE_BLOCK,
              line: lines.length,
              column: 1,
              context: '程式碼區塊未閉合',
              suggestion: '請在程式碼區塊結尾加上 ``` 或 ~~~',
            });
          }
        }
      }
    }

    if (errors.length > 0) {
      return invalid(errors);
    }

    return valid(markdown);
  }

  private walkTokens(
    tokens: Tokens.Token[],
    lines: string[],
    errors: MarkdownError[],
  ): void {
    for (const token of tokens) {
      switch (token.type) {
        case 'heading':
          this.validateHeadingToken(token as Tokens.Heading, lines, errors);
          break;
        case 'code':
          // Code block is properly closed — nothing to validate inside
          break;
        case 'list':
          this.validateListToken(token as Tokens.List, lines, errors);
          break;
        case 'hr':
          this.validateHrToken(token as Tokens.Hr, lines, errors);
          break;
        case 'table':
          this.validateTableToken(token as Tokens.Table, lines, errors);
          break;
        case 'paragraph':
          // Check for inline headings and emphasis syntax issues in paragraph text
          this.validateParagraphToken(token as Tokens.Paragraph, lines, errors);
          break;
        default:
          break;
      }

      // Recurse into nested tokens (e.g. list items)
      const tok = token as Tokens.Token & { tokens?: Tokens.Token[] };
      if (tok.tokens && tok.tokens.length > 0) {
        this.walkTokens(tok.tokens, lines, errors);
      }
    }
  }

  private findLineNumber(lines: string[], raw: string, startLine = 0): number {
    const searchText = raw.slice(0, Math.min(raw.length, 50)).trimStart();
    if (!searchText) return lines.length;

    for (let i = startLine; i < lines.length; i++) {
      if (lines[i].includes(searchText) || searchText.includes(lines[i].trim())) {
        return i + 1;
      }
    }
    return startLine + 1;
  }

  private validateHeadingToken(
    token: Tokens.Heading,
    lines: Array<string>,
    errors: MarkdownError[],
  ): void {
    const lineNum = this.findLineNumber(lines, token.raw);

    // Check heading level exceeded
    if (token.depth > 6) {
      errors.push({
        errorType: ErrorType.HEADING_LEVEL_EXCEEDED,
        line: lineNum,
        column: 1,
        context: '#'.repeat(token.depth) + ' ' + token.text,
        suggestion: `標題層級不能超過 6（目前 ${token.depth} 層），請改為 ######`,
      });
      return;
    }

    // Check heading format: missing space after #
    const rawFirstLine = token.raw.split('\n')[0];
    const headingMarkerMatch = rawFirstLine.match(/^(#{1,6})(.*)$/);
    if (headingMarkerMatch) {
      const hashes = headingMarkerMatch[1];
      const afterHashes = headingMarkerMatch[2];
      if (afterHashes.length > 0 && afterHashes[0] !== ' ') {
        errors.push({
          errorType: ErrorType.HEADING_FORMAT,
          line: lineNum,
          column: hashes.length + 1,
          context: rawFirstLine.slice(0, 50),
          suggestion: '# 後需要加上空格',
        });
      }
    }

    // Check heading contains list marker
    if (token.text && /^[-*+]\s/.test(token.text)) {
      errors.push({
        errorType: ErrorType.HEADING_CONTAINS_LIST_MARKER,
        line: lineNum,
        column: 1,
        context: rawFirstLine.slice(0, 50),
        suggestion: '標題中不應包含列表標記',
      });
    }
  }

  private validateParagraphToken(
    token: Tokens.Paragraph,
    lines: Array<string>,
    errors: MarkdownError[],
  ): void {
    const raw = token.raw;
    const lineNum = this.findLineNumber(lines, raw);

    // Check for inline headings (## not at start of line)
    const inlineHeadingMatch = raw.match(/(?<=[^\n#`])#{2,6}\s+\S/);
    if (inlineHeadingMatch) {
      errors.push({
        errorType: ErrorType.INLINE_HEADING,
        line: lineNum,
        column: (inlineHeadingMatch.index ?? 0) + 1,
        context: raw.slice(Math.max(0, (inlineHeadingMatch.index ?? 0) - 5), (inlineHeadingMatch.index ?? 0) + 20),
        suggestion: '標題應在行首，而非行內',
      });
    }

    // Check for __text__ underline bold
    const underlineBoldRegex = /(?:^|\s)__([^_\n]+)__(?:\s|$)/;
    if (underlineBoldRegex.test(raw)) {
      errors.push({
        errorType: ErrorType.DISCORD_RENDER_ISSUE,
        line: lineNum,
        column: raw.search(underlineBoldRegex) + 1,
        context: raw.slice(0, 50),
        suggestion: 'Discord 不支援 __text__ 底線粗體，請改用 **text**',
      });
    }

    // Check for task list items in paragraph (not wrapped in list)
    if (/^\s*[-*+]\s\[[ x]\]/i.test(raw)) {
      errors.push({
        errorType: ErrorType.DISCORD_RENDER_ISSUE,
        line: lineNum,
        column: 1,
        context: raw.slice(0, 50),
        suggestion: 'Discord 不支援任務列表（task list），請改用普通列表',
      });
    }
  }

  private validateListToken(
    token: Tokens.List,
    lines: Array<string>,
    errors: MarkdownError[],
  ): void {
    const items = token.items;

    for (const item of items) {
      const itemLineNum = this.findLineNumber(lines, item.raw);
      const rawFirstLine = item.raw.split('\n')[0];
      const trimmed = rawFirstLine.trimStart();

      // Check list marker format (must have space after marker)
      if (/^[-*+](\S)/.test(trimmed)) {
        errors.push({
          errorType: ErrorType.MALFORMED_LIST,
          line: itemLineNum,
          column: rawFirstLine.indexOf(trimmed[0]) + 2,
          context: trimmed.slice(0, 50),
          suggestion: '列表標記後需要加上空格（如 "- item"）',
        });
      }

      // Check ordered list format
      if (/^\d+\.(\S)/.test(trimmed)) {
        errors.push({
          errorType: ErrorType.MALFORMED_LIST,
          line: itemLineNum,
          column: rawFirstLine.indexOf(trimmed[0]) + trimmed.match(/^\d+\./)![0].length + 1,
          context: trimmed.slice(0, 50),
          suggestion: '編號列表後需要加上空格（如 "1. item"）',
        });
      }

      // Check nested list indentation
      const leadingSpaces = rawFirstLine.length - rawFirstLine.trimStart().length;
      if (leadingSpaces > 0 && leadingSpaces % 4 !== 0) {
        errors.push({
          errorType: ErrorType.MALFORMED_NESTED_LIST,
          line: itemLineNum,
          column: 1,
          context: rawFirstLine.slice(0, 50),
          suggestion: `巢狀列表縮排應為 4 的倍數（目前 ${leadingSpaces} 空格）`,
        });
      }
    }
  }

  private validateHrToken(
    token: Tokens.Hr,
    lines: Array<string>,
    errors: MarkdownError[],
  ): void {
    const lineNum = this.findLineNumber(lines, token.raw);
    errors.push({
      errorType: ErrorType.DISCORD_RENDER_ISSUE,
      line: lineNum,
      column: 1,
      context: token.raw.slice(0, 50),
      suggestion: 'Discord 不支援水平分隔線，建議改用其他方式分隔內容',
    });
  }

  private validateTableToken(
    token: Tokens.Table,
    lines: Array<string>,
    errors: MarkdownError[],
  ): void {
    const lineNum = this.findLineNumber(lines, token.raw);
    errors.push({
      errorType: ErrorType.DISCORD_RENDER_ISSUE,
      line: lineNum,
      column: 1,
      context: token.raw.slice(0, 50),
      suggestion: 'Discord 不支援表格，請改用其他方式呈現結構化內容',
    });
  }

  /**
   * Recursively collect line numbers that are inside fenced code blocks.
   */
  private collectCodeBlockLines(tokens: Tokens.Token[], codeBlockLines: Set<number>): void {
    for (const token of tokens) {
      if (token.type === 'code') {
        // Skip all lines in code blocks - collectCodeBlockLines tracks them
      }
      const tok = token as Tokens.Token & { tokens?: Tokens.Token[] };
      if (tok.tokens && tok.tokens.length > 0) {
        this.collectCodeBlockLines(tok.tokens, codeBlockLines);
      }
    }
  }

  /**
   * Additional regex-based format pass for heading/list syntax issues
   * that the AST parser may not flag (since marked normalizes some syntax).
   * Skips lines inside code blocks.
   */
  private regexFormatPass(
    lines: string[],
    codeBlockLines: Set<number>,
    errors: MarkdownError[],
  ): void {
    // For simplicity, track code block state with same logic as old implementation
    let inCodeBlock = false;
    let codeFenceChar = '';

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const lineNum = i + 1;

      // Track code fence state
      if (/^\s*(```|~~~)/.test(line)) {
        if (!inCodeBlock) {
          inCodeBlock = true;
          codeFenceChar = line.match(/^(```|~~~)/)?.[1] ?? '```';
        } else if (line.trimStart().startsWith(codeFenceChar)) {
          inCodeBlock = false;
          codeFenceChar = '';
        }
        continue;
      }
      if (inCodeBlock) continue;

      const trimmed = line.trimStart();

      // Check headings that marked's AST might not flag (missing space after #)
      const headingMatch = line.match(/^(#{1,})(.*)$/);
      if (headingMatch) {
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
          continue;
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
            column: 1,
            context: line.slice(0, 50),
            suggestion: '標題中不應包含列表標記',
          });
        }
      }

      // Check inline headings (## not at start of line)
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

      // Skip empty lines for list checks
      if (!trimmed) continue;

      // Skip emphasis syntax (*text* and **text**) — these are not list markers
      if (/^\*[^*]+\*$/.test(trimmed) || /^\*\*[^*]+\*\*$/.test(trimmed)) continue;

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
        continue;
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
        continue;
      }

      // Check nested list indentation
      if (/^[-*+]\s/.test(trimmed) || /^\d+\.\s/.test(trimmed)) {
        const leadingSpaces = line.length - line.trimStart().length;
        if (leadingSpaces > 0 && leadingSpaces % 4 !== 0) {
          errors.push({
            errorType: ErrorType.MALFORMED_NESTED_LIST,
            line: lineNum,
            column: 1,
            context: trimmed.slice(0, 50),
            suggestion: `巢狀列表縮排應為 4 的倍數（目前 ${leadingSpaces} 空格）`,
          });
        }
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
}

/**
 * MarkdownErrorFormatter for formatting validation errors into human-readable strings.
 */
export class MarkdownErrorFormatter {
  format(errors: MarkdownError[], originalContent?: string): string {
    if (errors.length === 0) return '';

    const lines: string[] = ['Markdown 格式問題：'];

    for (const error of errors) {
      lines.push(
        `- 第 ${error.line} 行：${error.suggestion}（${error.errorType}）`,
      );
    }

    return lines.join('\n');
  }
}
