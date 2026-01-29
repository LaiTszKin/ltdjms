package ltdjms.discord.markdown.services;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ltdjms.discord.markdown.autofix.MarkdownAutoFixer;
import ltdjms.discord.markdown.validation.MarkdownValidator;

/** 串流 Markdown 處理器：標題分段、修復、驗證與分頁。 */
public final class DiscordMarkdownStreamProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(DiscordMarkdownStreamProcessor.class);

  private final MarkdownHeadingSegmenter segmenter;
  private final MarkdownValidator validator;
  private final MarkdownAutoFixer autoFixer;
  private final DiscordMarkdownSanitizer sanitizer;
  private final DiscordMarkdownPaginator paginator;

  public DiscordMarkdownStreamProcessor(
      MarkdownHeadingSegmenter segmenter,
      MarkdownValidator validator,
      MarkdownAutoFixer autoFixer,
      DiscordMarkdownSanitizer sanitizer,
      DiscordMarkdownPaginator paginator) {
    this.segmenter = segmenter;
    this.validator = validator;
    this.autoFixer = autoFixer;
    this.sanitizer = sanitizer;
    this.paginator = paginator;
  }

  public List<String> onChunk(String chunk) {
    List<String> segments = segmenter.processChunk(chunk);
    return processSegments(segments);
  }

  public List<String> flush() {
    String remaining = segmenter.drain();
    if (remaining == null || remaining.isBlank()) {
      return List.of();
    }
    return processSegments(List.of(remaining));
  }

  private List<String> processSegments(List<String> segments) {
    List<String> pages = new ArrayList<>();
    for (String segment : segments) {
      if (segment == null || segment.isBlank()) {
        continue;
      }
      String sanitizedOriginal = sanitizer.sanitize(segment);
      MarkdownValidator.ValidationResult originalValidation = validator.validate(sanitizedOriginal);
      if (originalValidation instanceof MarkdownValidator.ValidationResult.Valid) {
        pages.addAll(paginator.paginate(sanitizedOriginal));
        continue;
      }

      String fixed = autoFixer.autoFix(sanitizedOriginal);
      String sanitizedFixed = sanitizer.sanitize(fixed);
      MarkdownValidator.ValidationResult fixedValidation = validator.validate(sanitizedFixed);
      if (fixedValidation instanceof MarkdownValidator.ValidationResult.Invalid invalid) {
        LOG.warn("Markdown 分段驗證失敗: {} 個錯誤", invalid.errors().size());
      }
      pages.addAll(paginator.paginate(sanitizedFixed));
    }
    return pages;
  }
}
