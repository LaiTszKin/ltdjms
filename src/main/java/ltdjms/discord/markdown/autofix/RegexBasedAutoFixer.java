package ltdjms.discord.markdown.autofix;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基於正規表達式的 Markdown 自動修復器。
 *
 * <p>此實作使用正規表達式來識別和修復常見的 Markdown 格式錯誤。
 */
public class RegexBasedAutoFixer implements MarkdownAutoFixer {

  private static final Logger log = LoggerFactory.getLogger(RegexBasedAutoFixer.class);

  /** 正規表達式：匹配行首的連續 # 符號後直接跟隨非空白字符 例如: "#Heading" 或 "##Subheading" */
  private static final Pattern HEADING_WITHOUT_SPACE =
      Pattern.compile("^(#{1,6})([^\\s#].*)$", Pattern.MULTILINE);

  @Override
  public String autoFix(String markdown) {
    if (markdown == null || markdown.isEmpty()) {
      return markdown;
    }

    String result = markdown;

    // 應用修復（順序很重要）
    result = fixUnclosedCodeBlocks(result);
    result = fixHeadingFormat(result);

    return result;
  }

  /**
   * 修復未閉合的程式碼區塊。
   *
   * <p>計算程式碼區塊的開啟和閉合標記數量，如果不匹配則在文末添加閉合標記。
   *
   * <p>此外，會偵測程式碼區塊內的明顯非程式碼內容（如普通英文句子），並在該內容之前閉合程式碼區塊。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixUnclosedCodeBlocks(String markdown) {
    // 使用狀態機追蹤程式碼區塊
    StringBuilder result = new StringBuilder();
    boolean inCodeBlock = false;
    int codeBlockStart = -1;
    StringBuilder currentLine = new StringBuilder();

    for (int i = 0; i < markdown.length(); i++) {
      char c = markdown.charAt(i);

      // 檢查是否是程式碼區塊標記 (```)
      if (c == '`' && i + 2 < markdown.length()) {
        if (markdown.charAt(i + 1) == '`' && markdown.charAt(i + 2) == '`') {
          // 確保不是四個連續的 `
          boolean isQuad = i + 3 < markdown.length() && markdown.charAt(i + 3) == '`';
          if (!isQuad) {
            inCodeBlock = !inCodeBlock;
            if (inCodeBlock) {
              codeBlockStart = i;
              currentLine.setLength(0);
            } else {
              currentLine.setLength(0);
            }
            result.append("```");
            i += 2; // 跳過後兩個 `
            continue;
          }
        }
      }

      // 檢查是否遇到換行符
      if (c == '\n') {
        // 如果在程式碼區塊中，檢查當前行是否像非程式碼內容
        if (inCodeBlock && currentLine.length() > 0) {
          String lineContent = currentLine.toString().trim();
          // 如果這行看起來像普通英文句子
          if (looksLikePlainText(lineContent)) {
            log.debug("偵測到程式碼區塊中的普通文本：{}，提前閉合程式碼區塊", lineContent);
            // 移除最後一行（包括前面的換行符）
            int lineStart = result.length() - currentLine.length() - 1; // -1 for the newline
            result.setLength(lineStart);
            // 添加閉合標記（不包含額外的換行符，因為後面會添加）
            result.append("```");
            // 將當前行加入結果（保留原始格式）
            result.append("\n").append(lineContent);
            inCodeBlock = false;
            // 清空 currentLine 並繼續
            currentLine.setLength(0);
            // 不要在這裡返回，讓後面的邏輯處理換行符
          }
        }
        currentLine.setLength(0);
        result.append(c);
      } else {
        currentLine.append(c);
        result.append(c);
      }
    }

    // 處理最後一行
    if (inCodeBlock && currentLine.length() > 0) {
      String lineContent = currentLine.toString().trim();
      if (looksLikePlainText(lineContent)) {
        log.debug("偵測到程式碼區塊中的普通文本：{}，提前閉合程式碼區塊", lineContent);
        // 移除最後一行（因為它不是程式碼）
        int lineStart = result.length() - lineContent.length();
        result.setLength(lineStart);
        // 添加閉合標記和當前行
        result.append("```\n").append(lineContent);
        inCodeBlock = false;
      }
    }

    // 如果結束時仍在程式碼區塊中，添加閉合標記
    if (inCodeBlock) {
      log.debug("偵測到未閉合的程式碼區塊（位置: {}），自動閉合", codeBlockStart);
      result.append("\n```");
    }

    return result.toString();
  }

  /**
   * 判斷一行文字是否像普通文本而非程式碼。
   *
   * <p>簡單的啟發式規則：
   *
   * <ul>
   *   <li>包含常見的英文單詞（完整單詞匹配）
   *   <li>且不包含明顯的程式碼特徵
   *   <li>不是以 # 開頭的行（避免影響標題）
   * </ul>
   */
  private boolean looksLikePlainText(String line) {
    if (line.isEmpty()) {
      return false;
    }

    // 不處理以 # 開頭的行（可能是標題）
    if (line.trim().startsWith("#")) {
      return false;
    }

    // 檢查是否包含明顯的程式碼特徵 - 優先檢查這些
    if (line.contains("()") || line.contains(";")) {
      return false; // 像函數調用或語句
    }
    if (line.contains("{") || line.contains("}")) {
      return false; // 像程式碼區塊
    }

    // 檢查是否包含程式語言關鍵字（這些通常表示程式碼）
    String[] codeKeywords = {
      "class ",
      "interface ",
      "public ",
      "private ",
      "protected ",
      "static ",
      "void ",
      "int ",
      "String ",
      "return ",
      "import ",
      "package ",
      "extends ",
      "implements "
    };
    for (String keyword : codeKeywords) {
      if (line.contains(keyword)) {
        return false;
      }
    }

    // 檢查是否包含常見的英文小詞（完整單詞匹配，避免 "block1" 被誤判）
    String[] commonWords = {
      " some ", " text ", " after ", " the ", " and ", " this ", " that ", " with ", "some ",
      "text ", "after ", "the ", "and ", "this ", "that ", "with "
    };
    String lowerLine = " " + line.toLowerCase() + " "; // 添加空格以便匹配完整單詞
    for (String word : commonWords) {
      if (lowerLine.contains(word)) {
        return true;
      }
    }

    return false;
  }

  /**
   * 修復標題格式錯誤。
   *
   * <p>在 # 符號和標題文字之間插入空格。
   *
   * @param markdown 原始 Markdown
   * @return 修復後的 Markdown
   */
  private String fixHeadingFormat(String markdown) {
    // 先保護程式碼區塊
    List<String> codeBlocks = new ArrayList<>();
    String protectedContent = protectCodeBlocks(markdown, codeBlocks);

    // 修復非程式碼區塊中的標題
    String fixedContent = HEADING_WITHOUT_SPACE.matcher(protectedContent).replaceAll("$1 $2");

    // 還原程式碼區塊
    return restoreCodeBlocks(fixedContent, codeBlocks);
  }

  /** 保護程式碼區塊，替換為佔位符。 */
  private String protectCodeBlocks(String markdown, List<String> codeBlocks) {
    StringBuffer sb = new StringBuffer();
    Matcher matcher = Pattern.compile("```[\\s\\S]*?```").matcher(markdown);
    int lastEnd = 0;

    while (matcher.find()) {
      // 添加程式碼區塊之前的內容
      sb.append(markdown, lastEnd, matcher.start());
      // 儲存程式碼區塊
      codeBlocks.add(matcher.group());
      // 添加佔位符
      sb.append("\u0000CODE_BLOCK_").append(codeBlocks.size() - 1).append("\u0000");
      lastEnd = matcher.end();
    }

    sb.append(markdown.substring(lastEnd));
    return sb.toString();
  }

  /** 還原程式碼區塊。 */
  private String restoreCodeBlocks(String markdown, List<String> codeBlocks) {
    String result = markdown;
    for (int i = 0; i < codeBlocks.size(); i++) {
      result = result.replace("\u0000CODE_BLOCK_" + i + "\u0000", codeBlocks.get(i));
    }
    return result;
  }
}
