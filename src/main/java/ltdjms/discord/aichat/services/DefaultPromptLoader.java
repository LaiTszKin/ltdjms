package ltdjms.discord.aichat.services;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static ltdjms.discord.shared.DomainError.unexpectedFailure;

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
public final class DefaultPromptLoader implements PromptLoader {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultPromptLoader.class);
  private static final String MD_EXTENSION = ".md";

  private final EnvironmentConfig config;

  public DefaultPromptLoader(EnvironmentConfig config) {
    this.config = config;
  }

  @Override
  public Result<SystemPrompt, DomainError> loadPrompts(boolean agentEnabled) {
    // 1. 載入 system/ 資料夾（必備）
    Result<SystemPrompt, DomainError> systemResult = loadFromDirectory("system");
    if (systemResult.isErr()) {
      return systemResult;
    }

    // 2. 如果啟用 Agent，載入 agent/ 資料夾（可選）
    SystemPrompt finalPrompt = systemResult.getValue();
    if (agentEnabled) {
      Result<SystemPrompt, DomainError> agentResult = loadFromDirectory("agent");
      if (agentResult.isOk()) {
        finalPrompt = combinePrompts(finalPrompt, agentResult.getValue());
      } else {
        // agent/ 不存在時記錄警告，但不影響運作
        LOG.warn("Agent prompts directory not found, using base prompt only");
      }
    }

    return Result.ok(finalPrompt);
  }

  /**
   * 從指定子資料夾載入提示詞。
   *
   * @param subDir 子資料夾名稱（如 "system" 或 "agent"）
   * @return 載入結果
   */
  private Result<SystemPrompt, DomainError> loadFromDirectory(String subDir) {
    Path promptsDir = Paths.get(config.getPromptsDirPath());
    Path targetDir = promptsDir.resolve(subDir);

    if (!exists(targetDir) || !isDirectory(targetDir)) {
      // system/ 是必備的，agent/ 是可選的
      if ("system".equals(subDir)) {
        return Result.err(
            unexpectedFailure("Required prompts directory not found: " + subDir, null));
      }
      return Result.err(unexpectedFailure("Optional prompts directory not found: " + subDir, null));
    }

    List<PromptSection> sections = new ArrayList<>();
    long maxSizeBytes = config.getPromptMaxSizeBytes();

    try (Stream<Path> stream = Files.list(targetDir)) {
      stream
          .filter(this::isValidPromptFile)
          .sorted()
          .forEach(
              file -> {
                try {
                  // 檢查檔案大小
                  long fileSize = Files.size(file);
                  if (fileSize > maxSizeBytes) {
                    LOG.warn(
                        "Skipping prompt file exceeding size limit: {} ({} bytes, max: {} bytes)",
                        file,
                        fileSize,
                        maxSizeBytes);
                    return;
                  }

                  // 讀取檔案內容
                  PromptSection section = loadPromptSection(file);
                  sections.add(section);
                } catch (IOException e) {
                  LOG.error("Failed to load prompt file: {}", file, e);
                }
              });
    } catch (IOException e) {
      return Result.err(unexpectedFailure("Failed to list prompts directory: " + subDir, e));
    }

    // 記錄載入統計
    MDC.put("directory", subDir);
    MDC.put("fileCount", String.valueOf(sections.size()));
    LOG.debug("Loaded {} prompt sections from {}/ directory", sections.size(), subDir);
    MDC.clear();

    // 按字母順序排序
    sections.sort(Comparator.comparing(PromptSection::title));

    return Result.ok(new SystemPrompt(sections));
  }

  /** 合併多個提示詞。 */
  private SystemPrompt combinePrompts(SystemPrompt base, SystemPrompt additional) {
    List<PromptSection> sections = new ArrayList<>(base.sections());
    sections.addAll(additional.sections());
    return new SystemPrompt(sections);
  }

  private boolean isValidPromptFile(Path file) {
    String fileName = file.getFileName().toString();
    return Files.isRegularFile(file)
        && fileName.endsWith(MD_EXTENSION)
        && !fileName.startsWith(".");
  }

  private PromptSection loadPromptSection(Path file) throws IOException {
    String fileName = file.getFileName().toString();
    String title = normalizeTitle(fileName);

    // 直接讀取整個檔案，保留原始換行符
    String content = Files.readString(file);

    return new PromptSection(title, content);
  }

  private String normalizeTitle(String fileName) {
    // 移除 .md 副檔名
    String title =
        fileName.endsWith(MD_EXTENSION)
            ? fileName.substring(0, fileName.length() - MD_EXTENSION.length())
            : fileName;

    // 空檔名處理
    if (title.isBlank()) {
      return "UNTITLED";
    }

    // 替換連字符和底線為空格
    title = title.replace("-", " ").replace("_", " ");

    // 只轉換 ASCII 字母為大寫，保留其他語言字元（如中文）
    StringBuilder result = new StringBuilder();
    for (char c : title.toCharArray()) {
      if (c >= 'a' && c <= 'z') {
        result.append(Character.toUpperCase(c));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}
