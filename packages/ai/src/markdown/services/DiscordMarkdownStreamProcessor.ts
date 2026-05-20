import { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import { RegexBasedAutoFixer } from '../autofix/RegexBasedAutoFixer.js';
import { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';
import { isValid, type ValidationResult } from '../types.js';

/**
 * Processes streaming Markdown content through the sanitize → fix → validate → paginate pipeline.
 * Matches Java DiscordMarkdownStreamProcessor.
 */
export class DiscordMarkdownStreamProcessor {
  private buffer = '';

  constructor(
    private readonly sanitizer: DiscordMarkdownSanitizer,
    private readonly autoFixer: RegexBasedAutoFixer,
    private readonly validator: CommonMarkValidator,
    private readonly paginator: DiscordMarkdownPaginator,
  ) {}

  /**
   * Processes a chunk and returns pages if ready.
   */
  onChunk(chunk: string): string[] {
    this.buffer += chunk;

    // Try to find a heading boundary as segment point
    const segmentIndex = this.findSegmentPoint(this.buffer);
    if (segmentIndex === -1) {
      return []; // Not enough content to segment yet
    }

    const segment = this.buffer.slice(0, segmentIndex);
    this.buffer = this.buffer.slice(segmentIndex);

    return this.processSegment(segment);
  }

  /**
   * Flushes remaining content and returns final pages.
   */
  flush(): string[] {
    if (this.buffer.length === 0) return [];
    const segment = this.buffer;
    this.buffer = '';
    return this.processSegment(segment);
  }

  /**
   * Processes a complete segment through the pipeline.
   */
  private processSegment(segment: string): string[] {
    // Pipeline: Sanitize → AutoFix → Validate → Paginate

    // 1. Sanitize
    let result = this.sanitizer.sanitize(segment);

    // 2. AutoFix
    result = this.autoFixer.autoFix(result);

    // 3. Validate
    const validationResult: ValidationResult = this.validator.validate(result);

    // If invalid, try one more fix cycle
    if (!isValid(validationResult)) {
      result = this.autoFixer.autoFix(result);
    }

    // 4. Paginate
    return this.paginator.paginate(result);
  }

  /**
   * Finds a segment point at heading boundaries.
   */
  private findSegmentPoint(content: string): number {
    if (content.length < 500) return -1; // Minimum threshold

    // Find the last heading line
    const lines = content.split('\n');
    for (let i = lines.length - 2; i >= 0; i--) {
      if (/^#{1,6}\s+\S/.test(lines[i])) {
        // Found a heading — segment at previous heading boundary
        // Find the previous heading or use beginning
        const lineStart = lines.slice(0, i).join('\n').length + 1;
        if (lineStart > content.length * 0.3) {
          return lineStart;
        }
      }
    }

    return -1;
  }
}
