// Paginator uses a tighter limit (1900) than the raw splitter (1980) to leave room
// for code fence wrappers and other pipeline additions during pagination.
const MAX_MESSAGE_LENGTH = 1900;
const CODE_FENCE_RESERVED = 4; // 4 chars for code fence closure: \n```

/**
 * Splits long Markdown content into Discord-compatible pages.
 * Matches Java DiscordMarkdownPaginator.
 *
 * Splitting priority:
 * 1. At heading boundaries (^#{1,6}\s+.+)
 * 2. At code fence boundaries
 * 3. Hard split at 1900 chars
 */
export class DiscordMarkdownPaginator {
  private readonly maxLength: number;

  constructor(maxLength: number = MAX_MESSAGE_LENGTH) {
    this.maxLength = maxLength;
  }

  /**
   * Paginates Markdown content into chunks.
   * Returns empty array for null/empty content.
   */
  paginate(content: string | null | undefined): string[] {
    if (!content || content.trim().length === 0) {
      return [];
    }

    if (content.length <= this.maxLength) {
      return [content];
    }

    const pages: string[] = [];
    let remaining = content;
    let openCodeFence: string | null = null;

    while (remaining.length > 0) {
      const previousLength = remaining.length;

      if (remaining.length <= this.maxLength) {
        // Close any open code fence
        if (openCodeFence) {
          remaining += '\n```';
        }
        pages.push(remaining);
        break;
      }

      // Find split point within maxLength boundary
      const slice = remaining.slice(0, this.maxLength);

      // Priority 1: Split at heading boundary (before a heading)
      // Search backwards from maxLength for a heading
      const headingMatch = this.findLastHeadingBefore(slice);
      if (headingMatch !== -1 && headingMatch > this.maxLength * 0.3) {
        // Found a heading boundary
        const pageContent = remaining.slice(0, headingMatch);
        const { closed, fence } = this.handleCodeFenceBoundary(
          pageContent,
          openCodeFence,
        );
        pages.push(closed);
        openCodeFence = fence;

        remaining = remaining.slice(headingMatch).trimStart();
        if (openCodeFence !== null) {
          remaining = `\`\`\`${openCodeFence}\n` + remaining;
        }
        continue;
      }

      // Priority 2: Split at code fence boundary
      const fenceBoundary = this.findCodeFenceBoundary(slice);
      if (fenceBoundary !== -1 && fenceBoundary > 0) {
        const pageContent = remaining.slice(0, fenceBoundary);
        const { closed, fence } = this.handleCodeFenceBoundary(
          pageContent,
          openCodeFence,
        );
        pages.push(closed);
        openCodeFence = fence;

        remaining = remaining.slice(fenceBoundary).trimStart();
        if (openCodeFence !== null) {
          remaining = `\`\`\`${openCodeFence}\n` + remaining;
        }
        continue;
      }

      // Priority 3: Hard split
      const pageContent = remaining.slice(0, this.maxLength);
      const { closed, fence } = this.handleCodeFenceBoundary(
        pageContent,
        openCodeFence,
      );
      pages.push(closed);
      openCodeFence = fence;

      remaining = remaining.slice(this.maxLength).trimStart();
      if (openCodeFence !== null) {
        remaining = `\`\`\`${openCodeFence}\n` + remaining;
      }
    }

    return pages;
  }

  /**
   * Finds the last heading boundary before maxLength.
   */
  private findLastHeadingBefore(text: string): number {
    const headingRegex = /^#{1,6}\s+.+$/gm;
    let lastMatch = -1;
    let match: RegExpExecArray | null;

    while ((match = headingRegex.exec(text)) !== null) {
      // Heading should be at start of a line — find the line start
      const lineStart = text.lastIndexOf('\n', match.index) + 1;
      if (lineStart === 0) continue; // Don't split at first line
      lastMatch = lineStart;
    }

    return lastMatch;
  }

  /**
   * Finds a code fence boundary before maxLength.
   * Fences at position 0 are ignored (can't split at start).
   */
  private findCodeFenceBoundary(text: string): number {
    // Find the last ``` or ~~~ within the slice (excluding position 0)
    const fenceRegex = /\n```|\n~~~/g;
    let lastMatch = -1;
    let match: RegExpExecArray | null;

    while ((match = fenceRegex.exec(text)) !== null) {
      lastMatch = match.index + 1; // +1 to include the newline
    }

    return lastMatch;
  }

  /**
   * Handles code fence boundaries when splitting pages.
   * Ensures code fences are properly closed/opened across page boundaries.
   *
   * Uses a state machine: scan the page line-by-line, tracking whether we are
   * inside a code fence, so we know whether a fence started on a previous page
   * is closed in this one, or a new fence was opened without a close.
   */
  private handleCodeFenceBoundary(
    pageContent: string,
    openFence: string | null,
  ): { closed: string; fence: string | null } {
    let result = pageContent;
    let newFence: string | null = null;

    // Scan line-by-line to determine whether an open fence is closed
    // or a new unclosed fence was opened in this page
    let insideFence = openFence !== null;
    const lines = pageContent.split('\n');

    for (const line of lines) {
      const trimmed = line.trim();
      if (/^(`{3,})/.test(trimmed) || /^(~{3,})/.test(trimmed)) {
        insideFence = !insideFence;
      }
    }

    if (openFence) {
      // Entered this page with an open code fence from a previous page
      if (!insideFence) {
        // The fence was closed in this page
        newFence = null;
      } else {
        // Fence is still open — carry it forward
        newFence = openFence;
      }
    } else if (insideFence) {
      // A new code fence was opened but not closed — close it for this page
      result = result.trimEnd() + '\n```';
      newFence = '```';
      // Trim to stay within maxLength if needed
      if (result.length > this.maxLength + CODE_FENCE_RESERVED) {
        result = result.slice(0, this.maxLength);
        result = result.trimEnd() + '\n```';
      }
    }
    // else: no open fence and fences are balanced → newFence stays null

    return { closed: result.replace(/[ \t]+$/gm, ''), fence: newFence };
  }
}
