import { lexer, type Token, type Tokens } from 'marked';
import { MarkdownValidator } from './MarkdownValidator.js';
import { ErrorType, type MarkdownError, type ValidationResult, valid, invalid } from '../types.js';

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
  /**
   * Builds an array of line start offsets for the given text.
   */
  private buildLineStarts(text: string): number[] {
    const lineStarts: number[] = [0];
    for (let i = 0; i < text.length; i++) {
      if (text[i] === '\n') {
        lineStarts.push(i + 1);
      }
    }
    return lineStarts;
  }

  /**
   * Finds the 1-based line number for a given character offset
   * using binary search on the lineStarts array.
   */
  private getLineNumber(offset: number, lineStarts: number[]): number {
    let lo = 0;
    let hi = lineStarts.length - 1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (lineStarts[mid] <= offset) {
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    // hi is the index of the last line start <= offset; line number is hi + 1
    return hi + 1;
  }

  validate(markdown: string): ValidationResult {
    if (!markdown || markdown.trim().length === 0) {
      return valid(markdown);
    }

    const errors: MarkdownError[] = [];
    const lines = markdown.split('\n');
    const lineStarts = this.buildLineStarts(markdown);

    // Parse using marked.lexer() to get AST tokens
    let tokens: Token[];
    try {
      tokens = lexer(markdown);
    } catch {
      // If lexer fails, fall back to original markdown
      return valid(markdown);
    }

    // Walk the token tree recursively for AST-based validation
    this.walkTokens(tokens, lines, markdown, errors, lineStarts);

    // Additional regex-based pass for heading/list format issues that the AST may miss.
    // The CommonMark spec requires space after #/list markers, but we flag it proactively.
    this.regexFormatPass(lines, errors);

    // Deduplicate errors by (line, errorType) to avoid AST + regex double-reporting
    const errorMap = new Map<string, MarkdownError>();
    for (const error of errors) {
      const key = `${error.line}:${error.errorType}`;
      if (!errorMap.has(key)) {
        errorMap.set(key, error);
      }
    }
    errors.length = 0;
    errors.push(...errorMap.values());

    // Detect unclosed fenced code blocks by checking ALL code tokens, not just the last one.
    // An unclosed fence anywhere in the token stream will appear as a 'code' token
    // whose raw starts with a fence marker but whose trimmed end does not contain the closing fence.
    // Iterating through all tokens ensures mid-content unclosed blocks are also flagged.
    for (const token of tokens) {
      if (token.type === 'code') {
        const codeToken = token as Tokens.Code;
        const raw = codeToken.raw.trimStart();
        if (raw.startsWith('```') || raw.startsWith('~~~')) {
          const trimmedEnd = raw.trimEnd();
          if (!trimmedEnd.endsWith('```') && !trimmedEnd.endsWith('~~~')) {
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
    tokens: Token[],
    lines: string[],
    markdown: string,
    errors: MarkdownError[],
    lineStarts: number[],
    parentRaw?: string,
    parentPos?: number,
  ): void {
    let cursor = parentPos ?? 0;
    for (const token of tokens) {
      // Compute character position of this token in the original markdown
      let pos: number;
      if (parentRaw !== undefined && parentPos !== undefined) {
        const relativePos = parentRaw.indexOf(token.raw);
        pos = relativePos >= 0 ? parentPos + relativePos : -1;
      } else {
        pos = markdown.indexOf(token.raw, cursor);
      }

      if (pos >= 0 && parentRaw === undefined) {
        // Advance cursor past this token for next sibling search
        cursor = pos + token.raw.length;
      }

      // Compute 1-based line number from character position using binary search
      const lineNum = pos >= 0 ? this.getLineNumber(pos, lineStarts) : 1;

      switch (token.type) {
        case 'heading':
          this.validateHeadingToken(token as Tokens.Heading, errors, lineNum);
          break;
        case 'code':
          // Code block is properly closed — nothing to validate inside
          break;
        case 'list':
          this.validateListToken(
            token as Tokens.List,
            errors,
            markdown,
            pos >= 0 ? pos : 0,
            lineStarts,
          );
          break;
        case 'hr':
          this.validateHrToken(token as Tokens.Hr, errors, lineNum);
          break;
        case 'table':
          this.validateTableToken(token as Tokens.Table, errors, lineNum);
          break;
        case 'paragraph':
          // Check for inline headings and emphasis syntax issues in paragraph text
          this.validateParagraphToken(token as Tokens.Paragraph, errors, lineNum);
          break;
        default:
          break;
      }

      // Recurse into nested tokens (e.g. list items)
      const tok = token as Token & { tokens?: Token[] };
      if (tok.tokens && tok.tokens.length > 0) {
        this.walkTokens(
          tok.tokens,
          lines,
          markdown,
          errors,
          lineStarts,
          token.raw,
          pos >= 0 ? pos : 0,
        );
      }
    }
  }

  private validateHeadingToken(
    token: Tokens.Heading,
    errors: MarkdownError[],
    lineNum: number,
  ): void {
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
    errors: MarkdownError[],
    lineNum: number,
  ): void {
    const raw = token.raw;

    // Check for inline headings (## not at start of line)
    const inlineHeadingMatch = raw.match(/(?<=[^\n#`])#{2,6}\s+\S/);
    if (inlineHeadingMatch) {
      errors.push({
        errorType: ErrorType.HEADING_FORMAT,
        line: lineNum,
        column: (inlineHeadingMatch.index ?? 0) + 1,
        context: raw.slice(
          Math.max(0, (inlineHeadingMatch.index ?? 0) - 5),
          (inlineHeadingMatch.index ?? 0) + 20,
        ),
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
    errors: MarkdownError[],
    markdown: string,
    listPos: number,
    lineStarts: number[],
  ): void {
    const items = token.items;
    let itemCursor = 0;

    for (const item of items) {
      // Compute item's absolute position within the list's raw text
      const relativePos = token.raw.indexOf(item.raw, itemCursor);
      if (relativePos >= 0) {
        itemCursor = relativePos + item.raw.length;
      }
      const absolutePos = relativePos >= 0 ? listPos + relativePos : -1;
      const itemLineNum = absolutePos >= 0 ? this.getLineNumber(absolutePos, lineStarts) : 1;
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

  private validateHrToken(token: Tokens.Hr, errors: MarkdownError[], lineNum: number): void {
    errors.push({
      errorType: ErrorType.DISCORD_RENDER_ISSUE,
      line: lineNum,
      column: 1,
      context: token.raw.slice(0, 50),
      suggestion: 'Discord 不支援水平分隔線，建議改用其他方式分隔內容',
    });
  }

  private validateTableToken(token: Tokens.Table, errors: MarkdownError[], lineNum: number): void {
    errors.push({
      errorType: ErrorType.DISCORD_RENDER_ISSUE,
      line: lineNum,
      column: 1,
      context: token.raw.slice(0, 50),
      suggestion: 'Discord 不支援表格，請改用其他方式呈現結構化內容',
    });
  }

  /**
   * Additional regex-based format pass for heading/list syntax issues
   * that the AST parser may not flag (since marked normalizes some syntax).
   * Uses its own code fence tracking rather than the AST-based line set.
   * Uses compound regex expressions and early continue after each match (P2-10).
   */
  private regexFormatPass(lines: string[], errors: MarkdownError[]): void {
    // Compound regex for code fence detection
    const CODE_FENCE_RE = /^\s*(```|~~~)/;
    let inCodeBlock = false;
    let codeFenceChar = '';

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const lineNum = i + 1;

      // Track code fence state
      const fenceMatch = line.match(CODE_FENCE_RE);
      if (fenceMatch) {
        if (!inCodeBlock) {
          inCodeBlock = true;
          codeFenceChar = fenceMatch[1];
        } else if (line.trimStart().startsWith(codeFenceChar)) {
          inCodeBlock = false;
          codeFenceChar = '';
        }
        continue;
      }
      if (inCodeBlock) continue;

      const trimmed = line.trimStart();

      // Compound heading check: match heading marker and content in one expression
      const headingMatch = line.match(/^(#{1,})(.*)$/);
      if (headingMatch) {
        const hashes = headingMatch[1];
        const content = headingMatch[2];

        // Check heading level exceeded first
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
          continue;
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
          continue;
        }
      }

      // Check inline headings (## not at start of line)
      const inlineMatch = line.match(/(?<=[^\n#`])#{2,6}\s+\S/);
      if (inlineMatch && inlineMatch.index && inlineMatch.index > 0) {
        errors.push({
          errorType: ErrorType.HEADING_FORMAT,
          line: lineNum,
          column: (inlineMatch.index ?? 0) + 1,
          context: line.slice(
            Math.max(0, (inlineMatch.index ?? 0) - 5),
            (inlineMatch.index ?? 0) + 20,
          ),
          suggestion: '標題應在行首，而非行內',
        });
        continue;
      }

      // Skip empty lines for list checks
      if (!trimmed) continue;

      // Skip emphasis syntax (*text* and **text**) — these are not list markers
      if (/^\*[^*]+\*$/.test(trimmed) || /^\*\*[^*]+\*\*$/.test(trimmed)) continue;

      // Compound list format check: merge unordered and ordered list marker checks
      const listFormatMatch = trimmed.match(/^([-*+]|\d+\.)(\S)/);
      if (listFormatMatch) {
        const marker = listFormatMatch[1];
        const isOrdered = /^\d+\.$/.test(marker);
        if (isOrdered) {
          const prefixLen = marker.length;
          errors.push({
            errorType: ErrorType.MALFORMED_LIST,
            line: lineNum,
            column: line.indexOf(trimmed[0]) + prefixLen + 1,
            context: trimmed.slice(0, 50),
            suggestion: '編號列表後需要加上空格（如 "1. item"）',
          });
        } else {
          errors.push({
            errorType: ErrorType.MALFORMED_LIST,
            line: lineNum,
            column: line.indexOf(trimmed[0]) + 2,
            context: trimmed.slice(0, 50),
            suggestion: '列表標記後需要加上空格（如 "- item"）',
          });
        }
        continue;
      }

      // Check nested list indentation (compound pattern for list item detection)
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
          continue;
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
        continue;
      }
    }
  }
}

/**
 * MarkdownErrorFormatter for formatting validation errors into human-readable strings.
 */
export class MarkdownErrorFormatter {
  format(errors: MarkdownError[], _originalContent?: string): string {
    if (errors.length === 0) return '';

    const lines: string[] = ['Markdown 格式問題：'];

    for (const error of errors) {
      lines.push(`- 第 ${error.line} 行：${error.suggestion}（${error.errorType}）`);
    }

    return lines.join('\n');
  }
}
