/**
 * Sanitizes Markdown for Discord compatibility.
 * Matches Java DiscordMarkdownSanitizer.
 *
 * Operations:
 * 1. Remove HTML comments (<!-- ... -->)
 * 2. Remove HTML tags (<...>)
 * 3. Flatten nested blockquotes (>> → >)
 * 4. Convert tables to ```text code blocks
 */
export class DiscordMarkdownSanitizer {
  /**
   * Sanitizes Markdown content for Discord display.
   */
  sanitize(markdown: string): string {
    if (!markdown) return markdown;

    let result = markdown;

    // Extract code blocks for protection
    const codeBlocks: string[] = [];
    result = this.protectCodeBlocks(result, codeBlocks);

    // 1. Remove HTML comments
    result = result.replace(/<!--[\s\S]*?-->/g, '');

    // 2. Remove HTML tags (but keep self-closing br and hr simple)
    result = result.replace(/<[^>]*>/g, '');

    // 3. Flatten nested blockquotes (e.g. ">> > text" → "> text")
    // Regex matches leading blockquote prefix only; the rest of the line is preserved.
    result = result.replace(/^(?:\s*>\s*)+/gm, '> ');

    // 4. Convert tables to ```text code blocks
    result = this.convertTablesToCodeBlocks(result);

    // 5. Remove trailing whitespace on each line（P3-10：移至 pipeline 前端，僅執行一次）
    result = result.replace(/[ \t]+$/gm, '');

    // Restore code blocks
    result = this.restoreCodeBlocks(result, codeBlocks);

    return result;
  }

  private protectCodeBlocks(text: string, store: string[]): string {
    return text.replace(
      /(```[\s\S]*?```|~~~[\s\S]*?~~~)/g,
      (match) => {
        store.push(match);
        return `\x00CODEBLOCK${store.length - 1}\x00`;
      },
    );
  }

  private restoreCodeBlocks(text: string, store: string[]): string {
    return text.replace(
      /\x00CODEBLOCK(\d+)\x00/g,
      (_, index) => store[Number(index)] ?? '',
    );
  }

  /**
   * Converts Markdown tables to ```text code blocks.
   * Detects tables by header separator line pattern.
   * Two-phase processing: first scan to identify table regions, then build result.
   */
  private convertTablesToCodeBlocks(text: string): string {
    const lines = text.split('\n');
    const TABLE_SEPARATOR = /^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$/;

    // Phase 1: Scan for table regions (ranges of line indices [start, end))
    const tableRegions: Array<{ start: number; end: number }> = [];
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      if (TABLE_SEPARATOR.test(line)) {
        // Table starts at the previous line (header) or this line if at index 0
        const start = i > 0 ? i - 1 : i;
        let end = i + 1; // end is exclusive

        // Scan forward for table rows (lines starting with | or containing |)
        i++;
        while (i < lines.length) {
          const nextLine = lines[i];
          if (nextLine.trimStart().startsWith('|') || nextLine.includes('|')) {
            end = i + 1;
            i++;
          } else {
            break;
          }
        }
        tableRegions.push({ start, end });
      } else {
        i++;
      }
    }

    // Phase 2: Build result, replacing table regions with code blocks
    const result: string[] = [];
    let lastEnd = 0;
    for (const region of tableRegions) {
      // Add lines before this table
      for (let j = lastEnd; j < region.start; j++) {
        result.push(lines[j]);
      }
      // Convert table region to code block
      const tableContent = lines.slice(region.start, region.end).join('\n');
      result.push('```text\n' + tableContent + '\n```');
      lastEnd = region.end;
    }
    // Add remaining lines after last table
    for (let j = lastEnd; j < lines.length; j++) {
      result.push(lines[j]);
    }

    return result.join('\n');
  }
}
