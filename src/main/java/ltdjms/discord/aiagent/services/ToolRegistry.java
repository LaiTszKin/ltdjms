package ltdjms.discord.aiagent.services;

import java.util.List;

import ltdjms.discord.aiagent.domain.ToolDefinition;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * 工具註冊中心。
 *
 * <p>管理所有可被 AI 調用的系統工具，提供工具註冊、查詢和執行功能。
 */
public interface ToolRegistry {

  /**
   * 註冊一個工具。
   *
   * @param tool 工具定義
   * @return 註冊結果，失敗時返回錯誤（如工具名稱重複）
   */
  Result<Unit, ltdjms.discord.shared.DomainError> register(ToolDefinition tool);

  /**
   * 取消註冊工具。
   *
   * @param toolName 工具名稱
   * @return 取消註冊結果
   */
  Result<Unit, ltdjms.discord.shared.DomainError> unregister(String toolName);

  /**
   * 獲取工具定義。
   *
   * @param toolName 工具名稱
   * @return 工具定義，不存在時返回錯誤
   */
  Result<ToolDefinition, ltdjms.discord.shared.DomainError> getTool(String toolName);

  /**
   * 獲取所有已註冊的工具。
   *
   * @return 工具定義列表
   */
  List<ToolDefinition> getAllTools();

  /**
   * 檢查工具是否已註冊。
   *
   * @param toolName 工具名稱
   * @return 是否已註冊
   */
  boolean isRegistered(String toolName);

  /**
   * 獲取工具提示詞（用於注入 AI 系統提示詞）。
   *
   * @return JSON Schema 格式的工具定義
   */
  String getToolsPrompt();

  /**
   * 獲取工具實例。
   *
   * @param toolName 工具名稱
   * @return 工具實例，不存在時返回 null
   */
  Tool getToolInstance(String toolName);
}
