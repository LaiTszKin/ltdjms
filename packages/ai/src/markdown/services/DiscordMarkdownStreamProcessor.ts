import type { MarkdownAutoFixer } from '../autofix/MarkdownAutoFixer.js';
import type { MarkdownValidator } from '../validation/MarkdownValidator.js';
import { isValid } from '../types.js';
import { MarkdownHeadingSegmenter } from './MarkdownHeadingSegmenter.js';
import { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';

/**
 * Incremental stream markdown processor: segment, sanitize, validate, fix, paginate.
 * Matches Java DiscordMarkdownStreamProcessor.
 */
export class DiscordMarkdownStreamProcessor {
  constructor(
    private readonly segmenter: MarkdownHeadingSegmenter,
    private readonly validator: MarkdownValidator,
    private readonly autoFixer: MarkdownAutoFixer,
    private readonly sanitizer: DiscordMarkdownSanitizer,
    private readonly paginator: DiscordMarkdownPaginator,
  ) {}

  onChunk(chunk: string): string[] {
    const segments = this.segmenter.processChunk(chunk);
    return this.processSegments(segments);
  }

  flush(): string[] {
    const remaining = this.segmenter.drain();
    if (!remaining || remaining.trim().length === 0) {
      return [];
    }
    return this.processSegments([remaining]);
  }

  private processSegments(segments: string[]): string[] {
    const pages: string[] = [];
    for (const segment of segments) {
      if (!segment || segment.trim().length === 0) {
        continue;
      }

      const sanitizedOriginal = this.sanitizer.sanitize(segment);
      const originalValidation = this.validator.validate(sanitizedOriginal);
      if (isValid(originalValidation)) {
        pages.push(...this.paginator.paginate(sanitizedOriginal));
        continue;
      }

      const fixed = this.autoFixer.autoFix(sanitizedOriginal);
      const sanitizedFixed = this.sanitizer.sanitize(fixed);
      pages.push(...this.paginator.paginate(sanitizedFixed));
    }
    return pages;
  }
}
