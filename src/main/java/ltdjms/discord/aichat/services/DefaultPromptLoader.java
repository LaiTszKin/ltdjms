package ltdjms.discord.aichat.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ltdjms.discord.aichat.domain.PromptSection;
import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;

/** 預設的提示詞載入器實作，從本地檔案系統載入 markdown 檔案。 */
public class DefaultPromptLoader implements PromptLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPromptLoader.class);

  private final EnvironmentConfig config;

  public DefaultPromptLoader(EnvironmentConfig config) {
    this.config = config;
  }

  @Override
  public Result<SystemPrompt, DomainError> loadPrompts() {
    Path promptsDir = Paths.get(config.getPromptsDirPath());
    long maxSizeBytes = config.getPromptMaxSizeBytes();

    // 如果資料夾不存在，回傳空的 SystemPrompt
    if (!Files.exists(promptsDir) || !Files.isDirectory(promptsDir)) {
      LOGGER.info("Prompts directory not found: {}, using empty system prompt", promptsDir);
      return Result.ok(SystemPrompt.empty());
    }

    List<PromptSection> sections = new ArrayList<>();
    int loadedCount = 0;
    int skippedCount = 0;

    try (Stream<Path> paths = Files.walk(promptsDir, 1)) {
      List<Path> files =
          paths
              .filter(Files::isRegularFile)
              .filter(p -> !p.getFileName().toString().startsWith("."))
              .filter(p -> p.toString().endsWith(".md"))
              .sorted()
              .toList();

      for (Path file : files) {
        try {
          // 檢查檔案大小
          long fileSize = Files.size(file);
          if (fileSize > maxSizeBytes) {
            LOGGER.warn(
                "Skipping prompt file exceeding size limit: {} ({} bytes, max: {} bytes)",
                file,
                fileSize,
                maxSizeBytes);
            skippedCount++;
            continue;
          }

          // 讀取檔案內容（UTF-8）
          String content = Files.readString(file);

          // 標準化檔案名稱為標題
          String title = normalizeTitle(file);
          sections.add(new PromptSection(title, content));
          loadedCount++;

        } catch (IOException e) {
          LOGGER.warn("Skipping prompt file due to read error: {}", file, e);
          skippedCount++;
        }
      }

      // 設定 MDC 並記錄結果
      MDC.put("prompts_dir", promptsDir.toString());
      MDC.put("files_loaded", String.valueOf(loadedCount));
      MDC.put("files_skipped", String.valueOf(skippedCount));

      if (loadedCount > 0) {
        LOGGER.info(
            "Loaded {} prompt files from {} ({} skipped)", loadedCount, promptsDir, skippedCount);
      } else if (files.isEmpty()) {
        LOGGER.info("No markdown files found in prompts directory: {}", promptsDir);
      } else {
        LOGGER.warn("All prompt files were skipped in: {}", promptsDir);
      }

    } catch (IOException e) {
      LOGGER.error("Failed to walk prompts directory: {}", promptsDir, e);
      return Result.err(DomainError.unexpectedFailure("Failed to load prompts", e));
    } finally {
      MDC.remove("prompts_dir");
      MDC.remove("files_loaded");
      MDC.remove("files_skipped");
    }

    // 按字母順序排序
    sections.sort(Comparator.comparing(PromptSection::title));

    return Result.ok(new SystemPrompt(sections));
  }

  /**
   * 標準化檔案名稱為區間標題。
   *
   * <p>規則：移除 .md 副檔名 → 替換連字符和底線為空格 → 轉大寫
   */
  private String normalizeTitle(Path path) {
    String fileName = path.getFileName().toString();

    // 移除 .md 副檔名
    if (fileName.endsWith(".md")) {
      fileName = fileName.substring(0, fileName.length() - 3);
    }

    // 空檔名處理
    if (fileName.isBlank()) {
      return "UNTITLED";
    }

    // 替換連字符和底線為空格
    fileName = fileName.replace("-", " ").replace("_", " ");

    // 轉換為大寫
    return fileName.toUpperCase();
  }
}
