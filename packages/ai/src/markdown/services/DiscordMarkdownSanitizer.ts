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
   * Uses a single regex to match complete table blocks and replace them inline.
   * Detects tables by header separator line pattern.
   */
  private convertTablesToCodeBlocks(text: string): string {
    // Match table blocks: header row (contains |), separator row (|---| pattern), optional data rows (contain |)
    // Single regex pass instead of two-phase scanning
    const TABLE_BLOCK = /^[^\n]*\|[^\n]*\n\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*(?:\n[^\n]*\|[^\n]*)*/gm;

    return text.replace(TABLE_BLOCK, (match) => {
      return '```text\n' + match + '\n```';
    });
  }
}
