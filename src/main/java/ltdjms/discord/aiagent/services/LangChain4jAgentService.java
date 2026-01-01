package ltdjms.discord.aiagent.services;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4J AI Agent 服務介面。
 *
 * <p>此介面使用 LangChain4J 的 @AiService 模式定義 AI 服務。 通過 AiServices.builder() 動態創建實現類。
 *
 * <p>支援串流回應，使用 {@link TokenStream} 進行增量輸出。
 */
public interface LangChain4jAgentService {

  /**
   * 與 AI 進行串流對話。
   *
   * <p>LangChain4J 會自動：
   *
   * <ul>
   *   <li>使用 ChatMemoryProvider 加載會話歷史
   *   <li>處理工具調用
   *   <li>管理多輪對話狀態
   *   <li>以串流方式返回回應
   * </ul>
   *
   * @param conversationId 會話 ID（用作 ChatMemory 的 memoryId）
   * @param userMessage 用戶訊息
   * @param parameters 調用參數（用於傳遞 guildId、channelId、userId 等上下文）
   * @return TokenStream 用於接收串流回應
   */
  @SystemMessage(
      """
      你是龍騰電競的 AI 助手，負責協助管理 Discord 伺服器。

      你可以幫助用戶執行以下操作：
      - 創建和管理頻道
      - 創建和管理類別
      - 查詢伺服器資訊

      當需要執行操作時，請使用提供的工具。

      回應規則：
      1. 使用繁體中文回應
      2. 保持友善專業的語氣
      3. 如果需要使用工具，請直接調用工具
      4. 工具執行後，向用戶報告結果
      """)
  TokenStream chat(
      @MemoryId String conversationId,
      @UserMessage String userMessage,
      InvocationParameters parameters);
}
