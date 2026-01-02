package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import ltdjms.discord.aichat.domain.SystemPrompt;
import ltdjms.discord.aichat.services.DefaultPromptLoader;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.EnvironmentConfig;
import ltdjms.discord.shared.Result;

/** 測試 {@link DefaultPromptLoader} 的行為。 */
class PromptLoaderTest {

  private EnvironmentConfig mockConfig;
  private DefaultPromptLoader loader;

  @BeforeEach
  void setUp() {
    mockConfig = Mockito.mock(EnvironmentConfig.class);
  }

  @Test
  void testLoadPrompts_withSingleFile_returnsSystemPrompt(@TempDir Path tempDir)
      throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path testFile = systemDir.resolve("personality.md");
    Files.writeString(testFile, "You are a helpful bot.");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.isEmpty()).isFalse();
    assertThat(prompt.sectionCount()).isOne();
    assertThat(prompt.sections().get(0).title()).isEqualTo("PERSONALITY");
    assertThat(prompt.sections().get(0).content()).isEqualTo("You are a helpful bot.");
  }

  @Test
  void testLoadPrompts_withNonExistentDirectory_returnsError(@TempDir Path tempDir) {
    // Given
    Path nonExistentDir = tempDir.resolve("nonexistent");
    when(mockConfig.getPromptsDirPath()).thenReturn(nonExistentDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then - system/ 是必備的，應返回錯誤
    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.UNEXPECTED_FAILURE);
  }

  @Test
  void testLoadPrompts_withEmptyDirectory_returnsEmptyPrompt(@TempDir Path tempDir)
      throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.isEmpty()).isTrue();
  }

  @Test
  void testLoadPrompts_ignoresNonMarkdownFiles(@TempDir Path tempDir) throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path txtFile = systemDir.resolve("readme.txt");
    Files.writeString(txtFile, "This should be ignored");
    Path mdFile = systemDir.resolve("personality.md");
    Files.writeString(mdFile, "You are a helpful bot.");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sectionCount()).isOne();
    assertThat(prompt.sections().get(0).title()).isEqualTo("PERSONALITY");
  }

  @Test
  void testLoadPrompts_ignoresHiddenFiles(@TempDir Path tempDir) throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path hiddenFile = systemDir.resolve(".hidden.md");
    Files.writeString(hiddenFile, "This should be ignored");
    Path mdFile = systemDir.resolve("personality.md");
    Files.writeString(mdFile, "You are a helpful bot.");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sectionCount()).isOne();
  }

  @Test
  void testLoadPrompts_withFileContainingMarkdown(@TempDir Path tempDir) throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path testFile = systemDir.resolve("rules.md");
    String markdownContent =
        """
        # Rules

        1. Be helpful
        2. Be concise
        """;
    Files.writeString(testFile, markdownContent);

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sectionCount()).isOne();
    assertThat(prompt.sections().get(0).content()).isEqualTo(markdownContent);
  }

  @Test
  void testLoadPrompts_withChineseFileName_preservesChineseCharacters(@TempDir Path tempDir)
      throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path testFile = systemDir.resolve("系統設定.md");
    Files.writeString(testFile, "System configuration");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sections().get(0).title()).isEqualTo("系統設定");
  }

  @Test
  void testLoadPrompts_withMultipleFiles_sortsAlphabetically(@TempDir Path tempDir)
      throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path rulesFile = systemDir.resolve("rules.md");
    Path personalityFile = systemDir.resolve("personality.md");
    Files.writeString(rulesFile, "Rule 1");
    Files.writeString(personalityFile, "You are helpful");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sectionCount()).isEqualTo(2);
    // 按字母順序: PERSONALITY, RULES
    assertThat(prompt.sections().get(0).title()).isEqualTo("PERSONALITY");
    assertThat(prompt.sections().get(1).title()).isEqualTo("RULES");
  }

  @Test
  void testLoadPrompts_normalizesFileNameToTitle(@TempDir Path tempDir) throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path testFile = systemDir.resolve("bot-rules_v2.md");
    Files.writeString(testFile, "Rules content");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sections().get(0).title()).isEqualTo("BOT RULES V2");
  }

  @Test
  void testLoadPrompts_withMultipleFiles_formatsOutputWithSeparators(@TempDir Path tempDir)
      throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Path systemDir = promptsDir.resolve("system");
    Files.createDirectories(systemDir);
    Path rulesFile = systemDir.resolve("rules.md");
    Path formatFile = systemDir.resolve("format.md");
    Files.writeString(rulesFile, "Be helpful");
    Files.writeString(formatFile, "Use markdown");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    String combined = prompt.toCombinedString();
    assertThat(combined).contains("=== FORMAT ===");
    assertThat(combined).contains("=== RULES ===");
    assertThat(combined).contains("Use markdown");
    assertThat(combined).contains("Be helpful");
  }

  // ==================== 雙資料夾載入邏輯測試 ====================

  @Test
  @DisplayName("agentEnabled=false 時應只載入 system prompt")
  void loadPrompts_withAgentDisabled_shouldLoadSystemOnly(@TempDir Path tempDir)
      throws IOException {
    // 準備測試資料夾結構
    Path testDir = tempDir.resolve("prompts");
    Path systemDir = testDir.resolve("system");
    Files.createDirectories(systemDir);

    // 建立 system/ 測試檔案
    Files.writeString(systemDir.resolve("龍騰電競介紹.md"), "# 龍騰電競介紹");
    Files.writeString(systemDir.resolve("rules.md"), "# 基礎規則");

    // 建立但清空 agent/ 資料夾（驗證不會被讀取）
    Path agentDir = testDir.resolve("agent");
    Files.createDirectories(agentDir);
    Files.writeString(agentDir.resolve("agent.md"), "# Agent 說明（不應該被載入）");

    // 建立 config mock
    when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

    loader = new DefaultPromptLoader(mockConfig);

    // 執行
    Result<SystemPrompt, DomainError> result = loader.loadPrompts(false);

    // 驗證 - 按字母排序，英文會排在中文前面
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sections()).hasSize(2);
    assertThat(prompt.sections().get(0).title()).isEqualTo("RULES");
    assertThat(prompt.sections().get(1).title()).isEqualTo("龍騰電競介紹");
  }

  @Test
  @DisplayName("agentEnabled=true 時應載入 system + agent prompt")
  void loadPrompts_withAgentEnabled_shouldLoadBoth(@TempDir Path tempDir) throws IOException {
    Path testDir = tempDir.resolve("prompts");
    Path systemDir = testDir.resolve("system");
    Path agentDir = testDir.resolve("agent");
    Files.createDirectories(systemDir);
    Files.createDirectories(agentDir);

    Files.writeString(systemDir.resolve("龍騰電競介紹.md"), "# 龍騰電競介紹");
    Files.writeString(agentDir.resolve("Agent-說明.md"), "# Agent 說明");

    when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

    loader = new DefaultPromptLoader(mockConfig);

    Result<SystemPrompt, DomainError> result = loader.loadPrompts(true);

    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sections()).hasSize(2);
    // 按字母排序：中文排在英文前面
    assertThat(prompt.sections().get(0).title()).isEqualTo("龍騰電競介紹");
    assertThat(prompt.sections().get(1).title()).isEqualTo("AGENT 說明");
  }

  @Test
  @DisplayName("agent/ 不存在且 agentEnabled=true 時應記錄警告並繼續")
  void loadPrompts_agentDirMissing_shouldContinueWithWarning(@TempDir Path tempDir)
      throws IOException {
    Path testDir = tempDir.resolve("prompts");
    Path systemDir = testDir.resolve("system");
    Files.createDirectories(systemDir);

    Files.writeString(systemDir.resolve("龍騰電競介紹.md"), "# 龍騰電競介紹");

    when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

    loader = new DefaultPromptLoader(mockConfig);

    Result<SystemPrompt, DomainError> result = loader.loadPrompts(true);

    assertThat(result.isOk()).isTrue();
    assertThat(result.getValue().sections()).hasSize(1);
    assertThat(result.getValue().sections().get(0).title()).isEqualTo("龍騰電競介紹");
  }

  @Test
  @DisplayName("system/ 不存在時應返回錯誤")
  void loadPrompts_systemDirMissing_shouldReturnError(@TempDir Path tempDir) throws IOException {
    Path testDir = tempDir.resolve("prompts");
    Files.createDirectories(testDir);

    when(mockConfig.getPromptsDirPath()).thenReturn(testDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1024 * 1024L);

    loader = new DefaultPromptLoader(mockConfig);

    Result<SystemPrompt, DomainError> result = loader.loadPrompts(true);

    assertThat(result.isErr()).isTrue();
    assertThat(result.getError().category()).isEqualTo(DomainError.Category.UNEXPECTED_FAILURE);
    assertThat(result.getError().message()).contains("system");
  }
}
