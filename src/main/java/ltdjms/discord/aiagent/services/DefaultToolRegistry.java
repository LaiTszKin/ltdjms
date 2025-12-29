package ltdjms.discord.aiagent.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ltdjms.discord.aiagent.domain.ToolDefinition;
import ltdjms.discord.shared.DomainError;
import ltdjms.discord.shared.Result;
import ltdjms.discord.shared.Unit;

/**
 * ToolRegistry 的預設實作。
 *
 * <p>使用 ConcurrentHashMap 作為底層存儲，實現線程安全的工具註冊中心。
 */
public class DefaultToolRegistry implements ToolRegistry {

  private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
  private final Map<String, Tool> toolInstances = new ConcurrentHashMap<>();

  @Override
  public Result<Unit, DomainError> register(ToolDefinition tool) {
    if (tool == null) {
      return Result.err(DomainError.invalidInput("工具定義不能為 null"));
    }

    String toolName = tool.name();
    if (toolName == null || toolName.isBlank()) {
      return Result.err(DomainError.invalidInput("工具名稱不能為空"));
    }

    if (tools.containsKey(toolName)) {
      return Result.err(DomainError.invalidInput(String.format("工具 '%s' 已存在", toolName)));
    }

    tools.put(toolName, tool);
    return Result.okVoid();
  }

  @Override
  public Result<Unit, DomainError> unregister(String toolName) {
    if (toolName == null || toolName.isBlank()) {
      return Result.err(DomainError.invalidInput("工具名稱不能為空"));
    }

    if (!tools.containsKey(toolName)) {
      return Result.err(DomainError.invalidInput(String.format("工具 '%s' 不存在", toolName)));
    }

    tools.remove(toolName);
    return Result.okVoid();
  }

  @Override
  public Result<ToolDefinition, DomainError> getTool(String toolName) {
    if (toolName == null || toolName.isBlank()) {
      return Result.err(DomainError.invalidInput("工具名稱不能為空"));
    }

    ToolDefinition tool = tools.get(toolName);
    if (tool == null) {
      return Result.err(DomainError.invalidInput(String.format("工具 '%s' 不存在", toolName)));
    }

    return Result.ok(tool);
  }

  @Override
  public List<ToolDefinition> getAllTools() {
    return Collections.unmodifiableList(new ArrayList<>(tools.values()));
  }

  @Override
  public boolean isRegistered(String toolName) {
    if (toolName == null || toolName.isBlank()) {
      return false;
    }
    return tools.containsKey(toolName);
  }

  @Override
  public String getToolsPrompt() {
    if (tools.isEmpty()) {
      return "[]";
    }

    List<ToolDefinition> toolList = new ArrayList<>(tools.values());
    StringBuilder sb = new StringBuilder();
    sb.append("[");

    for (int i = 0; i < toolList.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(toolList.get(i).toJsonSchema());
    }

    sb.append("]");
    return sb.toString();
  }

  @Override
  public Tool getToolInstance(String toolName) {
    if (toolName == null || toolName.isBlank()) {
      return null;
    }
    return toolInstances.get(toolName);
  }

  /**
   * 註冊工具實例。
   *
   * @param tool 工具實例
   * @return 註冊結果
   */
  public Result<Unit, DomainError> registerToolInstance(Tool tool) {
    if (tool == null) {
      return Result.err(DomainError.invalidInput("工具實例不能為 null"));
    }

    String toolName = tool.name();
    if (toolName == null || toolName.isBlank()) {
      return Result.err(DomainError.invalidInput("工具名稱不能為空"));
    }

    toolInstances.put(toolName, tool);
    return Result.okVoid();
  }

  /**
   * 取消註冊工具實例。
   *
   * @param toolName 工具名稱
   */
  public void unregisterToolInstance(String toolName) {
    if (toolName != null) {
      toolInstances.remove(toolName);
    }
  }
}
