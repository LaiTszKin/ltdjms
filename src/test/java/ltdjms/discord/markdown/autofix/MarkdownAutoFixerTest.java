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
  @DisplayName("應該將行內標題拆成獨立一行")
  void shouldFixInlineHeading() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "前文##標題";
    String expected = "前文\n## 標題";

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
    String expected = "- First item\n- Second item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復缺少空格的無序列表（+ 符號）")
  void shouldFixUnorderedListWithPlusMissingSpace() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "+First item\n+Second item";
    String expected = "- First item\n- Second item";

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
  @DisplayName("不應該把純強調語法當成列表")
  void shouldNotTreatEmphasisAsList() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "**關於機器人功能：**\n*斜體內容*\n***加強斜體***\n**段落標題**：";
    String expected = "**關於機器人功能：**\n*斜體內容*\n***加強斜體***\n**段落標題**：";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復行內 CJK 無空格列表標記")
  void shouldFixInlineListMarkersWithoutSpacesInCjk() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "- 解答關於 Discord機器人功能的問題-協助使用 /user-panel";
    String expected = "- 解答關於 Discord機器人功能的問題\n- 協助使用 /user-panel";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該移除水平分隔線")
  void shouldRemoveHorizontalRule() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "---\n***\n___";
    String expected = "";

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
    String expected = "- Wrong\n- Correct\n1. Wrong\n2. Correct\n- Also wrong";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理縮排的列表")
  void shouldHandleIndentedLists() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "  -Indented item\n    -More indented";
    String expected = "  - Indented item\n      - More indented";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該將巢狀列表縮排調整為每層 4 個空格")
  void shouldNormalizeNestedListIndentation() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "- 第一層\n  - 第二層\n    - 第三層\n  - 第二層回到同層";
    String expected = "- 第一層\n    - 第二層\n        - 第三層\n    - 第二層回到同層";

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

  // ===== 嵌入列表修復測試 =====

  @Test
  @DisplayName("應該修復正文中嵌入的有序列表")
  void shouldFixEmbeddedOrderedList() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "Some text 1. first item 2. second item";
    String expected = "Some text\n1. first item\n2. second item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復正文中嵌入的無序列表")
  void shouldFixEmbeddedUnorderedList() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "Some text - first item - second item";
    String expected = "Some text\n- first item\n- second item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復標題行內的列表項")
  void shouldFixInlineListItemsAfterHeading() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "## 工具- 項目一- 項目二";
    String expected = "## 工具\n- 項目一\n- 項目二";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該修復列表行中缺少空格的行內列表項")
  void shouldFixInlineListItemsMissingSpaceInListLine() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "- 建立文字頻道 -創建新的文字頻道";
    String expected = "- 建立文字頻道\n- 創建新的文字頻道";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理多個嵌入的列表項")
  void shouldFixMultipleEmbeddedListItems() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "text 1. first 2. second 3. third";
    String expected = "text\n1. first\n2. second\n3. third";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該誤判版本號為列表")
  void shouldNotMisinterpretVersionNumbers() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "Version 1.2.3 is the latest";
    String expected = "Version 1.2.3 is the latest";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修復程式碼區塊中的嵌入列表")
  void shouldNotFixEmbeddedListsInCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\ntext 1. first 2. second\n```\ntext 1. first 2. second";
    String expected = "```\ntext 1. first 2. second\n```\ntext\n1. first\n2. second";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該處理嵌入列表與其他修復的組合")
  void shouldHandleEmbeddedListsWithOtherFixes() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "#Heading\ntext 1. first 2. second\n-Another item";
    String expected = "# Heading\ntext\n1. first\n2. second\n- Another item";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  // ===== 標題層級超限修復測試 =====

  @Test
  @DisplayName("應該修復超過 H6 層級的標題")
  void shouldFixHeadingLevelExceeded() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "####### 超過限制\n######## 更超過";
    String expected = "###### 超過限制\n###### 更超過";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修改符合限制的標題層級")
  void shouldNotModifyValidHeadingLevels() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input =
        """
        # 標題 1
        ## 標題 2
        ###### 標題 6
        """;

    String result = fixer.autoFix(input);
    assertEquals(input, result);
  }

  // ===== 標題中包含列表標記修復測試 =====

  @Test
  @DisplayName("應該移除標題中的無序列表標記")
  void shouldRemoveListMarkerFromHeading() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "### - 標題\n## * 另一個標題";
    String expected = "### 標題\n## 另一個標題";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("應該移除標題中的有序列表標記")
  void shouldRemoveOrderedListMarkerFromHeading() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "### 1. 標題\n## 2. 另一個";
    String expected = "### 標題\n## 另一個";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  // ===== Discord 底線粗體修復測試 =====

  @Test
  @DisplayName("應該將底線粗體轉換為星號粗體")
  void shouldConvertUnderlineBoldToAsterisk() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "這是 __粗體文字__ 的範例";
    String expected = "這是 **粗體文字** 的範例";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修改程式碼區塊中的底線")
  void shouldNotModifyUnderscoreInCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\n__variable_name__\n```\n__bold text__";
    String expected = "```\n__variable_name__\n```\n**bold text**";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  // ===== Task List 修復測試 =====

  @Test
  @DisplayName("應該將 Task List 轉換為普通列表")
  void shouldConvertTaskListToRegularList() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "- [x] 已完成\n- [ ] 未完成";
    String expected = "- 已完成\n- 未完成";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  @Test
  @DisplayName("不應該修改程式碼區塊中的 Task List 語法")
  void shouldNotModifyTaskListInCodeBlocks() {
    MarkdownAutoFixer fixer = new RegexBasedAutoFixer();

    String input = "```\n- [x] code task\n```\n- [x] real task";
    String expected = "```\n- [x] code task\n```\n- real task";

    String result = fixer.autoFix(input);
    assertEquals(expected, result);
  }

  // 巢狀列表縮排的修復已啟用，測試覆蓋於上方 shouldNormalizeNestedListIndentation
}
