package ltdjms.discord.aichat.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 回應模型，符合 OpenAI Chat Completions API 格式。
 *
 * @param id 回應 ID
 * @param object 物件類型
 * @param created 建立時間戳
 * @param model 使用的模型
 * @param choices 選擇列表
 * @param usage Token 使用量
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AIChatResponse(
    @JsonProperty("id") String id,
    @JsonProperty("object") String object,
    @JsonProperty("created") Long created,
    @JsonProperty("model") String model,
    @JsonProperty("choices") List<Choice> choices,
    @JsonProperty("usage") Usage usage) {

  /**
   * 選擇。
   *
   * @param index 選擇索引
   * @param message 訊息內容
   * @param finishReason 結束原因
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Choice(
      @JsonProperty("index") Integer index,
      @JsonProperty("message") AIMessage message,
      @JsonProperty("finish_reason") String finishReason) {

    /**
     * AI 訊息。
     *
     * @param role 角色 ("assistant")
     * @param content 回應內容
     * @param reasoningContent 推理內容
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AIMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("reasoning_content") String reasoningContent) {}
  }

  /**
   * Token 使用量。
   *
   * @param promptTokens 提示詞 Token 數
   * @param completionTokens 完成 Token 數
   * @param totalTokens 總 Token 數
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Usage(
      @JsonProperty("prompt_tokens") Integer promptTokens,
      @JsonProperty("completion_tokens") Integer completionTokens,
      @JsonProperty("total_tokens") Integer totalTokens) {}

  /** 提取第一個選擇的內容。 */
  public String getContent() {
    if (choices == null || choices.isEmpty()) {
      return "";
    }
    Choice first = choices.get(0);
    if (first.message() == null) {
      return "";
    }
    String content = first.message().content();
    return content == null ? "" : content;
  }
}
