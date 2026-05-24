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

/** Agent non-thread conversations use a fixed message id, matching Java ConversationIdBuilder.build(..., -1). */
export const AGENT_NON_THREAD_MESSAGE_ID = '-1';

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
 * Prepares Discord message pages for agent final content.
 * When streamProcessed is false (validation bypassed/disabled), only splits raw text.
 */
export function prepareAgentFinalPages(
  finalResponse: string,
  streamProcessed: boolean,
  pipeline?: MarkdownPipelineComponents,
  splitter: MessageSplitter = new MessageSplitter(),
): string[] {
  const trimmed = finalResponse.trim();
  if (!trimmed) {
    return [];
  }

  if (!streamProcessed || !pipeline) {
    return splitter.split(trimmed);
  }

  const processor = buildMarkdownStreamProcessor(pipeline);
  const pages = [...processor.onChunk(trimmed), ...processor.flush()];
  if (pages.length === 0) {
    return splitter.split(trimmed);
  }
  return pages;
}
