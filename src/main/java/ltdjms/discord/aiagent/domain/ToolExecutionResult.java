package ltdjms.discord.aiagent.domain;

import java.util.Optional;

/**
 * 工具執行結果。
 *
 * @param success 是否成功
 * @param result 成功時的結果資料
 * @param error 失敗時的錯誤訊息
 */
public record ToolExecutionResult(
    boolean success, Optional<String> result, Optional<String> error) {

  /**
   * 建立成功的執行結果。
   *
   * @param result 結果資料
   * @return 成功結果
   */
  public static ToolExecutionResult success(String result) {
    return new ToolExecutionResult(true, Optional.of(result), Optional.empty());
  }

  /**
   * 建立失敗的執行結果。
   *
   * @param error 錯誤訊息
   * @return 失敗結果
   */
  public static ToolExecutionResult failure(String error) {
    return new ToolExecutionResult(false, Optional.empty(), Optional.of(error));
  }
}
