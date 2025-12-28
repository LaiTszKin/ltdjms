package ltdjms.discord.aichat.services;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能分段累積器，用於累積流式輸出並在適當時機分割發送。
 *
 * <p>分割策略優先級：
 *
 * <ol>
 *   <li>段落分割（{@code \n\n}）- 優先分割
 *   <li>強制分割（1980 字元）- 兜底策略
 * </ol>
 */
public final class MessageChunkAccumulator {

  private static final int MAX_MESSAGE_LENGTH = 1980;
  private final StringBuilder buffer = new StringBuilder();

  /**
   * 累積增量內容，返回準備發送的片段列表。
   *
   * @param delta 新增的文本
   * @return 應該立即發送的片段列表（最多一個片段）
   */
  public List<String> accumulate(String delta) {
    if (delta != null && !delta.isEmpty()) {
      buffer.append(delta);
    }

    List<String> readyToSend = new ArrayList<>();

    // 1. 優先檢查段落分割（\n\n）
    int paragraphEnd = findFirstParagraphBoundary();
    if (paragraphEnd > 0 && paragraphEnd <= MAX_MESSAGE_LENGTH) {
      String chunk = buffer.substring(0, paragraphEnd);
      readyToSend.add(chunk);
      buffer.delete(0, paragraphEnd);
      return readyToSend;
    }

    // 2. 如果超過限制，強制分割
    if (buffer.length() >= MAX_MESSAGE_LENGTH) {
      readyToSend.add(buffer.substring(0, MAX_MESSAGE_LENGTH));
      buffer.delete(0, MAX_MESSAGE_LENGTH);
    }

    return readyToSend;
  }

  /**
   * 獲取剩餘內容（流結束時調用）。
   *
   * @return 剩餘的內容（已去除首尾空白）
   */
  public String drain() {
    String remaining = buffer.toString().trim();
    buffer.setLength(0);
    return remaining;
  }

  /**
   * 查找第一個段落邊界（\n\n）。
   *
   * @return 邊界位置（\n\n 之後的位置），如果找不到返回 -1
   */
  private int findFirstParagraphBoundary() {
    int idx = buffer.indexOf("\n\n");
    return idx >= 0 ? idx + 2 : -1;
  }
}
