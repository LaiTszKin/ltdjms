package ltdjms.discord.aiagent.services;

/**
 * 工具執行上下文持有者。
 *
 * <p>使用 ThreadLocal 存儲當前工具執行的上下文信息（guildId、channelId、userId）， 以便 LangChain4J 的 @Tool 方法可以訪問這些信息。
 */
public final class ToolExecutionContext {

  private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

  private ToolExecutionContext() {
    // 工具類，不允許實例化
  }

  /**
   * 設置當前工具執行的上下文。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param userId 用戶 ID
   */
  public static void setContext(long guildId, long channelId, long userId) {
    CONTEXT.set(new Context(guildId, channelId, userId));
  }

  /** 清除當前上下文。 */
  public static void clearContext() {
    CONTEXT.remove();
  }

  /**
   * 獲取當前上下文。
   *
   * @return 上下文，如果未設置則拋出異常
   * @throws IllegalStateException 如果上下文未設置
   */
  public static Context getContext() {
    Context context = CONTEXT.get();
    if (context == null) {
      throw new IllegalStateException("工具執行上下文未設置，請先調用 setContext()");
    }
    return context;
  }

  /**
   * 檢查上下文是否已設置。
   *
   * @return 是否已設置
   */
  public static boolean isContextSet() {
    return CONTEXT.get() != null;
  }

  /**
   * 工具執行上下文數據。
   *
   * @param guildId 伺服器 ID
   * @param channelId 頻道 ID
   * @param userId 用戶 ID
   */
  public record Context(long guildId, long channelId, long userId) {}
}
