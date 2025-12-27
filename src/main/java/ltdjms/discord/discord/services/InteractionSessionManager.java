package ltdjms.discord.discord.services;

import ltdjms.discord.discord.domain.DiscordSessionManager;
import ltdjms.discord.discord.domain.SessionType;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基於 InteractionHook 的泛型 SessionManager 實作
 *
 * <p>此實作使用 ConcurrentHashMap 來儲存 Session，
 * 支援多執行緒並發存取。
 *
 * <h2>Session Key 格式：</h2>
 * <pre>{SessionType}:{guildId}:{userId}</pre>
 *
 * <h2>預設 TTL：</h2>
 * <p>15 分鐘（900 秒），符合 Discord InteractionHook 的有效期限制。
 *
 * <h2>使用範例：</h2>
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
 *     123L, // guildId
 *     456L, // userId
 *     hook,
 *     Map.of("page", 1)
 * );
 *
 * // 檢索 Session
 * Optional<Session<MySessionType>> sessionOpt =
 *     manager.getSession(MySessionType.USER_PANEL, 123L, 456L);
 * }</pre>
 *
 * @param <K> Session 類型（必須是實作 SessionType 的枚舉）
 */
public class InteractionSessionManager<K extends Enum<K> & SessionType>
        implements DiscordSessionManager<K> {

    /**
     * Session 儲存映射
     *
     * <p>Key 格式：{type}:{guildId}:{userId}
     */
    private final ConcurrentHashMap<String, Session<K>> sessions;

    /**
     * 建立一個新的 InteractionSessionManager
     */
    public InteractionSessionManager() {
        this.sessions = new ConcurrentHashMap<>();
    }

    @Override
    public void registerSession(K type, long guildId, long userId,
                               InteractionHook hook, Map<String, Object> metadata) {
        String key = getKey(type, guildId, userId);
        Session<K> session = new Session<>(
            type,
            hook,
            Instant.now(),
            DEFAULT_TTL_SECONDS,
            metadata != null ? metadata : Map.of()
        );
        sessions.put(key, session);
    }

    @Override
    public Optional<Session<K>> getSession(K type, long guildId, long userId) {
        String key = getKey(type, guildId, userId);
        Session<K> session = sessions.get(key);

        if (session == null) {
            return Optional.empty();
        }

        // 檢查 Session 是否已過期
        if (session.isExpired()) {
            // 自動清理過期的 Session
            sessions.remove(key);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    @Override
    public void clearSession(K type, long guildId, long userId) {
        String key = getKey(type, guildId, userId);
        sessions.remove(key);
    }

    @Override
    public void clearExpiredSessions() {
        // 遍歷所有 Session，移除過期的
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 生成 Session Key
     *
     * <p>Key 格式：{type}:{guildId}:{userId}
     *
     * @param type    Session 類型
     * @param guildId Guild ID
     * @param userId  使用者 ID
     * @return Session Key
     */
    private String getKey(K type, long guildId, long userId) {
        return type.name() + ":" + guildId + ":" + userId;
    }

    /**
     * 取得當前 Session 數量（包含已過期的）
     *
     * <p>此方法主要用於測試和監控。
     *
     * @return Session 數量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 清除所有 Session（無論是否過期）
     *
     * <p>此方法主要用於測試。
     */
    public void clearAll() {
        sessions.clear();
    }
}
