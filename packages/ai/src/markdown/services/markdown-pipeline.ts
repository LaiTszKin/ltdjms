import type { MarkdownAutoFixer } from '../autofix/MarkdownAutoFixer.js';
import type { CommonMarkValidator } from '../validation/CommonMarkValidator.js';
import type { DiscordMarkdownPaginator } from './DiscordMarkdownPaginator.js';
import type { DiscordMarkdownSanitizer } from './DiscordMarkdownSanitizer.js';
import { isValid } from '../types.js';

/**
 * 共用的 Markdown pipeline 工具函數。
 * Pipeline: Sanitize → AutoFix → Validate → Paginate
 *
 * 由 MarkdownValidatingAIChatService 和 DiscordMarkdownStreamProcessor 共用，
 * 避免 pipeline 邏輯重複（P2-4）。
 */
export function applyMarkdownPipeline(
  markdown: string,
  sanitizer: DiscordMarkdownSanitizer,
  autoFixer: MarkdownAutoFixer,
  validator: CommonMarkValidator,
  paginator: DiscordMarkdownPaginator,
): string[] {
  if (!markdown) return [markdown];

  let result = markdown;

  // 1. Sanitize
  result = sanitizer.sanitize(result);

  // 2. AutoFix
  result = autoFixer.autoFix(result);

  // 3. Validate → if invalid, retry fix up to 3 times (spec R10)
  let validationResult = validator.validate(result);
  for (let attempt = 0; attempt < 3 && !isValid(validationResult); attempt++) {
    result = autoFixer.autoFix(result);
    validationResult = validator.validate(result);
  }

  // 4. Paginate
  return paginator.paginate(result);
}
