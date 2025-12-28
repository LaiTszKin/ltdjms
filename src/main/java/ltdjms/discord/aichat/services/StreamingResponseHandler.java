package ltdjms.discord.aichat.services;

import ltdjms.discord.shared.DomainError;

/**
 * 流式回應處理器接口，用於接收 AI 模型的增量回應。
 *
 * <p>實作此接口以處理流式輸出的每個片段。
 *
 * <p>為了支持向後兼容，此接口提供兩種調用方式：
 *
 * <ul>
 *   <li>舊版三參數方法：{@code void onChunk(String, boolean, DomainError)}
 *   <li>新版四參數方法：{@code void onChunk(String, boolean, DomainError, ChunkType)}
 * </ul>
 *
 * <p>當調用四參數方法時，默認實現會調用三參數方法，實現向後兼容。
 */
public interface StreamingResponseHandler {

  /** 流式回應片段類型。 */
  enum ChunkType {
    /** 推理內容（reasoning_content）。 */
    REASONING,
    /** 實際回應內容（content）。 */
    CONTENT
  }

  /**
   * 處理流式回應片段（帶類型區分）。
   *
   * @param chunk 文本片段
   * @param isComplete 是否為最後一個片段
   * @param error 錯誤（如果發生）
   * @param type 片段類型（REASONING 或 CONTENT）
   */
  void onChunk(String chunk, boolean isComplete, DomainError error, ChunkType type);

  /**
   * 處理流式回應片段（向後兼容版本）。
   *
   * <p>此方法為向後兼容而保留，默認調用四參數版本並傳入 {@link ChunkType#CONTENT}。
   *
   * @param chunk 文本片段（空字串表示無新片段，僅表示狀態變化）
   * @param isComplete 是否為最後一個片段（流結束）
   * @param error 錯誤（如果發生），當 error 不為 null 時，chunk 和 isComplete 應被忽略
   */
  default void onChunk(String chunk, boolean isComplete, DomainError error) {
    onChunk(chunk, isComplete, error, ChunkType.CONTENT);
  }
}
