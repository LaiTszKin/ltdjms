import { MarkdownAutoFixer } from './MarkdownAutoFixer.js';

/**
 * Code block placeholder for protection during fixes.
 */
const CODE_BLOCK_PLACEHOLDER = (index: number) => `\x00CODEBLOCK${index}\x00`;

/**
 * Regex-based Markdown auto-fixer.
 * Applies 14 fix steps in a strict order.
 * Matches Java RegexBasedAutoFixer.
 */
export class RegexBasedAutoFixer implements MarkdownAutoFixer {
  /**
   * Applies all auto-fixes in order.
   * Max 3 retry cycles.
   */
  autoFix(markdown: string): string {
    if (!markdown || markdown.trim().length === 0) {
      return markdown;
    }

    let result = markdown;
    let previous = '';

    // Max 3 retry cycles
    for (let cycle = 0; cycle < 3; cycle++) {
      if (result === previous) break;
      previous = result;

      // Extract code blocks for protection
      const codeBlocks: string[] = [];
      result = this.protectCodeBlocks(result, codeBlocks);

      // Apply fixes in strict order
      result = this.fixUnclosedCodeBlocks(result);
      result = this.fixHeadingLevelExceeded(result);
      result = this.fixInlineHeadings(result);
      result = this.fixHeadingFormat(result);
      result = this.fixHeadingContainsListMarker(result);
      result = this.fixHeadingInlineListItems(result);
      result = this.fixEmbeddedLists(result);
      result = this.fixInlineListMarkersInListLines(result);
      // Step 9-14: separate line-based fixes in strict order
      result = this.fixListFormat(result);
      result = this.normalizeUnorderedListMarkers(result);
      result = this.fixNestedListIndentation(result);
      result = this.fixDiscordUnderlineBold(result);
      result = this.fixTaskList(result);
      result = this.fixHorizontalRules(result);

      // Restore code blocks
      result = this.restoreCodeBlocks(result, codeBlocks);

      // Early exit: if no changes were made in this cycle, skip remaining retries
      if (result === previous) {
        break;
      }
    }

    return result;
  }

  // ===== Code Block Protection =====

  private protectCodeBlocks(text: string, store: string[]): string {
    return text.replace(
      /(```[\s\S]*?```|~~~[\s\S]*?~~~)/g,
      (match) => {
        store.push(match);
        return CODE_BLOCK_PLACEHOLDER(store.length - 1);
      },
    );
  }

  private restoreCodeBlocks(text: string, store: string[]): string {
    return text.replace(
      /\x00CODEBLOCK(\d+)\x00/g,
      (_, index) => store[Number(index)] ?? '',
    );
  }

  // ===== Fix 1: Unclosed Code Blocks =====

  private fixUnclosedCodeBlocks(text: string): string {
    const lines = text.split('\n');
    let inCodeBlock = false;
    let fenceChar = '';
    let result: string[] = [];

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      if (/^\s*(```|~~~)/.test(line)) {
        if (!inCodeBlock) {
          inCodeBlock = true;
          fenceChar = line.match(/^(\s*)(```|~~~)/)?.[2] ?? '```';
        } else {
          inCodeBlock = false;
        }
        result.push(line);
      } else if (inCodeBlock) {
        // Check if line looks like a non-code sentence (heuristic)
        if (
          /^[A-Z][a-z]+(?:\s+[a-z]+)+[.!?]?$/.test(line.trim())
        ) {
          // This looks like a plain sentence, close code block before it
          result.push(fenceChar);
          inCodeBlock = false;
          result.push(line);
        } else {
          result.push(line);
        }
      } else {
        result.push(line);
      }
    }

    // Close unclosed code block at end
    if (inCodeBlock) {
      result.push(fenceChar);
    }

    return result.join('\n');
  }

  // ===== Fix 2: Heading Level Exceeded =====

  private fixHeadingLevelExceeded(text: string): string {
    return text.replace(/^#{7,}\s/gm, '###### ');
  }

  // ===== Fix 3: Inline Headings =====

  private fixInlineHeadings(text: string): string {
    // Convert text## heading → text\n## heading
    // Requires a non-whitespace, non-#, non-backtick character before heading marker
    return text.replace(/([^\s#\n`])(#{2,6}\s+\S)/g, '$1\n$2');
  }

  // ===== Fix 4: Heading Format (space after #) =====

  private fixHeadingFormat(text: string): string {
    return text.replace(/^(#{1,6})(?!#)(\S)/gm, '$1 $2');
  }

  // ===== Fix 5: Heading Contains List Marker =====

  private fixHeadingContainsListMarker(text: string): string {
    // ### - title → ### title
    return text.replace(/^(#{1,6}\s+)[-*+]\s+/gm, '$1');
  }

  // ===== Fix 6: Heading Inline List Items =====

  private fixHeadingInlineListItems(text: string): string {
    // Move list markers from heading to a new list line
    return text.replace(/^(#{1,6}\s+.+?)([-*+]\s+.+)$/gm, '$1\n$2');
  }

  // ===== Fix 7: Embedded Lists =====

  private fixEmbeddedLists(text: string): string {
    // Convert list markers embedded in paragraph text: "text-item" → "text\n- item"
    // Protected: **bold**, *italic*, horizontal rules, and headings
    return text.replace(
      /([^\n\s])([-*+])(\s+\S)/g,
      (match, before, marker, after) => {
        // Skip if part of **bold** or *italic* or if it's a heading
        const lookBehind = before.slice(-10);
        if (/^#{1,6}\s/.test(lookBehind)) return match;
        // Skip if marker is part of ** pattern (bold/italic)
        if (before.endsWith('*') || before.endsWith('-') || before.endsWith('+')) return match;
        return `${before}\n${marker}${after}`;
      },
    );
  }

  // ===== Fix 8: Inline List Markers in List Lines =====

  private fixInlineListMarkersInListLines(text: string): string {
    // Split double list markers on the same list line
    return text.replace(/^(\s*[-*+]\s+.+?)([-*+])\s+/gm, '$1\n$2 ');
  }

  // ===== Fix 9: List Format (space after list markers) =====

  private fixListFormat(text: string): string {
    return text
      .split('\n')
      .map((line) => {
        const trimmed = line.trimStart();
        if (
          !/^[-*_]{3,}$/.test(trimmed) &&
          !/^\*[^*]+\*$/.test(trimmed) &&
          !/^\*\*[^*]+\*\*$/.test(trimmed) &&
          !/^#{1,6}\s/.test(trimmed) &&
          !/^\s*\*\*/.test(trimmed)
        ) {
          let fixed = line;
          fixed = fixed.replace(/^(\s*)([-*+])(\S)/, '$1$2 $3');
          fixed = fixed.replace(/^(\s*)(\d+)\.(\S)/, '$1$2. $3');
          return fixed;
        }
        return line;
      })
      .join('\n');
  }

  // ===== Fix 10: Normalize Unordered List Markers =====

  private normalizeUnorderedListMarkers(text: string): string {
    return text
      .split('\n')
      .map((line) => {
        if (/^\s*[*+]\s/.test(line)) {
          return line.replace(/^(\s*)[*+](\s)/, '$1-$2');
        }
        return line;
      })
      .join('\n');
  }

  // ===== Fix 11: Nested List Indentation =====

  private fixNestedListIndentation(text: string): string {
    return text
      .split('\n')
      .map((line) => {
        const listTrimmed = line.trimStart();
        if (listTrimmed && (/^[-*+]\s/.test(listTrimmed) || /^\d+\.\s/.test(listTrimmed))) {
          const leadingSpaces = line.length - listTrimmed.length;
          if (leadingSpaces > 0 && leadingSpaces % 4 !== 0) {
            const chars = ' '.repeat(Math.round(leadingSpaces / 4) * 4);
            return chars + listTrimmed;
          }
        }
        return line;
      })
      .join('\n');
  }

  // ===== Fix 14: Horizontal Rules =====

  private fixHorizontalRules(text: string): string {
    return text
      .split('\n')
      .filter((line) => {
        const trimmedStart = line.trimStart();
        return !(/^[-*_]{3,}$/.test(trimmedStart.trimEnd()) && trimmedStart.trimEnd().length >= 3);
      })
      .join('\n');
  }

  // ===== Fix 12: Discord Underline Bold → Asterisk Bold =====

  private fixDiscordUnderlineBold(text: string): string {
    // __text__ → **text** (but not inside code blocks, already protected)
    return text.replace(/__([^_\n]+)__/g, '**$1**');
  }

  // ===== Fix 13: Task List → Regular List =====

  private fixTaskList(text: string): string {
    // - [x] item → - item
    // - [ ] item → - item
    return text.replace(/^(\s*[-*+])\s*\[[ x]\]\s*/gim, '$1 ');
  }

}
