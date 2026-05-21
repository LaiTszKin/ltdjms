import type { MarkdownAutoFixer } from '../autofix/MarkdownAutoFixer.js';
import type { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import type { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';
import type { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import { isValid } from '../types.js';
import pino from 'pino';

const logger = pino({ name: 'markdown-pipeline' });

const MAX_PROCESSING_TIME_MS = 500;

/**
 * 共用的 Markdown pipeline 工具函數。
 * Pipeline: Sanitize → AutoFix → Validate → Paginate
 *
 * 由 MarkdownValidatingAIChatService 使用，
 * 避免 pipeline 邏輯重複（P2-4）。
 */
export async function applyMarkdownPipeline(
  markdown: string,
  sanitizer: DiscordMarkdownSanitizer,
  autoFixer: MarkdownAutoFixer,
  validator: CommonMarkValidator,
  paginator: DiscordMarkdownPaginator,
): Promise<string[]> {
  if (!markdown) return [markdown];

  const startTime = Date.now();
  let result = markdown;

  // 1. Sanitize
  result = sanitizer.sanitize(result);

  // 2. AutoFix
  result = autoFixer.autoFix(result);

  // 3. Validate → if invalid, retry fix up to 3 times (spec R10)
  let validationResult = validator.validate(result);
  for (let attempt = 0; attempt < 3 && !isValid(validationResult); attempt++) {
    // Time budget guard: break out if we've exceeded MAX_PROCESSING_TIME_MS
    if (Date.now() - startTime > MAX_PROCESSING_TIME_MS) {
      logger.warn({ elapsed: Date.now() - startTime }, 'Markdown pipeline exceeded time budget, returning current result');
      break;
    }

    // Yield to event loop between retries to avoid blocking
    await new Promise<void>(resolve => setImmediate(resolve));
    result = autoFixer.autoFix(result);
    validationResult = validator.validate(result);
  }

  // 4. Paginate
  return paginator.paginate(result);
}
