const HEADING_LINE = /^#{1,6}\s+.+/;

interface Heading {
  level: number;
  line: string;
}

/**
 * Stream segmenter that splits on heading lines while preserving heading context.
 * Matches Java MarkdownHeadingSegmenter.
 */
export class MarkdownHeadingSegmenter {
  private buffer = '';
  private currentSegment = '';
  private headingStack: Heading[] = [];
  private inCodeBlock = false;

  processChunk(chunk: string | null | undefined): string[] {
    if (chunk) {
      this.buffer += chunk;
    }

    const segments: string[] = [];
    let newlineIndex: number;
    while ((newlineIndex = this.buffer.indexOf('\n')) >= 0) {
      const line = this.buffer.slice(0, newlineIndex);
      this.buffer = this.buffer.slice(newlineIndex + 1);
      this.handleLine(line, true, segments);
    }
    return segments;
  }

  drain(): string {
    if (this.buffer.length > 0) {
      const line = this.buffer;
      this.buffer = '';
      this.handleLine(line, false, []);
    }
    const result = this.currentSegment;
    this.currentSegment = '';
    this.headingStack = [];
    this.inCodeBlock = false;
    return result.trim();
  }

  private handleLine(line: string, appendNewline: boolean, segments: string[]): void {
    const trimmed = line.trim();
    const isFence = trimmed.startsWith('```');
    if (isFence) {
      this.inCodeBlock = !this.inCodeBlock;
    }

    if (!this.inCodeBlock && HEADING_LINE.test(trimmed)) {
      this.flushSegment(segments);
      this.updateHeadingStack(trimmed, line);
      this.rebuildSegmentPrefix();
      if (appendNewline) {
        this.currentSegment += '\n';
      }
      return;
    }

    this.currentSegment += line;
    if (appendNewline) {
      this.currentSegment += '\n';
    }
  }

  private flushSegment(segments: string[]): void {
    const segment = this.currentSegment.trim();
    if (segment.length > 0) {
      segments.push(segment);
    }
    this.currentSegment = '';
  }

  private updateHeadingStack(trimmedLine: string, originalLine: string): void {
    let level = 0;
    while (level < trimmedLine.length && trimmedLine[level] === '#') {
      level++;
    }

    while (
      this.headingStack.length > 0 &&
      this.headingStack[this.headingStack.length - 1].level >= level
    ) {
      this.headingStack.pop();
    }
    this.headingStack.push({ level, line: originalLine });
  }

  private rebuildSegmentPrefix(): void {
    this.currentSegment = '';
    for (let i = 0; i < this.headingStack.length; i++) {
      this.currentSegment += this.headingStack[i].line;
      if (i < this.headingStack.length - 1) {
        this.currentSegment += '\n';
      }
    }
  }
}
