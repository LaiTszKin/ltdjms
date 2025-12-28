package ltdjms.discord.aichat.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
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
    Files.createDirectory(promptsDir);
    Path testFile = promptsDir.resolve("personality.md");
    Files.writeString(testFile, "You are a helpful bot.");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.isEmpty()).isFalse();
    assertThat(prompt.sectionCount()).isOne();
    assertThat(prompt.sections().get(0).title()).isEqualTo("PERSONALITY");
    assertThat(prompt.sections().get(0).content()).isEqualTo("You are a helpful bot.");
  }

  @Test
  void testLoadPrompts_withNonExistentDirectory_returnsEmptyPrompt(@TempDir Path tempDir) {
    // Given
    Path nonExistentDir = tempDir.resolve("nonexistent");
    when(mockConfig.getPromptsDirPath()).thenReturn(nonExistentDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

    // Then - 返回空的 SystemPrompt，而非錯誤
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.isEmpty()).isTrue();
  }

  @Test
  void testLoadPrompts_withEmptyDirectory_returnsEmptyPrompt(@TempDir Path tempDir)
      throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Files.createDirectory(promptsDir);

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.isEmpty()).isTrue();
  }

  @Test
  void testLoadPrompts_ignoresNonMarkdownFiles(@TempDir Path tempDir) throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Files.createDirectory(promptsDir);
    Path txtFile = promptsDir.resolve("readme.txt");
    Files.writeString(txtFile, "This should be ignored");
    Path mdFile = promptsDir.resolve("personality.md");
    Files.writeString(mdFile, "You are a helpful bot.");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

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
    Files.createDirectory(promptsDir);
    Path hiddenFile = promptsDir.resolve(".hidden.md");
    Files.writeString(hiddenFile, "This should be ignored");
    Path mdFile = promptsDir.resolve("personality.md");
    Files.writeString(mdFile, "You are a helpful bot.");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    assertThat(prompt.sectionCount()).isOne();
  }

  @Test
  void testLoadPrompts_withFileContainingMarkdown(@TempDir Path tempDir) throws IOException {
    // Given
    Path promptsDir = tempDir.resolve("prompts");
    Files.createDirectory(promptsDir);
    Path testFile = promptsDir.resolve("rules.md");
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
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

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
    Files.createDirectory(promptsDir);
    Path testFile = promptsDir.resolve("系統設定.md");
    Files.writeString(testFile, "System configuration");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

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
    Files.createDirectory(promptsDir);
    Path rulesFile = promptsDir.resolve("rules.md");
    Path personalityFile = promptsDir.resolve("personality.md");
    Files.writeString(rulesFile, "Rule 1");
    Files.writeString(personalityFile, "You are helpful");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

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
    Files.createDirectory(promptsDir);
    Path testFile = promptsDir.resolve("bot-rules_v2.md");
    Files.writeString(testFile, "Rules content");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

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
    Files.createDirectory(promptsDir);
    Path rulesFile = promptsDir.resolve("rules.md");
    Path formatFile = promptsDir.resolve("format.md");
    Files.writeString(rulesFile, "Be helpful");
    Files.writeString(formatFile, "Use markdown");

    when(mockConfig.getPromptsDirPath()).thenReturn(promptsDir.toString());
    when(mockConfig.getPromptMaxSizeBytes()).thenReturn(1048576L);

    loader = new DefaultPromptLoader(mockConfig);

    // When
    Result<SystemPrompt, DomainError> result = loader.loadPrompts();

    // Then
    assertThat(result.isOk()).isTrue();
    SystemPrompt prompt = result.getValue();
    String combined = prompt.toCombinedString();
    assertThat(combined).contains("=== FORMAT ===");
    assertThat(combined).contains("=== RULES ===");
    assertThat(combined).contains("Use markdown");
    assertThat(combined).contains("Be helpful");
  }
}
