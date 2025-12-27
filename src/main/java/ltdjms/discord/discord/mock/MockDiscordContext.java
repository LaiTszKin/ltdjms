package ltdjms.discord.discord.mock;

import ltdjms.discord.discord.domain.DiscordContext;
import net.dv8tion.jda.api.entities.User;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 實作的 Discord 事件上下文
 *
 * <p>此類別提供 {@link DiscordContext} 介面的 Mock 實作，用於單元測試。
 * 允許測試者設定和檢查上下文值，而無需依賴 JDA 事件物件。
 *
 * <p>主要功能：
 * <ul>
 *   <li>建構時設定 Guild、使用者、頻道 ID 和 Mention</li>
 *   <li>動態設定和取得命令選項</li>
 *   <li>清除選項值</li>
 * </ul>
 *
 * <p>使用範例：
 * <pre>{@code
 * MockDiscordContext context = new MockDiscordContext(123L, 456L, 789L, "<@456>");
 * context.setOption("amount", 1000L);
 *
 * service.handle(context);
 *
 * assertThat(context.getOptionAsLong("amount")).contains(1000L);
 * }</pre>
 */
public class MockDiscordContext implements DiscordContext {

    private final long guildId;
    private final long userId;
    private final long channelId;
    private final String userMention;
    private final ConcurrentHashMap<String, Object> options;

    /**
     * 建構 Mock Discord 事件上下文
     *
     * @param guildId Guild ID
     * @param userId 使用者 ID
     * @param channelId 頻道 ID
     * @param userMention 使用者 Mention 格式（如 {@code <@123456789>}）
     */
    public MockDiscordContext(long guildId, long userId, long channelId, String userMention) {
        if (guildId <= 0) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (channelId <= 0) {
            throw new IllegalArgumentException("channelId must be positive");
        }
        if (userMention == null || userMention.isEmpty()) {
            throw new IllegalArgumentException("userMention must not be null or empty");
        }

        this.guildId = guildId;
        this.userId = userId;
        this.channelId = channelId;
        this.userMention = userMention;
        this.options = new ConcurrentHashMap<>();
    }

    @Override
    public long getGuildId() {
        return guildId;
    }

    @Override
    public long getUserId() {
        return userId;
    }

    @Override
    public long getChannelId() {
        return channelId;
    }

    @Override
    public String getUserMention() {
        return userMention;
    }

    @Override
    public Optional<String> getOption(String name) {
        Object value = options.get(name);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }

    @Override
    public Optional<String> getOptionAsString(String name) {
        Object value = options.get(name);
        if (value instanceof String) {
            return Optional.of((String) value);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> getOptionAsLong(String name) {
        Object value = options.get(name);
        if (value instanceof Long) {
            return Optional.of((Long) value);
        }
        if (value instanceof Integer) {
            return Optional.of(((Integer) value).longValue());
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> getOptionAsUser(String name) {
        Object value = options.get(name);
        if (value instanceof User) {
            return Optional.of((User) value);
        }
        return Optional.empty();
    }

    /**
     * 設定選項值（字串）
     *
     * @param name 選項名稱
     * @param value 選項值
     */
    public void setOption(String name, String value) {
        options.put(name, value);
    }

    /**
     * 設定選項值（Long）
     *
     * @param name 選項名稱
     * @param value 選項值
     */
    public void setOption(String name, Long value) {
        options.put(name, value);
    }

    /**
     * 設定選項值（User）
     *
     * @param name 選項名稱
     * @param value 選項值
     */
    public void setOption(String name, User value) {
        options.put(name, value);
    }

    /**
     * 設定選項值（Integer，會轉換為 Long）
     *
     * @param name 選項名稱
     * @param value 選項值
     */
    public void setOption(String name, Integer value) {
        options.put(name, value.longValue());
    }

    /**
     * 清除特定選項
     *
     * @param name 選項名稱
     */
    public void clearOption(String name) {
        options.remove(name);
    }

    /**
     * 清除所有選項
     */
    public void clearAllOptions() {
        options.clear();
    }

    /**
     * 檢查是否包含特定選項
     *
     * @param name 選項名稱
     * @return true 如果選項存在
     */
    public boolean hasOption(String name) {
        return options.containsKey(name);
    }

    /**
     * 取得選項數量
     *
     * @return 選項數量
     */
    public int getOptionCount() {
        return options.size();
    }
}
