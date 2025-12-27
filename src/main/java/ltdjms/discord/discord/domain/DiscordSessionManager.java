package ltdjms.discord.discord.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import net.dv8tion.jda.api.interactions.InteractionHook;

/**
 * Discord 互動 Session 管理器
 *
 * <p>此介面提供 Session 的註冊、檢索、更新和失效功能。 Session 用於管理跨多次互動的狀態（例如使用者面板）。
 *
 * <h2>Session 生命週期：</h2>
 *
 * <ol>
 *   <li>透過 {@link #registerSession} 註冊新 Session
 *   <li>透過 {@link #getSession} 檢索 Session（自動過濾過期 Session）
 *   <li>Session 過期後自動失效（基於 TTL）
 *   <li>可透過 {@link #clearSession} 手動清除
 * </ol>
 *
 * <h2>泛型參數：</h2>
 *
 * <p>{@code K} 必須是實作 {@link SessionType} 的枚舉類型。
 *
 * <h2>使用範例：</h2>
 *
 * <pre>{@code
 * public enum MySessionType implements SessionType {
 *     USER_PANEL,
 *     ADMIN_PANEL
 * }
 *
 * DiscordSessionManager<MySessionType> manager = new InteractionSessionManager<>();
 *
 * // 註冊 Session
 * manager.registerSession(
 *     MySessionType.USER_PANEL,
 *     guildId,
 *     userId,
 *     hook,
 *     Map.of("page", 1)
 * );
 *
 * // 檢索 Session
 * Optional<Session<MySessionType>> sessionOpt =
 *     manager.getSession(MySessionType.USER_PANEL, guildId, userId);
 *
 * if (sessionOpt.isPresent()) {
 *     Session<MySessionType> session = sessionOpt.get();
 *     session.hook().editMessageEmbeds(newEmbed).queue();
 * }
 * }</pre>
 *
 * @param <K> Session 類型（必須是實作 SessionType 的枚舉）
 */
public interface DiscordSessionManager<K extends Enum<K> & SessionType> {

  /**
   * Discord Interaction Hook 的預設 TTL（秒）
   *
   * <p>Discord 規定 Interaction Hook 在 15 分鐘後失效。
   */
  long DEFAULT_TTL_SECONDS = 15 * 60; // 15 分鐘

  /**
   * 註冊一個新的 Session
   *
   * <p>如果同一組合（type, guildId, userId）已存在 Session， 將會被新的 Session 替換。
   *
   * @param type Session 類型
   * @param guildId Guild ID
   * @param userId 使用者 ID
   * @param hook InteractionHook
   * @param metadata 元資料映射（可為 null 或空）
   */
  void registerSession(
      K type, long guildId, long userId, InteractionHook hook, Map<String, Object> metadata);

  /**
   * 取得 Session
   *
   * <p>如果 Session 不存在或已過期，將返回空的 Optional。
   *
   * @param type Session 類型
   * @param guildId Guild ID
   * @param userId 使用者 ID
   * @return Optional 包含 Session，如果不存在或已過期則為空
   */
  Optional<Session<K>> getSession(K type, long guildId, long userId);

  /**
   * 清除指定的 Session
   *
   * <p>如果 Session 不存在，此方法不執行任何操作。
   *
   * @param type Session 類型
   * @param guildId Guild ID
   * @param userId 使用者 ID
   */
  void clearSession(K type, long guildId, long userId);

  /**
   * 清除所有過期的 Session
   *
   * <p>此方法會遍歷所有 Session，移除 {@link Session#isExpired()} 返回 true 的 Session。 建議定期呼叫此方法以釋放記憶體。
   */
  void clearExpiredSessions();

  /**
   * Session 記錄
   *
   * <p>此記錄包含 Session 的所有資訊，包括類型、Hook、建立時間、TTL 和元資料。
   *
   * @param <K> Session 類型
   */
  record Session<K>(
      K type,
      InteractionHook hook,
      Instant createdAt,
      long ttlSeconds,
      Map<String, Object> metadata) {
    /**
     * 檢查 Session 是否已過期
     *
     * @return true 如果已過期（當前時間超過 createdAt + ttlSeconds）
     */
    public boolean isExpired() {
      return Instant.now().isAfter(createdAt.plusSeconds(ttlSeconds));
    }

    /**
     * 取得 Session 的剩餘有效時間（秒）
     *
     * @return 剩餘秒數，如果已過期則返回 0 或負數
     */
    public long getRemainingSeconds() {
      Instant expirationTime = createdAt.plusSeconds(ttlSeconds);
      return java.time.Duration.between(Instant.now(), expirationTime).getSeconds();
    }
  }
}
