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

    // 3. Flatten nested blockquotes
    result = result.replace(/^(>\s*)+>/gm, (match) => {
      // Collapse multiple > levels into single >
      const content = match.replace(/^>\s*/, '');
      return '>' + content;
    });

    // 4. Convert tables to ```text code blocks
    result = this.convertTablesToCodeBlocks(result);

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
   */
  private convertTablesToCodeBlocks(text: string): string {
    const lines = text.split('\n');
    const result: string[] = [];
    let inTable = false;
    let tableLines: string[] = [];
    let tableStartIndex = -1;

    const TABLE_SEPARATOR = /^\s*\|?\s*[:\-]+(?:\s*\|\s*[:\-]+)+\s*\|?\s*$/;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      // Check if this is a table separator line
      if (TABLE_SEPARATOR.test(line)) {
        if (!inTable) {
          inTable = true;
          tableLines = [];

          // Include the previous line as table header
          if (i > 0) {
            tableLines.push(lines[i - 1]);
            // Remove the header line from result
            if (result.length > 0) {
              let popped = result.pop();
              // Skip if it's already a code block marker
              if (popped && (popped.startsWith('```') || popped.startsWith('~~~'))) {
                result.push(popped);
                tableLines = [];
                inTable = false;
                result.push(line);
                continue;
              }
            }
          }

          tableLines.push(line);
          tableStartIndex = result.length;
        } else {
          tableLines.push(line);
        }
        continue;
      }

      if (inTable) {
        // Check if line is a table row (starts with |)
        if (line.trimStart().startsWith('|') || line.includes('|')) {
          tableLines.push(line);
          continue;
        } else {
          // End of table — convert to code block
          if (tableLines.length > 0) {
            const codeBlock = '```text\n' + tableLines.join('\n') + '\n```';
            result.push(codeBlock);
          }
          inTable = false;
          tableLines = [];
        }
      }

      result.push(line);
    }

    // Handle table at end of content
    if (inTable && tableLines.length > 0) {
      const codeBlock = '```text\n' + tableLines.join('\n') + '\n```';
      result.push(codeBlock);
    }

    return result.join('\n');
  }
}
