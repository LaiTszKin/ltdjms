/**
 * Finds appropriate split points in markdown text based on headings,
 * respecting code block boundaries.
 *
 * Matches Java MarkdownHeadingSegmenter.
 */
export class MarkdownHeadingSegmenter {
  /**
   * Finds a segment point at heading boundaries, skipping headings
   * that appear inside code blocks.
   *
   * @param text - The markdown text to search for a split point
   * @param maxLength - Minimum content length before attempting to split (default: 500)
   * @returns The byte index of the split point, or -1 if no suitable point is found
   */
  findSegmentPoint(text: string, maxLength: number = 500): number {
    if (text.length < maxLength) return -1;

    const lines = text.split('\n');

    // Forward scan to determine code block boundaries per line
    const insideCodeBlock = new Array<boolean>(lines.length).fill(false);
    let inBlock = false;
    for (let i = 0; i < lines.length; i++) {
      if (/^```/.test(lines[i].trim())) {
        inBlock = !inBlock;
      }
      insideCodeBlock[i] = inBlock;
    }

    // Backward scan to find the last heading outside a code block
    for (let i = lines.length - 2; i >= 0; i--) {
      if (insideCodeBlock[i]) continue;

      if (/^#{1,6}\s+\S/.test(lines[i])) {
        // Found a heading — segment at the start of this heading line
        const lineStart = lines.slice(0, i).join('\n').length + 1;
        if (lineStart > text.length * 0.3) {
          return lineStart;
        }
      }
    }

    return -1;
  }
}
