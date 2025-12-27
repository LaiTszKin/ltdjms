package ltdjms.discord.discord.domain;

/**
 * Discord Session 類型標記介面
 *
 * <p>所有 Session 類型枚舉都必須實作此介面。
 * 這允許 {@link DiscordSessionManager} 使用泛型約束，
 * 確保只有有效的 Session 類型可以被使用。
 *
 * <h2>使用範例：</h2>
 * <pre>{@code
 * public enum MySessionType implements SessionType {
 *     USER_PANEL,
 *     ADMIN_PANEL,
 *     SHOP_PURCHASE
 * }
 *
 * DiscordSessionManager<MySessionType> manager =
 *     new InteractionSessionManager<>();
 * }</pre>
 *
 * @see DiscordSessionManager
 */
public interface SessionType {
    // 標記介面，無需定義方法
}
