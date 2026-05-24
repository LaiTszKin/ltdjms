import { MessageSplitter } from '../../services/MessageSplitter.js';
import { DiscordMarkdownStreamProcessor } from './DiscordMarkdownStreamProcessor.js';
import { MarkdownHeadingSegmenter } from './MarkdownHeadingSegmenter.js';
import type { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import type { MarkdownAutoFixer } from '../autofix/MarkdownAutoFixer.js';
import type { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import type { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';

export interface MarkdownPipelineComponents {
  validator: CommonMarkValidator;
  autoFixer: MarkdownAutoFixer;
  sanitizer: DiscordMarkdownSanitizer;
  paginator: DiscordMarkdownPaginator;
}

export function buildMarkdownStreamProcessor(
  pipeline: MarkdownPipelineComponents,
): DiscordMarkdownStreamProcessor {
  return new DiscordMarkdownStreamProcessor(
    new MarkdownHeadingSegmenter(),
    pipeline.validator,
    pipeline.autoFixer,
    pipeline.sanitizer,
    pipeline.paginator,
  );
}

/**
 * Prepares Discord message pages for agent final content when streamProcessed is false.
 * Agent mode with streamProcessed=true sends chunks raw (Java sendAgentFinalContent parity).
 */
export function prepareAgentFinalPages(
  finalResponse: string,
  splitter: MessageSplitter = new MessageSplitter(),
): string[] {
  const trimmed = finalResponse.trim();
  if (!trimmed) {
    return [];
  }
  return splitter.split(trimmed);
}
