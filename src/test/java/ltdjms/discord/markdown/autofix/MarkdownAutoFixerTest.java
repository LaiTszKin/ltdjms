package ltdjms.discord.markdown.autofix;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkdownAutoFixerTest {

  @Test
  void shouldDefineInterfaceContract() {
    // 此測試確保介面存在且有正確的方法簽名
    // 實際實作會在後續步驟完成
    assertNotNull(MarkdownAutoFixer.class);
  }

  @Test
  @DisplayName("應該修復缺少空格的標題格式")
  void shouldFixHeadingFormatMissingSpace() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "#This is a heading\n##Another heading";
    String expected = "# This is a heading\n## Another heading";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修復正確的標題格式")
  void shouldNotModifyCorrectHeadingFormat() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "# This is correct\n## So is this";
    String expected = "# This is correct\n## So is this";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理混合正確與錯誤的標題")
  void shouldHandleMixedHeadingFormats() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "#Wrong format\n# Correct format\n##Also wrong";
    String expected = "# Wrong format\n# Correct format\n## Also wrong";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該將程式碼區塊中的 # 視為標題")
  void shouldNotFixHashInCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\n#This is code\n```\n#This is heading";
    String expected = "```\n#This is code\n```\n# This is heading";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復未閉合的程式碼區塊")
  void shouldFixUnclosedCodeBlock() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\nconsole.log('hello');\nSome text after";
    String expected = "```\nconsole.log('hello');\n```\nSome text after";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理多個未閉合的程式碼區塊")
  void shouldFixMultipleUnclosedCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\nblock1\n```\n```\nblock2\nText";
    String expected = "```\nblock1\n```\n```\nblock2\n```\nText";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修復已閉合的程式碼區塊")
  void shouldNotModifyClosedCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\nconst x = 1;\n```\nNormal text";
    String expected = "```\nconst x = 1;\n```\nNormal text";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理帶語言標籤的程式碼區塊")
  void shouldFixCodeBlocksWithLanguageSpec() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```java\npublic class Test {}\nText";
    String expected = "```java\npublic class Test {}\n```\nText";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  // ===== 列表修復測試 =====

  @Test
  @DisplayName("應該修復缺少空格的無序列表（- 符號）")
  void shouldFixUnorderedListWithDashMissingSpace() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "-First item\n-Second item\n- Third item";
    String expected = "- First item\n- Second item\n- Third item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復缺少空格的無序列表（* 符號）")
  void shouldFixUnorderedListWithAsteriskMissingSpace() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "*First item\n*Second item";
    String expected = "* First item\n* Second item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復缺少空格的無序列表（+ 符號）")
  void shouldFixUnorderedListWithPlusMissingSpace() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "+First item\n+Second item";
    String expected = "+ First item\n+ Second item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復缺少空格的有序列表")
  void shouldFixOrderedListMissingSpace() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "1.First item\n2.Second item\n10.Tenth item\n3. Third item";
    String expected = "1. First item\n2. Second item\n10. Tenth item\n3. Third item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修復正確格式的列表")
  void shouldNotModifyCorrectListFormat() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "- First item\n- Second item\n1. First\n2. Second";
    String expected = "- First item\n- Second item\n1. First\n2. Second";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該將分隔線視為列表")
  void shouldNotModifyHorizontalRule() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "---\n***\n___";
    String expected = "---\n***\n___";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修復程式碼區塊中的列表")
  void shouldNotFixListsInCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\n-First item\n2.Second item\n```\n-Real list";
    String expected = "```\n-First item\n2.Second item\n```\n- Real list";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理混合的列表格式")
  void shouldHandleMixedListFormats() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "-Wrong\n- Correct\n1.Wrong\n2. Correct\n*Also wrong";
    String expected = "- Wrong\n- Correct\n1. Wrong\n2. Correct\n* Also wrong";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理縮排的列表")
  void shouldHandleIndentedLists() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "  -Indented item\n    -More indented";
    String expected = "  - Indented item\n    - More indented";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修復文字中的連字符或句號")
  void shouldNotModifyHyphensAndDotsInText() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "This is-text.\nVersion 1.2.3\nEnd of sentence.Next one";
    String expected = "This is-text.\nVersion 1.2.3\nEnd of sentence.Next one";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }
}
