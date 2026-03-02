package ltdjms.discord.aichat.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 流式回應數據塊模型，符合 OpenAI SSE (Server-Sent Events) 格式。
 *
 * <p>用於解析流式 API 回應中的每一個 JSON 數據塊。
 *
 * @param id 回應 ID
 * @param object 物件類型 (chat.completion.chunk)
 * @param created 建立時間戳
 * @param model 使用的模型
 * @param choices 選擇列表
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AIChatStreamChunk(
    @JsonProperty("id") String id,
    @JsonProperty("object") String object,
    @JsonProperty("created") Long created,
    @JsonProperty("model") String model,
    @JsonProperty("choices") List<StreamChoice> choices) {

  /**
   * 流式選擇。
   *
   * @param index 選擇索引
   * @param delta 增量內容
   * @param finishReason 結束原因
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StreamChoice(
      @JsonProperty("index") Integer index,
      @JsonProperty("delta") Delta delta,
      @JsonProperty("finish_reason") String finishReason) {

    /**
     * 增量內容。
     *
     * @param content 新增的文本內容
     * @param reasoningContent 新增的推理內容
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(
        @JsonProperty("content") String content,
        @JsonProperty("reasoning_content") String reasoningContent) {}
  }

  /** 提取第一個選擇的增量內容。 */
  public String extractContent() {
    if (choices == null || choices.isEmpty()) {
      return null;
    }
    StreamChoice choice = choices.get(0);
    if (choice == null || choice.delta() == null) {
      return null;
    }
    return choice.delta().content();
  }

  /** 提取第一個選擇的推理增量內容。 */
  public String extractReasoningContent() {
    if (choices == null || choices.isEmpty()) {
      return null;
    }
    StreamChoice choice = choices.get(0);
    if (choice == null || choice.delta() == null) {
      return null;
    }
    return choice.delta().reasoningContent();
  }

  /** 檢查是否已結束（finish_reason 為 stop）。 */
  public boolean isFinished() {
    if (choices == null || choices.isEmpty()) {
      return false;
    }
    StreamChoice choice = choices.get(0);
    if (choice == null) {
      return false;
    }
    return "stop".equals(choice.finishReason());
  }
}
