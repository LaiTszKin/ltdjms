package ltdjms.discord.aichat.services;

import ltdjms.discord.shared.DomainError;

/**
 * 流式回應處理器接口，用於接收 AI 模型的增量回應。
 *
 * <p>實作此接口以處理流式輸出的每個片段。
 */
@FunctionalInterface
public interface StreamingResponseHandler {

  /**
   * 處理流式回應片段。
   *
   * @param chunk 文本片段（空字串表示無新片段，僅表示狀態變化）
   * @param isComplete 是否為最後一個片段（流結束）
   * @param error 錯誤（如果發生），當 error 不為 null 時，chunk 和 isComplete 應被忽略
   */
  void onChunk(String chunk, boolean isComplete, DomainError error);
}
