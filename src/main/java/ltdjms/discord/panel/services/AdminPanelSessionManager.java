package ltdjms.discord.panel.services;

import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages active admin panel sessions so that the ephemeral admin panel
 * message can be updated (for example after adjusting balances or game configs).
 *
 * Discord 的互動訊息（尤其是 ephemeral）只能透過 {@link InteractionHook}
 * 在 15 分鐘存活期間內進行編輯，因此必須在 /admin-panel 建立時把 hook 記錄下來，
 * 後續在 Modal 提交時才能安全地更新原本的面板 Embed，而不是直接透過 Message ID 編輯，
 * 否則會出現「10008 Unknown Message」錯誤。
 */
public class AdminPanelSessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(AdminPanelSessionManager.class);
    // 15 分鐘 TTL（互動 hook 過期後就無法再編輯原訊息）
    private static final long TTL_SECONDS = 15 * 60;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Registers an admin panel session.
     *
     * @param guildId the Discord guild ID
     * @param adminId the admin user ID who opened /admin-panel
     * @param hook    the interaction hook for updating the admin panel message
     */
    public void registerSession(long guildId, long adminId, InteractionHook hook) {
        String key = getKey(guildId, adminId);
        sessions.put(key, new Session(hook, Instant.now()));
        LOG.debug("Registered admin panel session for key={}", key);
    }

    /**
     * Updates the admin panel for a guild/admin pair if a valid session exists.
     *
     * @param guildId  the Discord guild ID
     * @param adminId  the admin user ID
     * @param consumer action to perform on the InteractionHook
     */
    public void updatePanel(long guildId, long adminId, Consumer<InteractionHook> consumer) {
        String key = getKey(guildId, adminId);
        Session session = sessions.get(key);
        if (session != null) {
            if (isExpired(session)) {
                sessions.remove(key);
                LOG.debug("Removed expired admin panel session for key={}", key);
            } else {
                try {
                    consumer.accept(session.hook());
                } catch (Exception e) {
                    LOG.warn("Failed to update admin panel session for key={}. Removing session.", key, e);
                    sessions.remove(key);
                }
            }
        }
    }

    /**
     * Clears the session (for example when admin leaves the main panel).
     */
    public void clearSession(long guildId, long adminId) {
        String key = getKey(guildId, adminId);
        sessions.remove(key);
        LOG.debug("Cleared admin panel session for key={}", key);
    }

    /**
     * Updates all admin panels for a guild. Used when guild-wide settings change
     * (e.g., game configuration, currency settings, product changes).
     *
     * @param guildId  the Discord guild ID
     * @param consumer action to perform on each InteractionHook
     */
    public void updatePanelsByGuild(long guildId, Consumer<InteractionHook> consumer) {
        String guildPrefix = guildId + ":";
        sessions.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (!key.startsWith(guildPrefix)) {
                return false;
            }
            Session session = entry.getValue();
            if (isExpired(session)) {
                LOG.debug("Removed expired admin panel session for key={}", key);
                return true;
            }
            try {
                consumer.accept(session.hook());
            } catch (Exception e) {
                LOG.warn("Failed to update admin panel session for key={}. Removing session.", key, e);
                return true;
            }
            return false;
        });
    }

    private boolean isExpired(Session session) {
        return Instant.now().isAfter(session.createdAt().plusSeconds(TTL_SECONDS));
    }

    private String getKey(long guildId, long adminId) {
        return guildId + ":" + adminId;
    }

    private record Session(InteractionHook hook, Instant createdAt) {}
}

